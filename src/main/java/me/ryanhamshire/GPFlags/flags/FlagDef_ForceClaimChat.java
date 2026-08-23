package me.ryanhamshire.GPFlags.flags;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.GPFlagsConfig;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.TextMode;
import me.ryanhamshire.GPFlags.hooks.EssentialsChatHook;
import me.ryanhamshire.GPFlags.hooks.PlaceholderApiHook;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlagDef_ForceClaimChat extends PlayerMovementFlagDefinition {

    private static final int LOCAL_RADIUS_SQUARED = 320 * 320;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** Players in a ForceClaimChat claim whose message did not start with '!'. Marked at LOWEST before Essentials strips shout. */
    private final Set<UUID> pendingForceLocal = ConcurrentHashMap.newKeySet();
    /** Players whose Essentials LocalChatEvent we cancelled so GPFlags can send the lonely notice. */
    private final Set<UUID> suppressedEssentialsLonely = ConcurrentHashMap.newKeySet();

    public FlagDef_ForceClaimChat(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
        EssentialsChatHook.register(plugin, this);
    }

    public boolean isPendingForceLocal(UUID uuid) {
        return pendingForceLocal.contains(uuid);
    }

    public void markSuppressedEssentialsLonely(UUID uuid) {
        suppressedEssentialsLonely.add(uuid);
    }

    @Override
    public void onChangeClaim(Player player, Location lastLocation, Location to, Claim claimFrom, Claim claimTo, @Nullable Flag flagFrom, @Nullable Flag flagTo) {
        // Notify player when entering a claim with ForceClaimChat enabled
        if (flagTo != null && (flagFrom == null || !flagFrom.equals(flagTo))) {
            String message = plugin.getFlagsDataStore().getMessage(Messages.ForceClaimChatNotification);
            MessagingUtil.sendMessage(player, TextMode.Info + message);
        }
    }

    /**
     * Detect force-local (and ! shout bypass) before Essentials can strip the shout prefix.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChatMark(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (event.getMessage().startsWith("!")) {
            return;
        }
        if (this.getFlagInstanceAtLocation(player.getLocation(), player) == null) {
            return;
        }
        pendingForceLocal.add(player.getUniqueId());
    }

    /**
     * Legacy backup: setFormat + recipient filter after Essentials' LOWEST/NORMAL handlers.
     * Paper display is driven by {@link #onPaperChat}; keep the event alive with %2$s for InteractiveChat.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!pendingForceLocal.contains(uuid)) {
            return;
        }
        if (event.isCancelled()) {
            if (!suppressedEssentialsLonely.contains(uuid)) {
                return;
            }
            event.setCancelled(false);
        }

        String message = event.getMessage();
        String format = buildLegacyFormat(player);
        event.setFormat(format);

        Location playerLoc = player.getLocation();
        event.getRecipients().clear();
        for (Player recipient : plugin.getServer().getOnlinePlayers()) {
            if (isInLocalRange(playerLoc, recipient)) {
                event.getRecipients().add(recipient);
            }
        }

        plugin.getLogger().info(ChatColor.stripColor(String.format(format, player.getDisplayName(), message)));
    }

    /**
     * Source of truth on Paper: re-filter viewers and set an Adventure ChatRenderer so Essentials'
     * global [G] renderer does not win the display fight. Does not stringify the message body
     * so InteractiveChat placeholders stay intact.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPaperChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!pendingForceLocal.remove(uuid)) {
            return;
        }
        boolean suppressed = suppressedEssentialsLonely.remove(uuid);
        if (event.isCancelled()) {
            if (!suppressed) {
                return;
            }
            event.setCancelled(false);
        }

        Location playerLoc = player.getLocation();
        event.viewers().removeIf(audience -> {
            if (!(audience instanceof Player)) {
                return false;
            }
            return !isInLocalRange(playerLoc, (Player) audience);
        });

        long playerViewers = 0;
        for (Audience audience : event.viewers()) {
            if (audience instanceof Player) {
                playerViewers++;
            }
        }
        if (playerViewers <= 1) {
            MessagingUtil.sendMessage(player, plugin.getFlagsDataStore().getMessage(Messages.ForceClaimChatNoOneAround));
        }

        final String resolvedTemplate = buildResolvedTemplate(player);
        event.renderer((source, sourceDisplayName, message, viewer) -> renderLocalFormat(resolvedTemplate, message));
    }

    /** Cleanup after Paper chat finishes (legacy MONITOR would run before AsyncChatEvent). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPaperChatCleanup(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingForceLocal.remove(uuid);
        suppressedEssentialsLonely.remove(uuid);
    }

    private static boolean isInLocalRange(Location senderLoc, Player recipient) {
        return recipient.getWorld().equals(senderLoc.getWorld())
                && recipient.getLocation().distanceSquared(senderLoc) <= LOCAL_RADIUS_SQUARED;
    }

    private String resolvePrefix(Player player, @Nullable Claim claim) {
        FlagDefinition prefixDef = this.plugin.getFlagManager().getFlagDefinitionByName("ForceClaimChatPrefix");
        Flag prefixFlag = prefixDef != null
                ? prefixDef.getFlagInstanceAtLocation(player.getLocation(), player)
                : null;
        if (prefixFlag != null) {
            return prefixFlag.parameters;
        }
        if (claim != null) {
            return "[" + claim.getID() + "]";
        }
        return "[wilderness]";
    }

    /**
     * Template with placeholders resolved and &amp; colors translated, still containing %message%.
     */
    private String buildResolvedTemplate(Player player) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), false, null);
        String prefix = resolvePrefix(player, claim);

        String format = GPFlagsConfig.FORCE_LOCAL_CHAT_FORMAT
                .replace("%prefix%", prefix)
                .replace("%displayname%", player.getDisplayName());

        if (claim != null) {
            format = format.replace("%claimnumber%", String.valueOf(claim.getID()));
        } else {
            format = format.replace("%claimnumber%", "wilderness");
        }

        format = PlaceholderApiHook.addPlaceholders(player, format);
        return ChatColor.translateAlternateColorCodes('&', format);
    }

    private String buildLegacyFormat(Player player) {
        final String messageToken = "\0GPFLAGS_MESSAGE\0";
        String format = buildResolvedTemplate(player).replace("%message%", messageToken);
        return format.replace("%", "%%").replace(messageToken, "%2$s");
    }

    private static Component renderLocalFormat(String resolvedTemplate, Component message) {
        String[] parts = resolvedTemplate.split("%message%", 2);
        String before = parts[0];
        String after = parts.length > 1 ? parts[1] : "";

        Component beforeComp = LEGACY.deserialize(before);
        Component afterComp = after.isEmpty() ? Component.empty() : LEGACY.deserialize(after);

        String lastColors = ChatColor.getLastColors(before);
        if (!lastColors.isEmpty()) {
            Style style = LEGACY.deserialize(lastColors + "x").style();
            message = message.applyFallbackStyle(style);
        }

        return beforeComp.append(message).append(afterComp);
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
