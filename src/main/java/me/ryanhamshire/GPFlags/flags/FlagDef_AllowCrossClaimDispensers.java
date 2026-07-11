package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.util.CrossClaimFlowUtil;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDispenseEvent;

public class FlagDef_AllowCrossClaimDispensers extends FlagDefinition {

    public FlagDef_AllowCrossClaimDispensers(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDispense(BlockDispenseEvent event) {
        if (!event.isCancelled()) {
            return;
        }

        Block fromBlock = event.getBlock();
        if (!(fromBlock.getBlockData() instanceof Dispenser)) {
            return;
        }
        Dispenser dispenser = (Dispenser) fromBlock.getBlockData();

        Block toBlock = fromBlock.getRelative(dispenser.getFacing());
        Claim fromClaim = CrossClaimFlowUtil.getClaimAt(fromBlock.getLocation());
        Claim toClaim = CrossClaimFlowUtil.getClaimAt(toBlock.getLocation(), fromClaim);

        if (!CrossClaimFlowUtil.shouldAllowCrossClaim(
                plugin.getFlagManager(), fromClaim, toClaim, fromBlock.getWorld(), getName())) {
            return;
        }

        event.setCancelled(false);
    }

    @Override
    public String getName() {
        return "AllowCrossClaimDispensers";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnabledAllowCrossClaimDispensers);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledAllowCrossClaimDispensers);
    }
}
