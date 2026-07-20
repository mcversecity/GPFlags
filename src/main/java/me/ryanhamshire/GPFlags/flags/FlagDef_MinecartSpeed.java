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
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
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
 * MinecartSpeed Flag - Allows claim owners to modify minecart max speed within their claims
 *
 * Features:
 * - Speed range: 10-500 (representing 0.1x to 5.0x of vanilla max speed 0.4)
 * - Uses {@link Minecart#setMaxSpeed(double)} as a physics cap (does not auto-accelerate)
 * - Smooth 20-tick slowdown only when current velocity exceeds a lower new cap
 * - Applied on place, mount, and claim boundary crossing
 * - Regular minecarts only (not chest/furnace/hopper variants)
 */
public class FlagDef_MinecartSpeed extends PlayerMovementFlagDefinition {

    // Tracking map for minecarts currently slowing down to a lower cap
    private final Map<UUID, SpeedTransitionTask> activeTransitions = new HashMap<>();

    // Constants
    private static final int MIN_SPEED_VALUE = 10;   // 0.1x speed
    private static final int MAX_SPEED_VALUE = 500;  // 5.0x speed
    private static final double VANILLA_MAX_SPEED = 0.4; // Bukkit default max speed
    private static final int TRANSITION_TICKS = 20;  // Duration of slowdown transition
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
     * Convert a multiplier to absolute Bukkit max speed (vanilla base 0.4).
     */
    private double toMaxSpeed(double multiplier) {
        return VANILLA_MAX_SPEED * multiplier;
    }

    /**
     * Apply the claim flag's max speed cap to a minecart (vanilla 0.4 if flag unset).
     */
    private void applyMaxSpeed(Minecart minecart, @Nullable Flag flag) {
        minecart.setMaxSpeed(toMaxSpeed(getSpeedMultiplier(flag)));
    }

    /**
     * Apply max speed for the flag at a location, and slow down only if over the new cap.
     */
    private void applyFlagAtLocation(Minecart minecart, Location location, @Nullable Player player) {
        Flag flag = this.getFlagInstanceAtLocation(location, player);
        double newMaxSpeed = toMaxSpeed(getSpeedMultiplier(flag));
        minecart.setMaxSpeed(newMaxSpeed);
        maybeSlowDown(minecart, newMaxSpeed);
    }

    /**
     * If current velocity exceeds the new max, start a smooth slowdown. Otherwise cancel any transition.
     */
    private void maybeSlowDown(Minecart minecart, double newMaxSpeed) {
        Vector velocity = minecart.getVelocity();
        double currentSpeed = velocity.length();

        if (currentSpeed > newMaxSpeed + 0.0001) {
            startSlowdown(minecart, currentSpeed, newMaxSpeed);
        } else {
            cancelTransition(minecart.getUniqueId());
        }
    }

    /**
     * Called when a player crosses a claim boundary while riding a minecart.
     * Updates max speed; slows velocity only when the new cap is below current speed.
     */
    @Override
    public void onChangeClaim(@NotNull Player player, @Nullable Location from, @NotNull Location to,
                             @Nullable Claim claimFrom, @Nullable Claim claimTo,
                             @Nullable Flag fromFlag, @Nullable Flag toFlag) {

        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;

        // Only handle regular minecarts (not chest, furnace, hopper, etc.)
        if (!(vehicle instanceof Minecart) || vehicle.getType() != EntityType.MINECART) {
            return;
        }

        Minecart minecart = (Minecart) vehicle;
        double newMaxSpeed = toMaxSpeed(getSpeedMultiplier(toFlag));
        minecart.setMaxSpeed(newMaxSpeed);
        maybeSlowDown(minecart, newMaxSpeed);
    }

    /**
     * Apply max speed when a player mounts a regular minecart (no claim crossing required).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onMount(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player)) return;
        if (!(event.getVehicle() instanceof Minecart)) return;
        if (event.getVehicle().getType() != EntityType.MINECART) return;

        Player player = (Player) event.getEntered();
        Minecart minecart = (Minecart) event.getVehicle();
        applyFlagAtLocation(minecart, minecart.getLocation(), player);
    }

    /**
     * Apply max speed when a regular minecart is placed in a flagged claim.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlace(EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Minecart) || entity.getType() != EntityType.MINECART) {
            return;
        }

        Minecart minecart = (Minecart) entity;
        Player player = event.getPlayer();
        applyFlagAtLocation(minecart, minecart.getLocation(), player);
    }

    /**
     * Start a smooth slowdown toward an absolute max speed length.
     */
    private void startSlowdown(Minecart minecart, double startSpeed, double targetMaxSpeed) {
        UUID minecartId = minecart.getUniqueId();

        // Cancel existing transition if one is in progress
        cancelTransition(minecartId);

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

        SpeedTransitionTask task = new SpeedTransitionTask(minecart, startSpeed, targetMaxSpeed);
        activeTransitions.put(minecartId, task);
        task.runTaskTimer(plugin, 0L, 1L); // Run every tick
    }

    private void cancelTransition(UUID minecartId) {
        SpeedTransitionTask existingTask = activeTransitions.remove(minecartId);
        if (existingTask != null) {
            existingTask.cancel();
        }
    }

    /**
     * Clean up tracking when minecart dies
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMinecartDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Minecart)) return;
        cancelTransition(event.getEntity().getUniqueId());
    }

    /**
     * Clean up tracking when chunk unloads
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Minecart) {
                cancelTransition(entity.getUniqueId());
            }
        }
    }

    /**
     * Smooth slowdown toward an absolute velocity length (never speeds up).
     * Runs for up to TRANSITION_TICKS (20 ticks = 1 second).
     */
    private class SpeedTransitionTask extends BukkitRunnable {
        private final UUID minecartUUID;
        private final double startSpeed;
        private final double targetMaxSpeed;
        private int currentTick = 0;

        public SpeedTransitionTask(Minecart minecart, double startSpeed, double targetMaxSpeed) {
            this.minecartUUID = minecart.getUniqueId();
            this.startSpeed = startSpeed;
            this.targetMaxSpeed = targetMaxSpeed;
        }

        @Override
        public void run() {
            Entity entity = Bukkit.getEntity(minecartUUID);
            if (entity == null || !entity.isValid() || !(entity instanceof Minecart)) {
                this.cancel();
                activeTransitions.remove(minecartUUID);
                return;
            }

            Minecart minecart = (Minecart) entity;
            currentTick++;
            double progress = Math.min(1.0, (double) currentTick / TRANSITION_TICKS);
            double desiredSpeed = lerp(startSpeed, targetMaxSpeed, progress);
            applyVelocityLength(minecart, desiredSpeed);

            if (currentTick >= TRANSITION_TICKS || desiredSpeed <= targetMaxSpeed + 0.0001) {
                // Ensure we land at or below the target
                applyVelocityLength(minecart, Math.min(minecart.getVelocity().length(), targetMaxSpeed));
                this.cancel();
                activeTransitions.remove(minecartUUID);
            }
        }

        /**
         * Instantly complete the slowdown (used when hitting concurrent task limit)
         */
        public void completeInstantly() {
            Entity entity = Bukkit.getEntity(minecartUUID);
            if (entity instanceof Minecart) {
                applyVelocityLength((Minecart) entity, targetMaxSpeed);
            }
        }

        private double lerp(double start, double end, double progress) {
            return start + (end - start) * progress;
        }

        /**
         * Set velocity length while preserving direction. Never increases speed.
         */
        private void applyVelocityLength(Minecart minecart, double targetLength) {
            Vector velocity = minecart.getVelocity();

            // Don't modify stopped minecarts
            if (velocity.lengthSquared() < 0.0001) {
                return;
            }

            double currentSpeed = velocity.length();
            // Never speed up — only clamp downward toward target
            double newSpeed = Math.min(currentSpeed, targetLength);
            if (newSpeed >= currentSpeed - 0.0001) {
                return;
            }

            Vector direction = velocity.clone().normalize();
            minecart.setVelocity(direction.multiply(newSpeed));
        }
    }
}
