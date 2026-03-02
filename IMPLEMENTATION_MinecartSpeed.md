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

### Core Components

#### 1. Flag Definition Class
**File**: `/mnt/c/Users/David/Projects/GPFlags/src/main/java/me/ryanhamshire/GPFlags/flags/FlagDef_MinecartSpeed.java`

**Base Class**: Extend `PlayerMovementFlagDefinition` (for claim boundary tracking)

**Key Methods**:
- `getSetMessage()` - Return flag set confirmation
- `getUnSetMessage()` - Return flag removal confirmation
- `validateSetParameter()` - Validate value is integer between 10-500 and within player's permission range
- `onChangeClaim()` - Triggered when minecart crosses claim boundary
- Additional: `onMinecartMove()` - Custom handler for gradual speed adjustment (new listener method)

#### 2. Minecart Tracking System
**Purpose**: Track minecarts as they move between claims and manage speed transitions

**Data Structure**:
```
HashMap<UUID, MinecartSpeedData>
- UUID: Minecart entity UUID
- MinecartSpeedData {
    double currentMultiplier;  // Current speed multiplier (transitioning)
    double targetMultiplier;   // Target speed multiplier for current claim
    long transitionStartTime;  // System time when transition began
    int transitionTicks;       // Ticks remaining in transition
    Claim currentClaim;        // Current claim (null if wilderness)
}
```

**Cleanup**: Remove entries when minecart is destroyed or unloaded

#### 3. Event Listener
**File**: `/mnt/c/Users/David/Projects/GPFlags/src/main/java/me/ryanhamshire/GPFlags/listener/EntityListener.java` (create if doesn't exist, or add to existing)

**Events to Handle**:
- `VehicleMoveEvent` - Monitor minecart position changes, apply speed modifications
- `EntitySpawnEvent` - Initialize minecarts entering loaded chunks
- `EntityDeathEvent` - Clean up tracking data
- `ChunkUnloadEvent` - Clean up tracking data for unloaded minecarts
- `EntityDismountEvent` - Continue tracking (flag applies to ridden and empty)

#### 4. Speed Adjustment Logic

**Gradual Transition**:
- Transition duration: 20 ticks (1 second) for smooth interpolation
- Every tick during transition: `currentMultiplier = lerp(startMultiplier, targetMultiplier, progress)`
- Progress calculation: `ticksElapsed / transitionTicks`
- Apply multiplier to velocity vector: `velocity = velocity.normalize().multiply(baseSpeed * currentMultiplier)`

**Claim Boundary Detection**:
- When `VehicleMoveEvent` fires, check if minecart has changed claims
- If claim changed:
  - Get new claim's MinecartSpeed flag value (or default to 100 for vanilla)
  - Convert percentage to multiplier: `targetMultiplier = flagValue / 100.0`
  - Initialize transition from current to target over 20 ticks
  - Update tracking data

**Velocity Application**:
- Get minecart's current velocity vector
- Calculate base speed from velocity magnitude
- Apply multiplier: `newVelocity = velocity.normalize().multiply(baseSpeed * currentMultiplier)`
- Set minecart velocity (ensure not to override direction, only magnitude)

### 5. Registration
**File**: `/mnt/c/Users/David/Projects/GPFlags/src/main/java/me/ryanhamshire/GPFlags/GPFlagsConfig.java`

**Steps**:
- Add `FlagDef_MinecartSpeed` instance to flag definitions list
- Ensure initialization happens during plugin startup

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

## Implementation Steps

### Phase 1: Core Flag Structure
1. Create `FlagDef_MinecartSpeed.java` extending `PlayerMovementFlagDefinition`
2. Implement basic flag definition methods (name, messages, icon)
3. Implement `validateSetParameter()` with percentage range validation (10-500)
4. Register flag in `GPFlagsConfig.java`
5. Test basic flag set/unset commands

### Phase 2: Permission System
1. Implement permission scanning in `validateSetParameter()`
2. Find all `gpflags.flag.minecartspeed.max.*` permissions
3. Find all `gpflags.flag.minecartspeed.min.*` permissions
4. Calculate allowed range and validate against it
5. Provide clear error messages showing allowed range
6. Test with various permission combinations

### Phase 3: Minecart Tracking
1. Create `MinecartSpeedData` data class
2. Create `HashMap<UUID, MinecartSpeedData>` in flag definition or dedicated manager
3. Implement cleanup on entity death/unload
4. Test memory management (no leaks)

### Phase 4: Event Handlers
1. Create or modify entity listener class
2. Implement `VehicleMoveEvent` handler:
   - Detect claim changes
   - Initialize transitions
   - Apply velocity modifications
3. Implement lifecycle event handlers (spawn, death, unload)
4. Test event firing and tracking

### Phase 5: Speed Adjustment Logic
1. Implement gradual transition algorithm (linear interpolation over 20 ticks)
2. Apply velocity multiplier to minecart
3. Handle edge case: standstill minecarts (don't modify zero velocity)
4. Test smooth transitions visually

### Phase 6: Claim Boundary Integration
1. Integrate with existing claim boundary detection system
2. Handle wilderness → claim transitions
3. Handle claim → claim transitions
4. Handle claim → sub-claim transitions
5. Handle sub-claim → parent claim transitions
6. Test all transition scenarios

### Phase 7: Edge Case Handling
1. Implement each edge case solution from above list
2. Test rapid claim crossing
3. Test chunk boundary crossing
4. Test server reload scenarios
5. Test minecart on rails vs. free-moving
6. Test nested claims (parent/sub-claim)

### Phase 8: Testing and Refinement
1. Performance testing (many minecarts, many claims)
2. Visual smoothness testing
3. Permission boundary testing
4. Integration testing with other flags
5. Multiplayer testing
6. Documentation and help text

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

## Risks and Mitigation

### Risk 1: Performance Impact
- **Concern**: VehicleMoveEvent fires frequently, could impact TPS
- **Mitigation**: Optimize event handler, only process tracked minecarts, use efficient claim lookups
- **Monitoring**: Profile during testing, set performance benchmarks

### Risk 2: Physics Conflicts
- **Concern**: Velocity modification might conflict with Minecraft physics
- **Mitigation**: Apply multiplier carefully, preserve direction, test extensively on rails
- **Fallback**: Add config option to disable if issues arise

### Risk 3: Multiplayer Synchronization
- **Concern**: Client-side prediction vs. server-side speed changes
- **Mitigation**: Server authoritative, expect minor visual glitches
- **Testing**: Test with multiple players, varying latencies

### Risk 4: Exploit Potential
- **Concern**: Ultra-high speeds might enable claim bypasses or glitches
- **Mitigation**: Hard cap at 5.0x, monitor for exploits, ban list for problematic values
- **Admin Tools**: Easy flag removal, logging of flag changes

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
- **Difficulty**: Medium
- **Time Estimate**: 8-12 hours development + 4-6 hours testing
- **Files Created**: 1-2 (FlagDef_MinecartSpeed, possibly MinecartSpeedTracker)
- **Files Modified**: 2-3 (GPFlagsConfig, listener classes, possibly Util)
- **Lines of Code**: ~400-600 (including comments and documentation)

---

## Notes
- This implementation follows GPFlags architecture patterns observed in existing flags
- Uses percentage-based system for better user clarity (100 = vanilla is intuitive)
- Gradual transitions prevent jarring speed changes
- Permission system is flexible for server admins to restrict as needed
- Scoped to regular minecarts only for initial implementation simplicity
