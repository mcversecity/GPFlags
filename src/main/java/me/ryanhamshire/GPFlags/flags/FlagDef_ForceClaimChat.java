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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        String prefix = "";

        // Check if player is trying to bypass local chat with "!" prefix
        if (message.startsWith("!")) {
            Flag flag = this.getFlagInstanceAtLocation(player.getLocation(), player);
            if (flag != null) {
                // Remove the "!" prefix and let the message go through normally
                event.setMessage(message.substring(1));
            }
            return;
        }

        // Check if the flag is set at the player's location
        Flag flag = this.getFlagInstanceAtLocation(player.getLocation(), player);
        if (flag == null) return;

        // Cancel the original event
        event.setCancelled(true);

        // Get the claim for formatting
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), false, null);

        // Get the local chat format from config
        String format = GPFlagsConfig.FORCE_LOCAL_CHAT_FORMAT;

        // Replace custom placeholders first (before PAPI)
        String formattedMessage = format
                .replace("%message%", message)
                .replace("%prefix%", prefix)
                .replace("%displayname%", player.getDisplayName());

        // Replace claim number if available
        if (claim != null) {
            formattedMessage = formattedMessage.replace("%claimnumber%", String.valueOf(claim.getID()));
        } else {
            formattedMessage = formattedMessage.replace("%claimnumber%", "wilderness");
        }

        // Use PlaceholderAPI for all other placeholders
        formattedMessage = PlaceholderApiHook.addPlaceholders(player, formattedMessage);

        // Translate color codes
        formattedMessage = ChatColor.translateAlternateColorCodes('&', formattedMessage);

        // Get nearby players within 320 blocks and send them the message
        final String finalMessage = formattedMessage;
        for (Player recipient : plugin.getServer().getOnlinePlayers()) {
            if (recipient.getWorld().equals(player.getWorld())) {
                double distance = recipient.getLocation().distance(player.getLocation());
                if (distance <= 320) {
                    recipient.sendMessage(finalMessage);
                }
            }
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
