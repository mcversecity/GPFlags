package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.GPFlagsConfig;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.SetFlagResult;
import me.ryanhamshire.GPFlags.util.ClaimDescriptionUtil;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class FlagDef_ClaimDescription extends FlagDefinition {

    public static final String PLACEHOLDER_PERMISSION = "gpflags.flag.claimdescription.placeholders";

    public FlagDef_ClaimDescription(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public String getName() {
        return ClaimDescriptionUtil.FLAG_NAME;
    }

    @Override
    public List<FlagType> getFlagType() {
        return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT);
    }

    @Override
    public SetFlagResult validateParameters(String parameters, @Nullable CommandSender sender) {
        boolean allowPlaceholders = sender != null && sender.hasPermission(PLACEHOLDER_PERMISSION);
        String sanitized = ClaimDescriptionUtil.sanitize(parameters, allowPlaceholders);
        if (sanitized.isEmpty()) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.MessageRequired));
        }
        if (sanitized.length() > GPFlagsConfig.CLAIM_DESCRIPTION_MAX_LENGTH) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.ClaimDescriptionTooLong,
                    String.valueOf(GPFlagsConfig.CLAIM_DESCRIPTION_MAX_LENGTH)));
        }
        return new SetFlagResult(true, this.getSetMessage(sanitized));
    }

    @Override
    public String normalizeParameters(String parameters, @Nullable CommandSender sender) {
        boolean allowPlaceholders = sender != null && sender.hasPermission(PLACEHOLDER_PERMISSION);
        return ClaimDescriptionUtil.sanitize(parameters, allowPlaceholders);
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.SetClaimDescription, parameters);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.UnsetClaimDescription);
    }
}
