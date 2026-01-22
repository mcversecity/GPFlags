package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityChangeBlockEvent;

/**
 * Flag to allow endermen with weaving enchantment to spawn cobwebs.
 * This allows cobweb spawning even when the global "EndermenMoveBlocks: false" is set.
 * When set, endermen can place cobwebs (from weaving enchantment) in the claim.
 */
public class FlagDef_AllowEndermanSpawnCobbleWeb extends FlagDefinition {

    public FlagDef_AllowEndermanSpawnCobbleWeb(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public String getName() {
        return "AllowEndermanSpawnCobbleWeb";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnabledAllowEndermanSpawnCobbleWeb);
    }

    @EventHandler
    public void onEndermanSpawnCobweb(EntityChangeBlockEvent event) {
        Flag flag = this.getFlagInstanceAtLocation(event.getBlock().getLocation(), null);
        if (flag == null) return;

        // Check if it's an enderman placing a cobweb
        if (event.getEntity().getType() == EntityType.ENDERMAN &&
            event.getTo() == Material.COBWEB) {
            event.setCancelled(false);
        }
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledAllowEndermanSpawnCobbleWeb);
    }
}
