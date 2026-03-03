package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.FlagsDataStore;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.TextMode;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Arrays;
import java.util.List;

public class FlagDef_NoWindCharge extends FlagDefinition {

    public FlagDef_NoWindCharge(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        // Check if projectile is a wind charge
        if (!(event.getEntity() instanceof WindCharge)) return;

        WindCharge windCharge = (WindCharge) event.getEntity();
        ProjectileSource shooter = windCharge.getShooter();

        // Only process player-thrown wind charges (skip Breeze mobs)
        if (!(shooter instanceof Player)) return;

        Player player = (Player) shooter;

        // Check for flag at hit location
        Flag flag = this.getFlagInstanceAtLocation(windCharge.getLocation(), player);
        if (flag == null) return;

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(windCharge.getLocation(), false, null);

        // Check if player has build trust (shouldBypass returns true if they have trust)
        if (Util.shouldBypass(player, claim, flag)) return;

        // Block the wind charge explosion
        event.setCancelled(true);
        windCharge.remove();

        // Send message to player
        String owner = claim.getOwnerName();

        String msg = new FlagsDataStore().getMessage(Messages.NoWindChargeInClaim);
        msg = msg.replace("{o}", owner).replace("{0}", owner);
        MessagingUtil.sendMessage(player, TextMode.Warn + msg);
    }

    @Override
    public String getName() {
        return "NoWindCharge";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnableNoWindCharge);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisableNoWindCharge);
    }

    @Override
    public List<FlagType> getFlagType() {
        return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT);
    }

}
