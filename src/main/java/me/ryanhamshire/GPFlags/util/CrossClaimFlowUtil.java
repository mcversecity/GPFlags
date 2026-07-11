package me.ryanhamshire.GPFlags.util;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CrossClaimFlowUtil {

    private CrossClaimFlowUtil() {
    }

    public static @Nullable Claim getClaimAt(@NotNull Location location) {
        if (!GriefPrevention.instance.claimsEnabledForWorld(location.getWorld())) {
            return null;
        }
        return GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
    }

    public static @Nullable Claim getClaimAt(@NotNull Location location, @Nullable Claim hint) {
        if (!GriefPrevention.instance.claimsEnabledForWorld(location.getWorld())) {
            return null;
        }
        return GriefPrevention.instance.dataStore.getClaimAt(location, false, hint);
    }

    /**
     * Whether a cross-claim action cancelled by GriefPrevention should be allowed because
     * an opt-in flag is set on a relevant claim.
     */
    public static boolean shouldAllowCrossClaim(
            @NotNull FlagManager flagManager,
            @Nullable Claim fromClaim,
            @Nullable Claim toClaim,
            @NotNull World world,
            @NotNull String flagName) {
        if (claimHasAllowFlag(flagManager, fromClaim, world, flagName)) {
            return true;
        }
        return destinationHasAllowFlag(flagManager, toClaim, world, flagName);
    }

    private static boolean claimHasAllowFlag(
            @NotNull FlagManager flagManager,
            @Nullable Claim claim,
            @NotNull World world,
            @NotNull String flagName) {
        if (claim == null) {
            return false;
        }
        Flag flag = flagManager.getEffectiveFlag(flagName, claim, world);
        return flag != null;
    }

    private static boolean destinationHasAllowFlag(
            @NotNull FlagManager flagManager,
            @Nullable Claim claim,
            @NotNull World world,
            @NotNull String flagName) {
        if (claim == null) {
            return false;
        }
        if (isRestrictedSubdivision(claim)) {
            Flag flag = flagManager.getRawClaimFlag(claim, flagName);
            return flag != null && flag.getSet();
        }
        return claimHasAllowFlag(flagManager, claim, world, flagName);
    }

    private static boolean isRestrictedSubdivision(@NotNull Claim claim) {
        return claim.parent != null && claim.getSubclaimRestrictions();
    }
}
