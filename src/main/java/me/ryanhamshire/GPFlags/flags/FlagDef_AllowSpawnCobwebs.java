package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityChangeBlockEvent;

/**
 * Flag to allow any mob with weaving enchantment to spawn cobwebs.
 * When set, any entity can place cobwebs (from weaving enchantment) in the claim.
 */
public class FlagDef_AllowSpawnCobwebs extends FlagDefinition {

    public FlagDef_AllowSpawnCobwebs(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public String getName() {
        return "AllowSpawnCobwebs";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnabledAllowSpawnCobwebs);
    }

    @EventHandler
    public void onSpawnCobweb(EntityChangeBlockEvent event) {
        Flag flag = this.getFlagInstanceAtLocation(event.getBlock().getLocation(), null);
        if (flag == null) return;

        if (event.getTo() == Material.COBWEB) {
            event.setCancelled(false);
        }
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledAllowSpawnCobwebs);
    }
}
