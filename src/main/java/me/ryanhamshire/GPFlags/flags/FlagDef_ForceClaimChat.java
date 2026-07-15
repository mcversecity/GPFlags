package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.GPFlagsConfig;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.TextMode;
import me.ryanhamshire.GPFlags.hooks.PlaceholderApiHook;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.Nullable;

public class FlagDef_ForceClaimChat extends PlayerMovementFlagDefinition {

    public FlagDef_ForceClaimChat(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public void onChangeClaim(Player player, Location lastLocation, Location to, Claim claimFrom, Claim claimTo, @Nullable Flag flagFrom, @Nullable Flag flagTo) {
        // Notify player when entering a claim with ForceClaimChat enabled
        if (flagTo != null && (flagFrom == null || !flagFrom.equals(flagTo))) {
            String message = plugin.getFlagsDataStore().getMessage(Messages.ForceClaimChatNotification);
            MessagingUtil.sendMessage(player, TextMode.Info + message);
        }
    }

    // HIGH: after Essentials Chat (LOWEST format + NORMAL recipients) so prefix/color stick;
    // keep event alive with %2$s so InteractiveChat can still process placeholders.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Bypass local chat with "!" prefix; keep the "!" in the global message
        if (message.startsWith("!")) {
            return;
        }

        // Check if the flag is set at the player's location
        Flag flag = this.getFlagInstanceAtLocation(player.getLocation(), player);
        if (flag == null) return;

        // Get the claim for formatting
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), false, null);

        // Custom prefix replaces the default [%claimnumber%] slot
        FlagDefinition prefixDef = this.plugin.getFlagManager().getFlagDefinitionByName("ForceClaimChatPrefix");
        Flag prefixFlag = prefixDef != null
                ? prefixDef.getFlagInstanceAtLocation(player.getLocation(), player)
                : null;
        String prefix;
        if (prefixFlag != null) {
            prefix = prefixFlag.parameters;
        } else if (claim != null) {
            prefix = "[" + claim.getID() + "]";
        } else {
            prefix = "[wilderness]";
        }

        // Build format template only — leave the player message as Bukkit %2$s for InteractiveChat
        final String messageToken = "\0GPFLAGS_MESSAGE\0";
        String format = GPFlagsConfig.FORCE_LOCAL_CHAT_FORMAT
                .replace("%message%", messageToken)
                .replace("%prefix%", prefix)
                .replace("%displayname%", player.getDisplayName());

        if (claim != null) {
            format = format.replace("%claimnumber%", String.valueOf(claim.getID()));
        } else {
            format = format.replace("%claimnumber%", "wilderness");
        }

        format = PlaceholderApiHook.addPlaceholders(player, format);
        format = ChatColor.translateAlternateColorCodes('&', format);
        // Escape % for String.format, then restore the chat message placeholder
        format = format.replace("%", "%%").replace(messageToken, "%2$s");

        event.setFormat(format);

        // Limit recipients to same-world players within 320 blocks (do not cancel/resend)
        Location playerLoc = player.getLocation();
        event.getRecipients().clear();
        for (Player recipient : plugin.getServer().getOnlinePlayers()) {
            if (recipient.getWorld().equals(player.getWorld())
                    && recipient.getLocation().distanceSquared(playerLoc) <= 320 * 320) {
                event.getRecipients().add(recipient);
            }
        }

        plugin.getLogger().info(ChatColor.stripColor(String.format(format, player.getDisplayName(), message)));

        if (event.getRecipients().size() == 1) {
            player.sendMessage("There is no one around to hear you.");
        }
    }

    @Override
    public String getName() {
        return "ForceClaimChat";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnabledForceClaimChat);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledForceClaimChat);
    }
}
