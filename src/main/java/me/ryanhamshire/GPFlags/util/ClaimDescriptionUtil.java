package me.ryanhamshire.GPFlags.util;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public final class ClaimDescriptionUtil {

    public static final String FLAG_NAME = "ClaimDescription";

    private static final Pattern MINIMESSAGE_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern PLACEHOLDER_TOKEN = Pattern.compile("%[^%]+%");
    private static final Pattern WHITESPACE = Pattern.compile("[\\r\\n\\t]+");
    private static final Pattern MULTI_SPACE = Pattern.compile(" {2,}");

    private ClaimDescriptionUtil() {
    }

    /**
     * Sanitize claim description input before storing or displaying.
     *
     * @param raw               raw user input
     * @param allowPlaceholders if false, strip PlaceholderAPI-style %...% tokens
     */
    public static String sanitize(String raw, boolean allowPlaceholders) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String result = WHITESPACE.matcher(raw).replaceAll(" ");
        result = MULTI_SPACE.matcher(result).replaceAll(" ");
        result = MINIMESSAGE_TAG.matcher(result).replaceAll("");
        if (!allowPlaceholders) {
            result = PLACEHOLDER_TOKEN.matcher(result).replaceAll("");
        }
        result = MULTI_SPACE.matcher(result).replaceAll(" ");
        return result.trim();
    }

    /**
     * Resolve the effective ClaimDescription for a claim, respecting GPFlags inheritance.
     *
     * @return sanitized display value, or null if none is set
     */
    public static @Nullable String getEffectiveDescription(@NotNull FlagManager flagManager, @NotNull Claim claim, @NotNull World world) {
        Flag flag = flagManager.getEffectiveFlag(FLAG_NAME.toLowerCase(), claim, world);
        if (flag == null || flag.parameters == null || flag.parameters.isEmpty()) {
            return null;
        }
        String sanitized = sanitize(flag.parameters, true);
        return sanitized.isEmpty() ? null : sanitized;
    }

    /**
     * Build the default NotifyEnter label when no custom NotifyEnter params are set.
     * Always includes the claim ID; appends the description in parentheses when present.
     */
    public static String buildDefaultNotifyEnterLabel(@NotNull Claim claim, @NotNull World world, @NotNull FlagManager flagManager) {
        String label = "claim " + claim.getID();
        String description = getEffectiveDescription(flagManager, claim, world);
        if (description != null) {
            label = label + " (" + description + ")";
        }
        return label;
    }
}
