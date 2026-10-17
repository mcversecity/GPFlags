package me.ryanhamshire.GPFlags.flags;

import io.papermc.paper.event.entity.EntityMoveEvent;
import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.SetFlagResult;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * MinecartSpeed Flag - Allows claim owners to modify minecart max speed within their claims.
 *
 * The value is a percentage of vanilla max speed. 100 is 0.4 blocks/tick (8 blocks/second).
 * 200 is 0.8 blocks/tick (16 blocks/second). {@link Minecart#setMaxSpeed(double)} is only a
 * ceiling: powered rails, slopes, and rider input still provide the acceleration.
 * Regular minecarts only (not chest, furnace, hopper, or other variants).
 */
public class FlagDef_MinecartSpeed extends PlayerMovementFlagDefinition {

    private static final int MIN_SPEED_VALUE = 10;   // 0.1x speed
    private static final int MAX_SPEED_VALUE = 500;  // 5.0x speed
    private static final double VANILLA_MAX_SPEED = 0.4; // Bukkit default max speed, blocks per tick

    public FlagDef_MinecartSpeed(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public String getName() {
        return "MinecartSpeed";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        try {
            int speedValue = Integer.parseInt(parameters);
            double multiplier = speedValue / 100.0;
            String multiplierStr = String.format("%.1f", multiplier);
            return new MessageSpecifier(Messages.EnableMinecartSpeed, parameters, multiplierStr);
        } catch (NumberFormatException e) {
            return new MessageSpecifier(Messages.EnableMinecartSpeed, parameters, "?");
        }
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisableMinecartSpeed);
    }

    @Override
    public SetFlagResult validateParameters(String parameters, CommandSender sender) {
        if (parameters.isEmpty()) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.MinecartSpeedInvalidValue));
        }

        int speedValue;
        try {
            speedValue = Integer.parseInt(parameters);
        } catch (NumberFormatException e) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.MinecartSpeedInvalidValue));
        }

        if (speedValue < MIN_SPEED_VALUE || speedValue > MAX_SPEED_VALUE) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.MinecartSpeedOutOfRange,
                String.valueOf(MIN_SPEED_VALUE), String.valueOf(MAX_SPEED_VALUE)));
        }

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
     * Get minimum allowed speed value based on permissions.
     * Scans for gpflags.flag.minecartspeed.min.* permissions.
     */
    private int getMinAllowedValue(CommandSender sender) {
        int minAllowed = MIN_SPEED_VALUE;

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
     * Get maximum allowed speed value based on permissions.
     * Scans for gpflags.flag.minecartspeed.max.* permissions.
     */
    private int getMaxAllowedValue(CommandSender sender) {
        int maxAllowed = MAX_SPEED_VALUE;

        for (PermissionAttachmentInfo attachment : sender.getEffectivePermissions()) {
            String permName = attachment.getPermission().toLowerCase();
            if (permName.startsWith("gpflags.flag.minecartspeed.max.") && attachment.getValue()) {
                try {
                    int value = Integer.parseInt(permName.replace("gpflags.flag.minecartspeed.max.", ""));
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
     * Get speed multiplier from a flag (returns 1.0 if flag is null or invalid).
     */
    private double getSpeedMultiplier(@Nullable Flag flag) {
        if (flag == null || flag.parameters == null || flag.parameters.isEmpty()) {
            return 1.0;
        }

        try {
            int speedValue = Integer.parseInt(flag.parameters);
            return speedValue / 100.0;
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    /**
     * Convert a multiplier to absolute Bukkit max speed (vanilla base 0.4 blocks/tick).
     */
    private double toMaxSpeed(double multiplier) {
        return VANILLA_MAX_SPEED * multiplier;
    }

    /**
     * Apply the claim flag's max speed cap. An unset flag restores vanilla 0.4.
     */
    private void applyCap(Minecart minecart, @Nullable Flag flag) {
        double newMaxSpeed = toMaxSpeed(getSpeedMultiplier(flag));
        if (Math.abs(minecart.getMaxSpeed() - newMaxSpeed) < 0.0001) {
            return;
        }
        minecart.setMaxSpeed(newMaxSpeed);
    }

    private void applyCapAt(Minecart minecart, Location location) {
        Player rider = ridingPlayer(minecart);
        applyCap(minecart, this.getFlagInstanceAtLocation(location, rider));
    }

    private boolean isRegularMinecart(Entity entity) {
        return entity instanceof Minecart && entity.getType() == EntityType.MINECART;
    }

    @Nullable
    private Player ridingPlayer(Minecart minecart) {
        for (Entity passenger : minecart.getPassengers()) {
            if (passenger instanceof Player) {
                return (Player) passenger;
            }
        }
        return null;
    }

    /**
     * Called when a player crosses a claim boundary while riding a minecart.
     * The destination flag wins, including a subclaim overriding its parent.
     * No flag restores the vanilla cap.
     */
    @Override
    public void onChangeClaim(@NotNull Player player, @Nullable Location from, @NotNull Location to,
                             @Nullable Claim claimFrom, @Nullable Claim claimTo,
                             @Nullable Flag fromFlag, @Nullable Flag toFlag) {

        Entity vehicle = player.getVehicle();
        if (!isRegularMinecart(vehicle)) {
            return;
        }

        applyCap((Minecart) vehicle, toFlag);
    }

    /**
     * Apply the cap when a player mounts a regular minecart already inside a claim.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onMount(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player)) return;
        if (!isRegularMinecart(event.getVehicle())) return;

        applyCapAt((Minecart) event.getVehicle(), event.getVehicle().getLocation());
    }

    /**
     * Apply the cap when a regular minecart is placed in a flagged claim.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlace(EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (!isRegularMinecart(entity)) {
            return;
        }

        applyCapAt((Minecart) entity, entity.getLocation());
    }

    /**
     * Apply or clear the cap when a regular minecart crosses into another claim.
     * Block changes inside one claim also refresh the cap, so a cart already on
     * the rails picks up a flag that was set after it was placed.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onMinecartMove(EntityMoveEvent event) {
        Entity entity = event.getEntity();
        if (!isRegularMinecart(entity)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() != to.getWorld()) {
            return;
        }
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        // getClaimAt returns the innermost claim, so a subclaim overrides its parent.
        // An unset flag restores the vanilla 0.4 cap, including when rolling back into wilderness.
        applyCapAt((Minecart) entity, Util.getInBoundsLocation(to));
    }
}
