package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.SetFlagResult;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Companion flag for ForceClaimChat. When set, overrides the default 320-block local chat radius.
 */
public class FlagDef_ForceClaimChatRadius extends FlagDefinition {

    public static final int MIN_RADIUS = 10;
    public static final int MAX_RADIUS = 320;

    public FlagDef_ForceClaimChatRadius(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public String getName() {
        return "ForceClaimChatRadius";
    }

    @Override
    public SetFlagResult validateParameters(String parameters, @Nullable CommandSender sender) {
        if (parameters.isEmpty()) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.ForceClaimChatRadiusInvalid,
                    String.valueOf(MIN_RADIUS), String.valueOf(MAX_RADIUS)));
        }
        int radius;
        try {
            radius = Integer.parseInt(parameters.trim());
        } catch (NumberFormatException e) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.ForceClaimChatRadiusInvalid,
                    String.valueOf(MIN_RADIUS), String.valueOf(MAX_RADIUS)));
        }
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.ForceClaimChatRadiusInvalid,
                    String.valueOf(MIN_RADIUS), String.valueOf(MAX_RADIUS)));
        }
        return new SetFlagResult(true, this.getSetMessage(parameters));
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.SetForceClaimChatRadius, parameters.trim());
    }

    @Override
    public String normalizeParameters(String parameters, @Nullable CommandSender sender) {
        return parameters == null ? "" : parameters.trim();
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.UnsetForceClaimChatRadius);
    }

    @Override
    public List<FlagType> getFlagType() {
        return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT);
    }
}
