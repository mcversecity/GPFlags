# MinecartSpeed Flag - Implementation Plan

## Overview
The MinecartSpeed flag allows claim owners to modify the speed of minecarts within their claim boundaries. Speed adjustments are applied gradually when crossing claim boundaries and reset to vanilla speed when leaving a claim without the flag set.

## Specifications

### Flag Properties
- **Name**: `MinecartSpeed`
- **Type**: Numeric (Integer)
- **Value Range**: 10 - 500 (representing 0.1x to 5.0x speed multiplier)
  - 10 = 0.1x speed (10% of vanilla)
  - 100 = 1.0x speed (vanilla)
  - 500 = 5.0x speed (500% of vanilla)
- **Default Behavior**: When flag is not set, minecarts travel at vanilla speed (1.0x)
- **Scope**: Regular minecarts only (not chest, furnace, hopper, TNT, or command block variants)
- **Application**: All minecarts regardless of passenger status (ridden or empty)

### Sub-Claim Behavior
- Sub-claims can override parent claim settings independently
- When entering a sub-claim with a different MinecartSpeed, transition to the new speed
- When leaving a sub-claim, transition to parent claim's speed (or vanilla if parent has no flag)

## Permission System

### Three-Tier Permission Structure

1. **Base Permission**: `gpflags.flag.minecartspeed`
   - Required to use the flag at all
   - Without this, players cannot set the flag on their claims

2. **Maximum Value Permission**: `gpflags.flag.minecartspeed.max.<percentage>`
   - Controls the maximum speed multiplier a player can set
   - Example: `gpflags.flag.minecartspeed.max.300` allows setting up to 300 (3.0x speed)
   - Server checks highest granted max permission
   - Default if no max specified: 500 (5.0x)

3. **Minimum Value Permission**: `gpflags.flag.minecartspeed.min.<percentage>`
   - Controls the minimum speed multiplier a player can set
   - Example: `gpflags.flag.minecartspeed.min.50` prevents setting below 50 (0.5x speed)
   - Server checks highest granted min permission
   - Default if no min specified: 10 (0.1x)

### Permission Examples

**Scenario 1: Default Player**
- Permissions: `gpflags.flag.minecartspeed`
- Allowed Range: 10 - 500 (full range)

**Scenario 2: Restricted Player**
- Permissions:
  - `gpflags.flag.minecartspeed`
  - `gpflags.flag.minecartspeed.max.200`
  - `gpflags.flag.minecartspeed.min.50`
- Allowed Range: 50 - 200 (0.5x to 2.0x)

**Scenario 3: Premium Player**
- Permissions:
  - `gpflags.flag.minecartspeed`
  - `gpflags.flag.minecartspeed.max.500`
  - `gpflags.flag.minecartspeed.min.10`
- Allowed Range: 10 - 500 (full range, explicitly granted)

**Scenario 4: No Base Permission**
- Permissions: None
- Result: Cannot use flag, command shows as unavailable

### Permission Validation Flow
1. Check if player has base permission `gpflags.flag.minecartspeed`
2. When setting flag value, scan player's permissions for all `gpflags.flag.minecartspeed.max.*` permissions
3. Extract numeric values, find highest granted max
4. Scan for all `gpflags.flag.minecartspeed.min.*` permissions
5. Extract numeric values, find highest granted min
6. Validate requested value is within [min, max] range
7. If valid, store value; if invalid, show error with allowed range

## Technical Implementation

### Architecture: Event-Based with Scheduled Transitions (Optimized)

This implementation uses an **event-based architecture** that minimizes performance overhead by only processing minecarts during active speed transitions. Instead of monitoring every minecart on every tick, we only track boundary crossings and run lightweight scheduled tasks for smooth transitions.

**Performance Profile**:
- Idle minecarts: Zero overhead (not tracked)
- Boundary crossing: Single event handler execution
- Transitioning minecarts: One scheduled task running every tick for 20 ticks
- Typical load: 0-10 active transitions at any time (not all minecarts)

### Core Components

#### 1. Flag Definition Class
**File**: `/mnt/c/Users/David/Projects/GPFlags/src/main/java/me/ryanhamshire/GPFlags/flags/FlagDef_MinecartSpeed.java`

**Base Class**: Extend `FlagDefinition` (standard flag base class)

**Key Methods**:
- `getSetMessage()` - Return flag set confirmation
- `getUnSetMessage()` - Return flag removal confirmation
- `validateSetParameter()` - Validate value is integer between 10-500 and within player's permission range
- `getMinecartSpeedMultiplier(Claim claim)` - Helper to get speed multiplier for a claim (returns 1.0 if not set)

#### 2. Minecart Tracking System
**Purpose**: Track ONLY minecarts currently in speed transitions (not all minecarts)

**Data Structure**:
```java
HashMap<UUID, SpeedTransitionTask>
- UUID: Minecart entity UUID
- SpeedTransitionTask extends BukkitRunnable {
    UUID minecartUUID;
    double startMultiplier;    // Speed at start of transition
    double targetMultiplier;   // Target speed for new claim
    int currentTick;           // Current tick in transition (0-20)
    int totalTicks;            // Total transition duration (20)
}
```

**Lifecycle**:
- **Add**: When minecart crosses claim boundary
- **Remove**: After 20 ticks when transition completes
- **Cleanup**: On entity death/unload events

**Memory Efficiency**: Only stores 0-20 minecarts typically (those actively transitioning), not all minecarts on server

#### 3. Entity Movement Detector
**File**: Create new class `MinecartClaimTracker.java` or add to existing listener

**Purpose**: Detect when minecarts cross claim boundaries using existing GPFlags vehicle tracking

**Implementation Approach**:
Hook into GPFlags' existing entity movement tracking system (similar to how `PlayerMovementFlagDefinition` works but for vehicles). The codebase already tracks vehicle movement via `Util.getMovementGroup()`.

**Events to Handle**:
- Custom vehicle boundary detection (piggyback on existing claim change detection)
- `EntityDeathEvent` - Clean up tracking data for destroyed minecarts
- `ChunkUnloadEvent` - Clean up tracking data for unloaded minecarts
- `VehicleCreateEvent` - Optionally initialize newly created minecarts in claims

**Key Logic**:
```java
// On minecart position change (detected via existing movement tracking)
if (minecart crosses claim boundary) {
    Claim newClaim = getClaimAt(minecart.getLocation());
    double targetSpeed = getMinecartSpeedMultiplier(newClaim);
    double currentSpeed = getCurrentSpeed(minecart); // from velocity magnitude

    startSpeedTransition(minecart, currentSpeed, targetSpeed);
}
```

#### 4. Speed Transition Task (BukkitRunnable)
**Purpose**: Smoothly interpolate minecart speed over 20 ticks when crossing boundaries

**Implementation**:
```java
class SpeedTransitionTask extends BukkitRunnable {
    @Override
    public void run() {
        Minecart minecart = getMinecartByUUID(minecartUUID);
        if (minecart == null || !minecart.isValid()) {
            this.cancel();
            removeFromTracking(minecartUUID);
            return;
        }

        currentTick++;
        double progress = (double) currentTick / totalTicks;
        double currentMultiplier = lerp(startMultiplier, targetMultiplier, progress);

        applySpeedMultiplier(minecart, currentMultiplier);

        if (currentTick >= totalTicks) {
            this.cancel();
            removeFromTracking(minecartUUID);
        }
    }
}
```

**Scheduling**: Each task runs every tick (1/20 second) for exactly 20 ticks, then self-cancels

#### 5. Velocity Application Logic

**Speed Multiplier Application**:
```java
void applySpeedMultiplier(Minecart minecart, double multiplier) {
    Vector velocity = minecart.getVelocity();

    // Don't modify stopped minecarts
    if (velocity.lengthSquared() < 0.0001) {
        return;
    }

    // Get current direction and speed
    Vector direction = velocity.clone().normalize();
    double currentSpeed = velocity.length();

    // Calculate base vanilla speed (what speed would be without any flags)
    // This is estimated from current speed / current multiplier if transitioning,
    // or just current speed if entering from vanilla area
    double baseSpeed = getCurrentBaseSpeed(minecart);

    // Apply new multiplier
    double newSpeed = baseSpeed * multiplier;

    // Set new velocity preserving direction
    Vector newVelocity = direction.multiply(newSpeed);
    minecart.setVelocity(newVelocity);
}
```

**Linear Interpolation (Lerp)**:
```java
double lerp(double start, double end, double progress) {
    return start + (end - start) * progress;
}
```

**Claim Boundary Detection**:
- Leverage existing GPFlags claim tracking mechanisms
- Store last known claim for each tracked minecart
- Compare current location's claim to last known claim
- On mismatch: boundary crossed, initiate transition

### 5. Registration
**File**: `/mnt/c/Users/David/Projects/GPFlags/src/main/java/me/ryanhamshire/GPFlags/GPFlagsConfig.java`

**Steps**:
- Add `FlagDef_MinecartSpeed` instance to flag definitions list
- Ensure initialization happens during plugin startup
- Register `MinecartClaimTracker` listener

### 6. Performance Optimizations

#### Smart Boundary Detection
Instead of using `VehicleMoveEvent` (fires 20x/second per vehicle), implement one of these efficient approaches:

**Option A: Leverage Existing Player Movement System** (RECOMMENDED)
- GPFlags already has `PlayerMovementFlagDefinition` that tracks claim crossings
- Extend this system to track vehicles when players are riding them
- When player crosses boundary while in minecart, also check vehicle

**Option B: Periodic Chunk-Based Scanning**
- Run a scheduled task every 5-10 ticks (0.25-0.5 seconds)
- Only scan chunks that contain claims with MinecartSpeed flag
- Check minecart entities in those chunks for claim changes
- Much lower frequency than per-tick monitoring

**Option C: Hybrid Event Filtering**
- Use `VehicleMoveEvent` but with aggressive filtering:
  ```java
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onVehicleMove(VehicleMoveEvent event) {
      // Fast bailouts
      if (!(event.getVehicle() instanceof Minecart)) return;
      if (event.getVehicle().getType() != EntityType.MINECART) return;

      Location from = event.getFrom();
      Location to = event.getTo();

      // Only check if crossed block boundary (not sub-block movement)
      if (from.getBlockX() == to.getBlockX() &&
          from.getBlockZ() == to.getBlockZ()) return;

      // Now do claim check (only when crossing block boundaries)
      checkClaimBoundary(minecart, from, to);
  }
  ```

**Recommended**: Use Option A (piggyback on player movement) for ridden minecarts and Option B (periodic scanning) for empty minecarts.

#### Task Management
- Limit maximum concurrent transition tasks (e.g., 50 max)
- If limit reached, complete oldest transitions instantly
- Prevents memory issues with massive minecart farms

#### Cleanup Strategy
- Weak references for minecart UUIDs to allow garbage collection
- Periodic cleanup task (every 5 minutes) to remove stale entries
- Remove tracking on chunk unload events

#### Conditional Activation
Only register event listeners when at least one claim has MinecartSpeed flag set:
- On flag set: Register listeners if not already registered
- On flag unset: Check if any claims still have flag, unregister if none
- Reduces overhead on servers not using this flag

## Edge Cases and Considerations

### 1. Chunk Boundaries
- **Issue**: Minecarts crossing chunk boundaries might unload/reload
- **Solution**: Store tracking data keyed by UUID, persist across chunk loads if possible
- **Fallback**: Re-initialize on chunk load, detect current claim, start fresh transition

### 2. Server Restart/Reload
- **Issue**: In-progress transitions lost on restart
- **Solution**: Don't persist transition data; on reload, minecarts start fresh with vanilla speed
- **Behavior**: Next claim boundary crossing will trigger proper speed adjustment

### 3. Rapid Claim Crossing
- **Issue**: Minecart crosses multiple claims within transition period
- **Solution**: Reset transition timer on each boundary cross, use current (transitioning) speed as new start value

### 4. Minecart on Rails vs. Free-Moving
- **Issue**: Rails constrain minecart physics differently than free movement
- **Solution**: Velocity modification should work for both; test thoroughly
- **Note**: Rail speed limits might cap effectiveness at high multipliers

### 5. Multiple Passengers / Entities
- **Issue**: Multiple entities riding same minecart
- **Solution**: Track by minecart UUID, not passenger; one speed applies to vehicle

### 6. Redstone-Powered Rails
- **Issue**: Powered rails add their own speed boost
- **Solution**: Multiplier applies to resulting velocity, works additively with rail boosts

### 7. Permission Changes Mid-Transition
- **Issue**: Player loses permission while minecart is transitioning
- **Solution**: Speed already set remains active; permission only checked on flag set/modify

### 8. Claim Deletion
- **Issue**: Claim deleted while minecart inside
- **Solution**: Treat as exiting to wilderness, transition to vanilla speed (100)

### 9. Sub-Claim Entry/Exit
- **Issue**: Nested claims with different speeds
- **Solution**: Use GriefPrevention's claim hierarchy; check innermost claim first
- **Behavior**: Sub-claim overrides parent, exit sub-claim uses parent's value

### 10. Value Persistence
- **Issue**: Flag value stored but player permission changes
- **Solution**: Permission checked only on SET; existing values remain functional
- **Admin Override**: Manual flag removal if needed

## Implementation Steps (Optimized Approach)

### Phase 1: Core Flag Structure
1. Create `FlagDef_MinecartSpeed.java` extending `FlagDefinition` (standard base class)
2. Implement basic flag definition methods (name, messages, icon)
3. Implement `validateSetParameter()` with percentage range validation (10-500)
4. Implement helper method `getMinecartSpeedMultiplier(Claim claim)`
5. Register flag in `GPFlagsConfig.java`
6. Test basic flag set/unset commands

### Phase 2: Permission System
1. Implement permission scanning in `validateSetParameter()`
2. Find all `gpflags.flag.minecartspeed.max.*` permissions
3. Find all `gpflags.flag.minecartspeed.min.*` permissions
4. Calculate allowed range and validate against it
5. Provide clear error messages showing allowed range
6. Test with various permission combinations

### Phase 3: Speed Transition Task (BukkitRunnable)
1. Create `SpeedTransitionTask` class extending `BukkitRunnable`
2. Implement tick-based linear interpolation (lerp) logic
3. Implement velocity application with direction preservation
4. Add self-cancellation after 20 ticks
5. Add null/invalid entity checks
6. Test transition smoothness visually

### Phase 4: Minecart Tracking System
1. Create `MinecartClaimTracker` class (singleton or static manager)
2. Add `HashMap<UUID, SpeedTransitionTask>` to track active transitions
3. Implement `startTransition(minecart, startSpeed, targetSpeed)` method
4. Implement cleanup methods (on death, unload, completion)
5. Add maximum concurrent task limit (50) with instant-completion fallback
6. Test memory management (verify cleanup works)

### Phase 5: Boundary Detection (Choose Implementation)
Choose one or combine approaches from Performance Optimizations section:

**Option A Implementation** (Recommended for ridden minecarts):
1. Hook into existing `PlayerMovementFlagDefinition` system
2. Detect when player in minecart crosses claim boundary
3. Trigger speed transition for the vehicle
4. Test with player riding minecart

**Option B Implementation** (Recommended for empty minecarts):
1. Create scheduled task running every 5-10 ticks
2. Scan only chunks containing claims with MinecartSpeed flag
3. Check each minecart for claim changes
4. Trigger transitions as needed
5. Test with empty minecarts

**Option C Implementation** (Fallback):
1. Implement `VehicleMoveEvent` handler with aggressive filtering
2. Only process block boundary crossings (not sub-block)
3. Fast type checks and bailouts
4. Test performance impact

### Phase 6: Event Handlers for Cleanup
1. Implement `EntityDeathEvent` handler → cancel task, remove from tracking
2. Implement `ChunkUnloadEvent` handler → cancel tasks for unloaded chunks
3. Implement periodic cleanup task (every 5 minutes) for stale entries
4. Test cleanup under various scenarios

### Phase 7: Claim Boundary Integration
1. Integrate chosen boundary detection with claim lookup
2. Handle wilderness → claim transitions
3. Handle claim → claim transitions (different speeds)
4. Handle claim → sub-claim transitions
5. Handle sub-claim → parent claim transitions
6. Handle rapid boundary crossing (cancel old task, start new)
7. Test all transition scenarios

### Phase 8: Conditional Activation (Optional Optimization)
1. Track count of claims with MinecartSpeed flag
2. Only register listeners when count > 0
3. Unregister when count reaches 0
4. Test activation/deactivation

### Phase 9: Edge Case Handling
1. Test and handle stopped minecarts (zero velocity)
2. Test rapid claim crossing (task interruption)
3. Test chunk boundary crossing
4. Test server reload scenarios (transitions reset)
5. Test minecart on rails vs. free-moving
6. Test nested claims (parent/sub-claim)
7. Test concurrent transition limit (50+)

### Phase 10: Performance Testing and Refinement
1. Performance test with 100 minecarts, many claims
2. Monitor TPS with active transitions
3. Profile boundary detection efficiency
4. Visual smoothness testing (20-tick transitions)
5. Permission boundary testing
6. Integration testing with other flags
7. Multiplayer testing
8. Documentation and help text

## Testing Plan

### Unit Tests
1. **Permission Validation**:
   - Player with no permissions → rejected
   - Player with base only → accepts 10-500
   - Player with max.200 → rejects 201-500
   - Player with min.50 → rejects 10-49
   - Player with both max.200 min.50 → accepts only 50-200

2. **Value Conversion**:
   - 10 → 0.1x multiplier
   - 100 → 1.0x multiplier
   - 500 → 5.0x multiplier

3. **Transition Calculation**:
   - Linear interpolation accuracy
   - Edge values (0 ticks, 20 ticks)
   - Mid-transition interruption

### Integration Tests
1. **Single Claim**:
   - Minecart enters claim with speed 200 → gradual acceleration
   - Minecart exits claim to wilderness → gradual deceleration to vanilla
   - Minecart spawned inside claim → starts at vanilla, transitions on first move

2. **Multiple Claims**:
   - Claim A (speed 50) → Claim B (speed 300) → smooth transition
   - Rapid crossing A → B → A → verify no weird behavior

3. **Sub-Claims**:
   - Parent (speed 100) contains sub-claim (speed 400)
   - Enter sub-claim → accelerate
   - Exit sub-claim → decelerate to parent speed

4. **Minecart Types**:
   - Regular minecart → affected
   - Chest minecart → NOT affected (verify exclusion)
   - Furnace minecart → NOT affected (verify exclusion)

5. **Rail Scenarios**:
   - Powered rails + speed multiplier → combined effect
   - Unpowered rails with high multiplier → verify speed maintained
   - Activator rails → no interference

6. **Passenger Scenarios**:
   - Player riding minecart → speed applies
   - Empty minecart → speed applies
   - Entity (mob) riding → speed applies

### Performance Tests
1. 100 minecarts in single claim → monitor TPS
2. 10 claims with different speeds, minecarts crossing → monitor performance
3. Server with 50 players, multiple active minecarts → stability check

## Configuration

### Config Options (Optional Future Enhancement)
```yaml
minecart-speed:
  transition-ticks: 20  # Duration of speed transition in ticks
  default-permission-max: 500  # Default max if no permission specified
  default-permission-min: 10   # Default min if no permission specified
  apply-to-types:  # Future: extend to other types
    - MINECART
```

## Documentation Requirements

### Help Text
```
MinecartSpeed: Sets the speed multiplier for minecarts in this claim.
Usage: /cf MinecartSpeed <value>
Value: 10-500 (10=0.1x speed, 100=vanilla, 500=5.0x speed)
Default: Vanilla speed (100) when not set
Permissions:
  - gpflags.flag.minecartspeed - Base permission to use flag
  - gpflags.flag.minecartspeed.max.<value> - Maximum speed allowed
  - gpflags.flag.minecartspeed.min.<value> - Minimum speed allowed
Examples:
  /cf MinecartSpeed 200  - Double speed (2.0x)
  /cf MinecartSpeed 50   - Half speed (0.5x)
  /cf MinecartSpeed      - Remove flag (return to vanilla)
```

### Permission Documentation
Add to permissions.yml or server documentation:
```yaml
gpflags.flag.minecartspeed:
  description: Allows using the MinecartSpeed flag
  default: true

gpflags.flag.minecartspeed.max.300:
  description: Allows setting MinecartSpeed up to 300 (3.0x)
  default: op

gpflags.flag.minecartspeed.min.50:
  description: Prevents setting MinecartSpeed below 50 (0.5x)
  default: op
```

## Future Enhancements (Out of Scope)

1. **Extended Minecart Types**: Support chest, furnace, hopper, TNT, command block minecarts
2. **Per-Type Speed**: Different multipliers for different minecart types
3. **Directional Speed**: Different speeds for north/south vs. east/west
4. **Acceleration Curves**: Non-linear transitions (ease-in, ease-out)
5. **Sound Effects**: Speed-based sound pitch modification
6. **Particle Effects**: Visual indicators during speed transitions
7. **Config-Driven Defaults**: Server-wide default speed when flag not set
8. **API Integration**: Allow other plugins to modify/query minecart speeds

## Performance Analysis

### Comparison: Original vs. Optimized Approach

| Metric | Original (VehicleMoveEvent) | Optimized (Event-Based + Tasks) |
|--------|----------------------------|----------------------------------|
| **Events/Second (100 minecarts)** | ~2,000 (20 per minecart) | ~10-20 boundary crosses total |
| **CPU per Idle Minecart** | Continuous monitoring | Zero (not tracked) |
| **CPU per Transitioning Cart** | High (every tick checks) | Low (scheduled task only) |
| **Typical Active Tasks** | All minecarts tracked | 0-10 transition tasks |
| **Memory Overhead** | 100 tracking entries | 0-10 tracking entries |
| **TPS Impact (estimate)** | Medium-High | Very Low |

### Worst Case Performance

**Scenario**: 100 minecarts all crossing boundaries simultaneously
- Original: 2,000 events/second continuously
- Optimized: ~100 boundary events (one-time) + 100 transition tasks for 1 second

**Result**: Even in worst case, optimized approach completes transitions and drops to zero overhead within 1 second. Original maintains continuous load.

### Expected Real-World Performance

**Typical Server**:
- 20-50 players
- 5-10 active minecarts at any time
- 1-2 minecarts transitioning at any moment

**Optimized Overhead**:
- Boundary detection: ~2-5 checks/second (via player movement or periodic scan)
- Active transitions: 1-2 BukkitRunnables running
- **TPS impact**: <0.1 TPS (negligible)

## Risks and Mitigation

### Risk 1: Performance Impact
- **Concern**: Even optimized approach could impact TPS with many simultaneous transitions
- **Mitigation**:
  - Limit concurrent transitions to 50 (instant-complete extras)
  - Use efficient boundary detection (piggyback on player movement)
  - Conditional listener activation (only when flag in use)
  - Block-boundary filtering (not sub-block movements)
- **Monitoring**: Profile during testing, benchmark with 100+ minecarts
- **Fallback**: Config option to disable flag if issues arise

### Risk 2: Physics Conflicts
- **Concern**: Velocity modification might conflict with Minecraft physics
- **Mitigation**:
  - Apply multiplier carefully, preserve direction
  - Test extensively on rails (powered, unpowered, curves)
  - Don't modify zero-velocity minecarts
  - Use server-authoritative velocity setting
- **Fallback**: Add config option to disable if issues arise

### Risk 3: Multiplayer Synchronization
- **Concern**: Client-side prediction vs. server-side speed changes
- **Mitigation**:
  - Server authoritative velocity (client syncs to server)
  - Gradual 20-tick transition reduces visual jarring
  - Expect minor visual glitches during transition
- **Testing**: Test with multiple players, varying latencies (50ms, 100ms, 200ms)

### Risk 4: Exploit Potential
- **Concern**: Ultra-high speeds might enable claim bypasses, chunk loading exploits, or duplication glitches
- **Mitigation**:
  - Hard cap at 5.0x (reasonable limit)
  - Permission system allows server-specific limits
  - Log flag changes for admin monitoring
  - Easy admin override (remove flag)
- **Admin Tools**:
  - `/cf MinecartSpeed` removal
  - Permission revocation
  - Claim flag inspection commands

### Risk 5: Boundary Detection Gaps
- **Concern**: Minecart crosses boundary without detection (teleport, high speed, chunk loading)
- **Mitigation**:
  - Use multiple detection methods (player movement + periodic scan)
  - Periodic scan catches missed boundaries every 5-10 ticks
  - Chunk load events can trigger claim check
- **Acceptable**: Minor delays (0.25-0.5s) before speed adjustment acceptable

### Risk 6: Memory Leaks
- **Concern**: Failed cleanup of tracking data or BukkitRunnables
- **Mitigation**:
  - Self-cancelling tasks after 20 ticks
  - Cleanup on entity death/unload
  - Periodic cleanup task (every 5 minutes)
  - Weak references where applicable
- **Testing**: Run for 24+ hours, monitor memory usage

## Success Criteria

1. Minecarts smoothly transition between different speed zones
2. Permission system correctly enforces min/max limits
3. No performance degradation with reasonable minecart counts (<100 active)
4. No memory leaks over extended runtime (24+ hours)
5. Correct behavior across claim boundaries, including sub-claims
6. Clean code following existing GPFlags patterns
7. Comprehensive error messages for users
8. Documentation complete and accurate

## Estimated Complexity
- **Difficulty**: Medium-High (optimized approach requires careful implementation)
- **Time Estimate**: 10-15 hours development + 6-8 hours testing
- **Files Created**: 2-3
  - `FlagDef_MinecartSpeed.java` - Flag definition
  - `SpeedTransitionTask.java` - BukkitRunnable for transitions
  - `MinecartClaimTracker.java` - Boundary detection and tracking manager
- **Files Modified**: 2-4
  - `GPFlagsConfig.java` - Flag registration
  - Listener classes (player movement or entity listeners)
  - Possibly `Util.java` if adding shared helper methods
  - Possibly `PlayerMovementFlagDefinition.java` if extending vehicle tracking
- **Lines of Code**: ~500-800 (including comments and documentation)

**Complexity Breakdown**:
- Flag definition: Simple (50-100 LOC)
- Permission validation: Medium (100-150 LOC)
- Transition task: Medium (100-150 LOC)
- Boundary detection: Medium-High (150-250 LOC, multiple approaches)
- Tracking/cleanup: Medium (100-150 LOC)

---

## Notes
- This implementation uses an **optimized event-based architecture** to minimize performance impact
- Only tracks minecarts during active speed transitions (0-20 at a time, not all minecarts)
- Uses percentage-based system for better user clarity (100 = vanilla is intuitive)
- Gradual 20-tick transitions prevent jarring speed changes
- Permission system with separate min/max controls provides flexible server administration
- Scoped to regular minecarts only for initial implementation simplicity
- Follows GPFlags architecture patterns while optimizing for performance
- Estimated TPS impact: <0.1 TPS under typical load (negligible)

## Key Design Decisions

1. **Event-Based over Continuous Monitoring**: Dramatically reduces CPU overhead
2. **BukkitRunnable for Transitions**: Self-contained, self-cancelling tasks
3. **Percentage Values (10-500)**: More intuitive than decimals (0.1-5.0)
4. **Separate Min/Max Permissions**: Greater administrative flexibility
5. **20-Tick Transition**: Balance between smoothness and responsiveness
6. **Regular Minecarts Only**: Simpler initial scope, extensible later
7. **Conditional Activation**: Listeners only registered when flag in use (optional optimization)
