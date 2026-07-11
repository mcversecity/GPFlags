package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.util.CrossClaimFlowUtil;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockFromToEvent;

public class FlagDef_AllowCrossClaimFluidFlow extends FlagDefinition {

    public FlagDef_AllowCrossClaimFluidFlow(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        if (event.getFace() == BlockFace.DOWN) {
            return;
        }

        Location fromLocation = event.getBlock().getLocation();
        Location toLocation = event.getToBlock().getLocation();
        Claim fromClaim = CrossClaimFlowUtil.getClaimAt(fromLocation);
        Claim toClaim = CrossClaimFlowUtil.getClaimAt(toLocation, fromClaim);

        if (!CrossClaimFlowUtil.shouldAllowCrossClaim(
                plugin.getFlagManager(), fromClaim, toClaim, fromLocation.getWorld(), getName())) {
            return;
        }

        event.setCancelled(false);
    }

    @Override
    public String getName() {
        return "AllowCrossClaimFluidFlow";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnabledAllowCrossClaimFluidFlow);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledAllowCrossClaimFluidFlow);
    }
}
