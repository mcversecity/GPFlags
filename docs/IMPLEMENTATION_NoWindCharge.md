# NoWindCharge Flag - Implementation Plan

## Overview
The NoWindCharge flag prevents non-trusted players from using wind charges within protected claims. Wind charges will be blocked from exploding when thrown by players who lack build trust in the claim.

## Requirements

### Functional Requirements
1. **Block wind charge explosions** from players without build trust
2. **Require build trust** for players to use wind charges in claims
3. **Show message** to player when their wind charge is blocked
4. **Player-only blocking** - Only block player-thrown wind charges, not Breeze mob attacks
5. **Dual location checking** - Check both throw location and explosion location (like NoEnderPearl)

### Non-Functional Requirements
- Efficient implementation (minimal performance overhead)
- Consistent with existing flag patterns (NoEnderPearl, NoExplosionDamage)
- Clear user messaging

## Technical Background

### Wind Charge Mechanics (Minecraft 1.21+)
- **Entity Type**: `WindCharge` (projectile entity, added in 1.21)
- **Sources**:
  - Players throwing wind charge items
  - Breeze mobs shooting wind charges
- **Behavior**: Creates explosion effect when hitting blocks/entities
- **Events**:
  - `ProjectileLaunchEvent` - When wind charge is thrown/shot
  - `ProjectileHitEvent` - When wind charge hits something
  - `EntityExplodeEvent` - When wind charge creates explosion (if applicable)

### Shooter Detection
Wind charges have a `ProjectileSource` which can be:
- `Player` - if thrown by player
- `Breeze` - if shot by Breeze mob
- Can check with `windCharge.getShooter() instanceof Player`

## Implementation Approach

### Events to Listen To

#### Option A: ProjectileHitEvent (RECOMMENDED)
Listen for `ProjectileHitEvent` and check if projectile is a wind charge:
```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onProjectileHit(ProjectileHitEvent event) {
    if (!(event.getEntity() instanceof WindCharge)) return;

    WindCharge windCharge = (WindCharge) event.getEntity();
    ProjectileSource shooter = windCharge.getShooter();

    // Only process player-thrown wind charges
    if (!(shooter instanceof Player)) return;

    // Check claims and trust...
}
```

**Pros**:
- Catches wind charge hits before explosion
- Can cancel explosion by removing the entity
- Single event handler needed
- Efficient (only fires when projectiles hit)

**Cons**:
- Need to verify that cancelling here prevents explosion

#### Option B: EntityExplodeEvent
Listen for explosion events and filter for wind charge explosions:
```java
@EventHandler
public void onEntityExplode(EntityExplodeEvent event) {
    if (!(event.getEntity() instanceof WindCharge)) return;
    // Check shooter and claims...
}
```

**Pros**:
- Directly controls explosion behavior
- Clear cancellation semantics

**Cons**:
- Wind charges might not trigger EntityExplodeEvent (needs testing)
- Less efficient if many explosions occur

### Recommended Approach: ProjectileHitEvent

Use `ProjectileHitEvent` to intercept wind charges before they explode:

1. Check if projectile is `WindCharge`
2. Check if shooter is a `Player` (skip Breeze wind charges)
3. Get hit location
4. Check claim at hit location
5. Check if player has build trust
6. If no trust, cancel by removing entity and show message
7. Optional: Also check launch location (dual-check like NoEnderPearl)

## Implementation Details

### File Structure
**New File**: `FlagDef_NoWindCharge.java`
- Location: `/src/main/java/me/ryanhamshire/GPFlags/flags/`
- Extends: `FlagDefinition`
- Pattern: Similar to `FlagDef_NoEnderPearl.java`

### Code Structure

```java
package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.FlagsDataStore;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.TextMode;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;

public class FlagDef_NoWindCharge extends FlagDefinition {

    public FlagDef_NoWindCharge(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        // Check if wind charge
        if (!(event.getEntity() instanceof WindCharge)) return;

        WindCharge windCharge = (WindCharge) event.getEntity();
        ProjectileSource shooter = windCharge.getShooter();

        // Only process player-thrown wind charges (skip Breeze)
        if (!(shooter instanceof Player)) return;

        Player player = (Player) shooter;

        // Check hit location
        Flag flag = this.getFlagInstanceAtLocation(event.getEntity().getLocation(), player);
        if (flag == null) return;

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(
            event.getEntity().getLocation(), false, null
        );

        // Check if player has build trust (using Util.shouldBypass)
        if (Util.shouldBypass(player, claim, flag)) return;

        // Block the wind charge
        event.setCancelled(true); // or windCharge.remove()

        // Send message to player
        String owner = claim.getOwnerName();
        String playerName = player.getName();

        String msg = new FlagsDataStore().getMessage(Messages.NoWindChargeInClaim);
        msg = msg.replace("{p}", playerName).replace("{o}", owner);
        msg = msg.replace("{0}", playerName).replace("{1}", owner);
        MessagingUtil.sendMessage(player, TextMode.Warn + msg);
    }

    @Override
    public String getName() {
        return "NoWindCharge";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnableNoWindCharge);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisableNoWindCharge);
    }

    @Override
    public List<FlagType> getFlagType() {
        return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT);
    }
}
```

### Message Enum Additions
**File**: `Messages.java`

Add three new message entries:
```java
NoWindChargeInClaim,           // "You can't use wind charges in {o}'s claim."
EnableNoWindCharge,             // "Wind charges disabled."
DisableNoWindCharge,            // "Wind charges re-enabled."
```

### Messages Properties File
**File**: `messages.yml` or similar

Add default messages:
```yaml
NoWindChargeInClaim: "You can't use wind charges in {o}'s claim."
EnableNoWindCharge: "Wind charges disabled."
DisableNoWindCharge: "Wind charges re-enabled."
```

### Configuration File Registration
**File**: `GPFlagsConfig.java`

Add flag to the list of registered flags:
```java
this.flagDefinitions.add(new FlagDef_NoWindCharge(this.flagManager, this.plugin));
```

## Dual Location Checking (Optional Enhancement)

Similar to NoEnderPearl, we could check both:
1. **Launch location** - Where player threw the wind charge from
2. **Hit location** - Where the wind charge exploded

### Implementation:
```java
// Store launch location in metadata when ProjectileLaunchEvent fires
@EventHandler
public void onProjectileLaunch(ProjectileLaunchEvent event) {
    if (!(event.getEntity() instanceof WindCharge)) return;
    if (!(event.getEntity().getShooter() instanceof Player)) return;

    // Check flag at launch location
    Flag flag = this.getFlagInstanceAtLocation(event.getLocation(), player);
    if (flag != null) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(...);
        if (!Util.shouldBypass(player, claim, flag)) {
            event.setCancelled(true);
            // Send message: "You can't throw wind charges from {o}'s claim"
        }
    }
}
```

**Decision**: Start with hit location only (simpler). Add launch location check if needed based on user feedback.

## Efficiency Considerations

### Performance Analysis

**Event Frequency**:
- `ProjectileHitEvent` only fires when projectiles hit something
- Not a high-frequency event like player movement
- Efficient fast-path checks (instanceof) before expensive operations

**Optimization Techniques**:
1. **Early Returns**:
   - `instanceof WindCharge` check first (fastest)
   - `instanceof Player` check for shooter second
   - Only then do claim lookup
2. **Single Event Handler**: Only `ProjectileHitEvent` (not multiple events)
3. **No Continuous Monitoring**: Event-driven, no scheduled tasks

**Expected Overhead**:
- Negligible impact on TPS
- Only processes when wind charges are actually used
- Claim lookup is already optimized in GriefPrevention

### Breeze Detection Efficiency

**Question**: Is it efficient to skip Breeze wind charges?

**Answer**: Yes, very efficient!
- `shooter instanceof Player` is an O(1) type check
- Happens before any expensive claim lookups
- Breeze wind charges are skipped immediately with minimal overhead

**Alternative Approaches Considered**:
1. ❌ Check entity type of shooter (less efficient, requires casting)
2. ✅ `instanceof Player` (optimal, direct type check)
3. ❌ Metadata tagging (unnecessary complexity)

## Edge Cases

### 1. Player Throws from Wilderness into Claim
- **Behavior**: Block explosion at hit location
- **Check**: Hit location claim has flag → block

### 2. Player Throws from Claim into Wilderness
- **Behavior**: Allow (no flag in wilderness)
- **Check**: Hit location has no claim → allow

### 3. Player Throws from Their Claim into Someone Else's Claim
- **Behavior**: Block explosion in target claim if no build trust
- **Check**: Hit location claim owner ≠ shooter → block if no trust

### 4. Breeze Shoots Wind Charge in Claim
- **Behavior**: Always allow (Breeze not affected by flag)
- **Check**: `shooter instanceof Player` returns false → skip immediately

### 5. Wind Charge Hits Player vs Block
- **Behavior**: Block regardless of what it hits
- **Check**: Location of wind charge entity, not hit target

### 6. Player Logs Out Mid-Flight
- **Behavior**: Wind charge continues, shooter reference might be null
- **Check**: Add null check for shooter

### 7. Claim Deleted While Wind Charge in Flight
- **Behavior**: No claim at hit location → allow explosion
- **Check**: Flag instance lookup returns null → allow

### 8. Sub-Claims
- **Behavior**: Check innermost claim (sub-claim overrides parent)
- **Check**: GriefPrevention already handles this in `getClaimAt()`

### 9. Admin Claims
- **Behavior**: Respect flag if set on admin claim
- **Check**: `shouldBypass()` handles admin permissions

### 10. World Settings / Default Flags
- **Behavior**: Flag can be set as world default
- **Check**: `getFlagType()` includes `FlagType.DEFAULT`

## Testing Plan

### Unit Tests (Manual)

#### Test 1: Basic Blocking
1. Set NoWindCharge flag on claim
2. Non-trusted player throws wind charge into claim
3. **Expected**: Wind charge blocked, message shown

#### Test 2: Trusted Player
1. Set NoWindCharge flag on claim
2. Claim owner throws wind charge in claim
3. **Expected**: Wind charge explodes normally

#### Test 3: Build Trust
1. Set NoWindCharge flag on claim
2. Give player build trust (/trust player)
3. Player throws wind charge
4. **Expected**: Wind charge explodes normally

#### Test 4: Access Trust (Not Enough)
1. Set NoWindCharge flag on claim
2. Give player access trust (/accesstrust player)
3. Player throws wind charge
4. **Expected**: Wind charge blocked (needs build trust)

#### Test 5: Breeze Wind Charges
1. Set NoWindCharge flag on claim
2. Spawn Breeze mob in claim
3. Breeze shoots wind charge at player
4. **Expected**: Breeze wind charge NOT blocked

#### Test 6: Wilderness
1. Player throws wind charge in wilderness (no claim)
2. **Expected**: Wind charge explodes normally

#### Test 7: Cross-Claim
1. Player in wilderness throws wind charge into protected claim
2. **Expected**: Wind charge blocked at claim boundary

#### Test 8: Sub-Claim Override
1. Parent claim: NoWindCharge flag set
2. Sub-claim: NoWindCharge flag NOT set
3. Player throws wind charge in sub-claim
4. **Expected**: Allowed in sub-claim (sub overrides parent)

#### Test 9: Flag Removal
1. Set NoWindCharge flag
2. Verify blocking works
3. Remove flag (/cf NoWindCharge)
4. **Expected**: Wind charges work again

#### Test 10: Message Content
1. Trigger block condition
2. **Expected**: Message shows claim owner name correctly

### Integration Tests

#### Test 1: Multiple Flags
- Combine with NoExplosionDamage, NoPlayerDamage, etc.
- Verify no conflicts

#### Test 2: Performance
- Spawn 10 Breezes, throw 20 player wind charges
- Monitor TPS
- **Expected**: No noticeable impact

#### Test 3: Server Reload
- Set flag, reload server
- Verify flag persists and works

## Implementation Steps

### Phase 1: Core Flag Implementation
1. Create `FlagDef_NoWindCharge.java`
2. Implement basic structure (extends FlagDefinition)
3. Implement `getName()`, `getSetMessage()`, `getUnSetMessage()`, `getFlagType()`
4. Add imports

### Phase 2: Event Handler
1. Add `ProjectileHitEvent` handler
2. Implement wind charge type check
3. Implement player shooter check
4. Test basic event firing (debug logging)

### Phase 3: Claim and Trust Checking
1. Add claim lookup at hit location
2. Integrate `Util.shouldBypass()` for build trust check
3. Test with trusted vs non-trusted players

### Phase 4: Blocking Logic
1. Implement event cancellation or entity removal
2. Test that explosion is actually prevented
3. Verify no visual glitches

### Phase 5: Messaging
1. Add message enum entries to `Messages.java`
2. Add default message strings
3. Implement message sending to player
4. Test message formatting (placeholders)

### Phase 6: Registration
1. Add flag to `GPFlagsConfig.java`
2. Test flag appears in `/cf` list
3. Test flag can be set/unset

### Phase 7: Testing
1. Run all manual tests from Testing Plan
2. Fix any issues found
3. Test edge cases
4. Performance testing

### Phase 8: Documentation
1. Update plugin documentation
2. Add help text for flag
3. Document permission requirements

## Files to Modify

### New Files
1. `FlagDef_NoWindCharge.java` - Main flag implementation

### Modified Files
1. `Messages.java` - Add message enum entries
2. `messages.yml` (or properties file) - Add message strings
3. `GPFlagsConfig.java` - Register flag

## Estimated Complexity
- **Difficulty**: Low (very similar to NoEnderPearl)
- **Time Estimate**: 1-2 hours implementation + 1 hour testing
- **Lines of Code**: ~80-100 (including comments)

## Success Criteria
1. ✅ Player wind charges blocked in claims without build trust
2. ✅ Breeze wind charges NOT blocked (pass through)
3. ✅ Clear message shown to player on block
4. ✅ Build trust level required
5. ✅ No performance impact
6. ✅ Works with sub-claims and world defaults
7. ✅ Flag can be set/unset via commands
8. ✅ Follows existing code patterns

## Alternative Approaches Considered

### Alternative 1: EntityExplodeEvent
- **Pros**: Directly prevents explosion
- **Cons**: May not fire for wind charges, less control
- **Decision**: Rejected in favor of ProjectileHitEvent

### Alternative 2: Block All Wind Charges (Including Breeze)
- **Pros**: Simpler implementation
- **Cons**: User explicitly requested Breeze exemption
- **Decision**: Rejected per requirements

### Alternative 3: Dual Location Checking (Launch + Hit)
- **Pros**: More comprehensive protection
- **Cons**: More complex, may be confusing
- **Decision**: Deferred to future enhancement

### Alternative 4: Custom Explosion Damage Prevention
- **Pros**: Could integrate with NoExplosionDamage
- **Cons**: Doesn't prevent explosion, only damage
- **Decision**: Rejected, requirement is to prevent explosion

## Open Questions

1. **Event Cancellation**: Does cancelling `ProjectileHitEvent` prevent wind charge explosion?
   - **Resolution**: Test during implementation. If not, use `windCharge.remove()` instead.

2. **Message Variants**: Should there be different messages for:
   - Blocked at hit location
   - Blocked at launch location (if dual-check implemented)
   - **Resolution**: Start with single message, expand if needed

3. **Permission Bypass**: Should there be a permission to bypass this flag?
   - **Resolution**: `Util.shouldBypass()` already handles this via GriefPrevention trust system

4. **World Default Behavior**: If flag set as world default, should it block everywhere?
   - **Resolution**: Yes, consistent with other flags' default behavior

## Notes
- Wind charges are a 1.21+ feature, ensure server is running compatible version
- This implementation follows GPFlags patterns for consistency
- Breeze detection is efficient via instanceof check
- Event-driven approach has minimal performance overhead
- Build trust requirement is standard for destructive actions
