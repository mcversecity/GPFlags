package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.util.CrossClaimFlowUtil;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PistonMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import java.util.List;
import java.util.Objects;

public class FlagDef_AllowCrossClaimPistons extends FlagDefinition {

    public FlagDef_AllowCrossClaimPistons(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        if (shouldAllowPiston(event.getBlock(), event.getDirection(), event.getBlocks())) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        if (shouldAllowPiston(event.getBlock(), event.getDirection(), event.getBlocks())) {
            event.setCancelled(false);
        }
    }

    private boolean shouldAllowPiston(Block pistonBlock, BlockFace direction, List<Block> blocks) {
        if (GriefPrevention.instance.config_pistonMovement == PistonMode.IGNORED) {
            return false;
        }
        if (!GriefPrevention.instance.claimsEnabledForWorld(pistonBlock.getWorld())) {
            return false;
        }

        Claim pistonClaim = CrossClaimFlowUtil.getClaimAt(pistonBlock.getLocation());
        Claim crossedClaim = findCrossedClaim(pistonBlock, direction, blocks, pistonClaim);

        return CrossClaimFlowUtil.shouldAllowCrossClaim(
                plugin.getFlagManager(),
                pistonClaim,
                crossedClaim,
                pistonBlock.getWorld(),
                getName());
    }

    private Claim findCrossedClaim(Block pistonBlock, BlockFace direction, List<Block> blocks, Claim pistonClaim) {
        if (blocks.isEmpty()) {
            return CrossClaimFlowUtil.getClaimAt(pistonBlock.getRelative(direction).getLocation(), pistonClaim);
        }

        Claim crossedClaim = null;
        for (Block block : blocks) {
            Claim blockClaim = CrossClaimFlowUtil.getClaimAt(block.getLocation(), pistonClaim);
            if (!Objects.equals(pistonClaim, blockClaim)) {
                crossedClaim = blockClaim;
                break;
            }
        }

        Claim invadedClaim = CrossClaimFlowUtil.getClaimAt(pistonBlock.getRelative(direction).getLocation(), pistonClaim);
        if (!Objects.equals(pistonClaim, invadedClaim)) {
            crossedClaim = invadedClaim;
        }

        return crossedClaim;
    }

    @Override
    public String getName() {
        return "AllowCrossClaimPistons";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnabledAllowCrossClaimPistons);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisabledAllowCrossClaimPistons);
    }
}
