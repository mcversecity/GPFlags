package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.SetFlagResult;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

/**
 * Companion flag for ForceClaimChat. When set, replaces the default [%claimnumber%] chat prefix.
 * Supports & color codes and spaces in the parameter.
 */
public class FlagDef_ForceClaimChatPrefix extends FlagDefinition {

    public FlagDef_ForceClaimChatPrefix(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public String getName() {
        return "ForceClaimChatPrefix";
    }

    @Override
    public SetFlagResult validateParameters(String parameters, @Nullable CommandSender sender) {
        if (parameters.isEmpty()) {
            return new SetFlagResult(false, new MessageSpecifier(Messages.MessageRequired));
        }
        return new SetFlagResult(true, this.getSetMessage(parameters));
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.SetForceClaimChatPrefix, parameters);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.UnsetForceClaimChatPrefix);
    }
}
