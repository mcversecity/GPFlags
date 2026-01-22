package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.*;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityChangeBlockEvent;

/**
 * Flag to allow endermen to move blocks within a claim.
 * This overrides the GriefPrevention global setting "EndermenMoveBlocks: false".
 * When set, endermen can pick up and place blocks in the claim.
 */
public class FlagDef_AllowEndermanMoveBlocks extends FlagDefinition {

    public FlagDef_AllowEndermanMoveBlocks(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public String getName() {
        return "AllowEndermanMoveBlocks";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnabledAllowEndermanMoveBlocks);
    }

    @EventHandler
    public void onEndermanMoveBlock(EntityChangeBlockEvent event) {
        Flag flag = this.getFlagInstanceAtLocation(event.getBlock().getLocation(), null);
        if (flag == null) return;
        if (event.getEntity().getType() == EntityType.ENDERMAN) {
            event.setCancelled(false);
        }
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledAllowEndermanMoveBlocks);
    }
}
