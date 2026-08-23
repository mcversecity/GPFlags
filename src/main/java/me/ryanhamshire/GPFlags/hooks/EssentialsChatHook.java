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
 * Optional EssentialsX Chat hook. Cancels lonely {@code LocalChatEvent}s when ForceClaimChat
 * already owns the message so Essentials does not send {@code localNoOne}.
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
                Collection<?> recipients = (Collection<?>) getRecipients.invoke(event);
                if (recipients.size() >= 2) {
                    return;
                }
                setCancelled.invoke(event, true);
                flag.markSuppressedEssentialsLonely(uuid);
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
