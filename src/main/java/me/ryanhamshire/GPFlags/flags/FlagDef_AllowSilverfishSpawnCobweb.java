package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityChangeBlockEvent;

/**
 * Flag to allow silverfish with weaving enchantment to spawn cobwebs.
 * This allows cobweb spawning even when the AllowInfest flag is false.
 * When set, silverfish can place cobwebs (from weaving enchantment) in the claim.
 */
public class FlagDef_AllowSilverfishSpawnCobweb extends FlagDefinition {

    public FlagDef_AllowSilverfishSpawnCobweb(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public String getName() {
        return "AllowSilverfishSpawnCobweb";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnabledAllowSilverfishSpawnCobweb);
    }

    @EventHandler
    public void onSilverfishSpawnCobweb(EntityChangeBlockEvent event) {
        Flag flag = this.getFlagInstanceAtLocation(event.getBlock().getLocation(), null);
        if (flag == null) return;

        // Check if it's a silverfish placing a cobweb
        if (event.getEntity().getType() == EntityType.SILVERFISH &&
            event.getTo() == Material.COBWEB) {
            event.setCancelled(false);
        }
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledAllowSilverfishSpawnCobweb);
    }
}
