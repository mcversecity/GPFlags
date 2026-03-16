package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.SetFlagResult;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * MinecartSpeed Flag - Allows claim owners to modify minecart speed within their claims
 *
 * Features:
 * - Speed range: 10-500 (representing 0.1x to 5.0x multiplier)
 * - Smooth 20-tick transitions when crossing claim boundaries
 * - Permission system with min/max controls
 * - Only affects ridden minecarts (empty minecarts deferred to Phase 2)
 * - Regular minecarts only (not chest/furnace/hopper variants)
 */
public class FlagDef_MinecartSpeed extends PlayerMovementFlagDefinition {

    // Tracking map for minecarts currently transitioning between speeds
    private final Map<UUID, SpeedTransitionTask> activeTransitions = new HashMap<>();

    // Constants
    private static final int MIN_SPEED_VALUE = 10;   // 0.1x speed
    private static final int MAX_SPEED_VALUE = 500;  // 5.0x speed
    private static final int DEFAULT_SPEED_VALUE = 100; // 1.0x vanilla speed
    private static final int TRANSITION_TICKS = 20;  // Duration of speed transition
    private static final int MAX_CONCURRENT_TRANSITIONS = 50; // Prevent abuse

    public FlagDef_MinecartSpeed(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public String getName() {
        return "MinecartSpeed";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        // Calculate multiplier for display (e.g., "200" -> "2.0")
        try {
            int speedValue = Integer.parseInt(parameters);
            double multiplier = speedValue / 100.0;
            String multiplierStr = String.format("%.1f", multiplier);
            return new MessageSpecifier(Messages.EnableMinecartSpeed, parameters, multiplierStr);
        } catch (NumberFormatException e) {
            // Fallback if parsing fails (shouldn't happen after validation)
            return new MessageSpecifier(Messages.EnableMinecartSpeed, parameters, "?");
        }
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisableMinecartSpeed);
    }

    @Override
    public SetFlagResult validateParameters(String parameters, CommandSender sender) {
        // Require a parameter
        if (parameters.isEmpty()) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.MinecartSpeedInvalidValue));
        }

        // Parse and validate integer
        int speedValue;
        try {
            speedValue = Integer.parseInt(parameters);
        } catch (NumberFormatException e) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.MinecartSpeedInvalidValue));
        }

        // Check basic range
        if (speedValue < MIN_SPEED_VALUE || speedValue > MAX_SPEED_VALUE) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.MinecartSpeedOutOfRange,
                String.valueOf(MIN_SPEED_VALUE), String.valueOf(MAX_SPEED_VALUE)));
        }

        // Check permissions if sender exists
        if (sender != null) {
            int minAllowed = getMinAllowedValue(sender);
            int maxAllowed = getMaxAllowedValue(sender);

            if (speedValue < minAllowed || speedValue > maxAllowed) {
                return new SetFlagResult(false, new MessageSpecifier(Messages.MinecartSpeedPermissionDenied,
                    String.valueOf(minAllowed), String.valueOf(maxAllowed)));
            }
        }

        return new SetFlagResult(true, this.getSetMessage(parameters));
    }

    /**
     * Get minimum allowed speed value based on permissions
     * Scans for gpflags.flag.minecartspeed.min.* permissions
     */
    private int getMinAllowedValue(CommandSender sender) {
        int minAllowed = MIN_SPEED_VALUE; // Default minimum

        for (PermissionAttachmentInfo attachment : sender.getEffectivePermissions()) {
            String permName = attachment.getPermission().toLowerCase();
            if (permName.startsWith("gpflags.flag.minecartspeed.min.") && attachment.getValue()) {
                try {
                    int value = Integer.parseInt(permName.replace("gpflags.flag.minecartspeed.min.", ""));
                    if (value > minAllowed) {
                        minAllowed = value;
                    }
                } catch (NumberFormatException ignored) {
                    // Invalid permission format, skip
                }
            }
        }

        return minAllowed;
    }

    /**
     * Get maximum allowed speed value based on permissions
     * Scans for gpflags.flag.minecartspeed.max.* permissions
     */
    private int getMaxAllowedValue(CommandSender sender) {
        int maxAllowed = MAX_SPEED_VALUE; // Default maximum

        for (PermissionAttachmentInfo attachment : sender.getEffectivePermissions()) {
            String permName = attachment.getPermission().toLowerCase();
            if (permName.startsWith("gpflags.flag.minecartspeed.max.") && attachment.getValue()) {
                try {
                    int value = Integer.parseInt(permName.replace("gpflags.flag.minecartspeed.max.", ""));
                    // Take the highest granted max permission
                    if (value > maxAllowed) {
                        maxAllowed = value;
                    }
                } catch (NumberFormatException ignored) {
                    // Invalid permission format, skip
                }
            }
        }

        return maxAllowed;
    }

    /**
     * Get speed multiplier from a flag (returns 1.0 if flag is null or invalid)
     */
    private double getSpeedMultiplier(@Nullable Flag flag) {
        if (flag == null || flag.parameters == null || flag.parameters.isEmpty()) {
            return 1.0; // Vanilla speed
        }

        try {
            int speedValue = Integer.parseInt(flag.parameters);
            return speedValue / 100.0; // Convert 100 -> 1.0x, 200 -> 2.0x, etc.
        } catch (NumberFormatException e) {
            return 1.0; // Default to vanilla on parse error
        }
    }

    /**
     * Called when a player crosses a claim boundary
     * Checks if player is riding a minecart and triggers speed transition
     */
    @Override
    public void onChangeClaim(@NotNull Player player, @Nullable Location from, @NotNull Location to,
                             @Nullable Claim claimFrom, @Nullable Claim claimTo,
                             @Nullable Flag fromFlag, @Nullable Flag toFlag) {

        // Check if player is riding a vehicle
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;

        // Only handle regular minecarts (not chest, furnace, hopper, etc.)
        if (!(vehicle instanceof Minecart) || vehicle.getType() != EntityType.MINECART) {
            return;
        }

        Minecart minecart = (Minecart) vehicle;

        // Get speed multipliers for both claims
        double fromSpeed = getSpeedMultiplier(fromFlag);
        double toSpeed = getSpeedMultiplier(toFlag);

        // Only transition if speeds are different
        if (Math.abs(fromSpeed - toSpeed) > 0.001) { // Use epsilon for float comparison
            startSpeedTransition(minecart, fromSpeed, toSpeed);
        }
    }

    /**
     * Start a smooth speed transition for a minecart
     */
    private void startSpeedTransition(Minecart minecart, double startMultiplier, double targetMultiplier) {
        UUID minecartId = minecart.getUniqueId();

        // Cancel existing transition if one is in progress
        SpeedTransitionTask existingTask = activeTransitions.get(minecartId);
        if (existingTask != null) {
            existingTask.cancel();
            activeTransitions.remove(minecartId);
        }

        // Check concurrent task limit to prevent abuse
        if (activeTransitions.size() >= MAX_CONCURRENT_TRANSITIONS) {
            // Instantly complete oldest transition
            Iterator<Map.Entry<UUID, SpeedTransitionTask>> iterator = activeTransitions.entrySet().iterator();
            if (iterator.hasNext()) {
                Map.Entry<UUID, SpeedTransitionTask> oldest = iterator.next();
                oldest.getValue().completeInstantly();
                oldest.getValue().cancel();
                iterator.remove();
            }
        }

        // Create and start new transition task
        SpeedTransitionTask task = new SpeedTransitionTask(minecart, startMultiplier, targetMultiplier);
        activeTransitions.put(minecartId, task);
        task.runTaskTimer(plugin, 0L, 1L); // Run every tick
    }

    /**
     * Clean up tracking when minecart dies
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMinecartDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Minecart)) return;

        UUID minecartId = event.getEntity().getUniqueId();
        SpeedTransitionTask task = activeTransitions.remove(minecartId);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Clean up tracking when chunk unloads
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        // Clean up any minecarts in the unloading chunk
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Minecart) {
                UUID minecartId = entity.getUniqueId();
                SpeedTransitionTask task = activeTransitions.remove(minecartId);
                if (task != null) {
                    task.cancel();
                }
            }
        }
    }

    /**
     * Inner class representing a smooth speed transition task
     * Runs for exactly TRANSITION_TICKS (20 ticks = 1 second)
     */
    private class SpeedTransitionTask extends BukkitRunnable {
        private final UUID minecartUUID;
        private final double startMultiplier;
        private final double targetMultiplier;
        private int currentTick = 0;

        public SpeedTransitionTask(Minecart minecart, double startMultiplier, double targetMultiplier) {
            this.minecartUUID = minecart.getUniqueId();
            this.startMultiplier = startMultiplier;
            this.targetMultiplier = targetMultiplier;
        }

        @Override
        public void run() {
            // Get minecart entity
            Entity entity = Bukkit.getEntity(minecartUUID);
            if (entity == null || !entity.isValid() || !(entity instanceof Minecart)) {
                // Minecart no longer exists, cancel and cleanup
                this.cancel();
                activeTransitions.remove(minecartUUID);
                return;
            }

            Minecart minecart = (Minecart) entity;

            // Calculate progress (0.0 to 1.0)
            currentTick++;
            double progress = (double) currentTick / TRANSITION_TICKS;

            // Linear interpolation (lerp) between start and target multipliers
            double currentMultiplier = lerp(startMultiplier, targetMultiplier, progress);

            // Apply the speed multiplier
            applySpeedMultiplier(minecart, currentMultiplier);

            // Check if transition is complete
            if (currentTick >= TRANSITION_TICKS) {
                this.cancel();
                activeTransitions.remove(minecartUUID);
            }
        }

        /**
         * Instantly complete the transition (used when hitting concurrent task limit)
         */
        public void completeInstantly() {
            Entity entity = Bukkit.getEntity(minecartUUID);
            if (entity instanceof Minecart) {
                applySpeedMultiplier((Minecart) entity, targetMultiplier);
            }
        }

        /**
         * Linear interpolation between two values
         */
        private double lerp(double start, double end, double progress) {
            return start + (end - start) * progress;
        }

        /**
         * Apply speed multiplier to a minecart's velocity
         */
        private void applySpeedMultiplier(Minecart minecart, double multiplier) {
            Vector velocity = minecart.getVelocity();

            // Don't modify stopped minecarts
            if (velocity.lengthSquared() < 0.0001) {
                return;
            }

            // Get current direction and speed
            Vector direction = velocity.clone().normalize();
            double currentSpeed = velocity.length();

            // Estimate base speed (what velocity would be at 1.0x)
            // If transitioning, we need to reverse the previous multiplier
            double baseSpeed;
            if (currentTick == 1) {
                // First tick: current speed is at startMultiplier
                baseSpeed = currentSpeed / startMultiplier;
            } else {
                // Subsequent ticks: use the base speed we've been working with
                // Since we're continuously adjusting, just use current/previous multiplier
                double previousProgress = (double) (currentTick - 1) / TRANSITION_TICKS;
                double previousMultiplier = lerp(startMultiplier, targetMultiplier, previousProgress);
                baseSpeed = currentSpeed / previousMultiplier;
            }

            // Apply new multiplier
            double newSpeed = baseSpeed * multiplier;

            // Set new velocity preserving direction
            Vector newVelocity = direction.multiply(newSpeed);
            minecart.setVelocity(newVelocity);
        }
    }
}
