package me.ryanhamshire.GPFlags.hooks;

import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.flags.FlagDef_ForceClaimChat;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

/**
 * Optional EssentialsX Chat hook. When ForceClaimChat owns a message, recipients are limited to
 * the GPFlags local radius (default 320, or ForceClaimChatRadius). Lonely sends are cancelled so
 * Essentials does not emit {@code localNoOne}; GPFlags then uncancels Bukkit/Paper chat and sends
 * {@code ForceClaimChatNoOneAround} after its own viewer filter.
 */
public final class EssentialsChatHook {

    private static final String LOCAL_CHAT_EVENT = "net.essentialsx.api.v2.events.chat.LocalChatEvent";

    private EssentialsChatHook() {
    }

    public static void register(GPFlags plugin, FlagDef_ForceClaimChat flag) {
        final Class<? extends Event> eventClass;
        final Method getPlayer;
        final Method getRecipients;
        final Method setCancelled;
        try {
            eventClass = Class.forName(LOCAL_CHAT_EVENT).asSubclass(Event.class);
            getPlayer = eventClass.getMethod("getPlayer");
            getRecipients = eventClass.getMethod("getRecipients");
            setCancelled = eventClass.getMethod("setCancelled", boolean.class);
        } catch (ClassNotFoundException | NoSuchMethodException | ClassCastException ignored) {
            return;
        }

        EventExecutor executor = (listener, event) -> {
            if (!eventClass.isInstance(event)) {
                return;
            }
            try {
                Player player = (Player) getPlayer.invoke(event);
                UUID uuid = player.getUniqueId();
                if (!flag.isPendingForceLocal(uuid)) {
                    return;
                }
                @SuppressWarnings("unchecked")
                Collection<Object> recipients = (Collection<Object>) getRecipients.invoke(event);
                int inRange = 0;
                for (Object recipient : recipients) {
                    if (!(recipient instanceof Player) || flag.isInLocalRange(player, (Player) recipient)) {
                        inRange++;
                    }
                }
                try {
                    recipients.removeIf(recipient ->
                            recipient instanceof Player && !flag.isInLocalRange(player, (Player) recipient));
                } catch (UnsupportedOperationException | ClassCastException ignored) {
                }
                // Lonely after GPFlags radius, or extras remain on an immutable Essentials list
                if (inRange < 2 || recipients.size() > inRange) {
                    setCancelled.invoke(event, true);
                    flag.markSuppressedEssentialsLonely(uuid);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        };

        plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                new Listener() {
                },
                EventPriority.NORMAL,
                executor,
                plugin,
                true
        );
    }
}
