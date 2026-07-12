package me.ryanhamshire.GPFlags.flags;

import me.ryanhamshire.GPFlags.Flag;
import me.ryanhamshire.GPFlags.FlagManager;
import me.ryanhamshire.GPFlags.GPFlags;
import me.ryanhamshire.GPFlags.MessageSpecifier;
import me.ryanhamshire.GPFlags.Messages;
import me.ryanhamshire.GPFlags.TextMode;
import me.ryanhamshire.GPFlags.util.KitPvPSword1Rules;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlagDef_KitPvPSword1 extends PlayerMovementFlagDefinition {

    private static final long WARNING_COOLDOWN_MS = 3000L;

    private final Map<UUID, Long> lastWarningTime = new HashMap<>();

    public FlagDef_KitPvPSword1(FlagManager manager, GPFlags plugin) {
        super(manager, plugin);
    }

    @Override
    public void onChangeClaim(@NotNull Player player, Location lastLocation, @NotNull Location to, Claim claimFrom, Claim claimTo, @Nullable Flag flagFrom, @Nullable Flag flagTo) {
        if (flagTo == null) return;
        if (Util.shouldBypass(player, claimTo, flagTo)) return;
        sendEnterMessage(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity defender = event.getEntity();
        if (!isPlayerOrPet(defender)) return;

        Player attacker = resolveAttackingPlayer(event.getDamager());
        if (attacker == null) return;
        if (shouldBlockAttacker(attacker)) {
            event.setCancelled(true);
            sendViolationWarning(attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        Entity defender = event.getEntity();
        if (!isPlayerOrPet(defender)) return;

        Player attacker = resolveAttackingPlayer(event.getCombuster());
        if (attacker == null) return;
        if (shouldBlockAttacker(attacker)) {
            event.setCancelled(true);
            sendViolationWarning(attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (shouldBlockAttacker(player)) {
            event.setCancelled(true);
            sendViolationWarning(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        ProjectileSource shooter = event.getEntity().getShooter();
        if (!(shooter instanceof Player)) return;
        Player player = (Player) shooter;
        if (shouldBlockAttacker(player)) {
            event.setCancelled(true);
            sendViolationWarning(player);
        }
    }

    private boolean shouldBlockAttacker(Player attacker) {
        Flag flag = this.getFlagInstanceAtLocation(attacker.getLocation(), attacker);
        if (flag == null) return false;
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(attacker.getLocation(), false, null);
        if (Util.shouldBypass(attacker, claim, flag)) return false;
        return !KitPvPSword1Rules.isCompliant(attacker);
    }

    @Nullable
    private Player resolveAttackingPlayer(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }

    private boolean isPlayerOrPet(Entity entity) {
        if (entity instanceof Player) return true;
        List<Entity> passengers = entity.getPassengers();
        return !passengers.isEmpty() && passengers.get(0) instanceof Player;
    }

    private void sendEnterMessage(Player player) {
        MessagingUtil.sendMessage(player, TextMode.Info, Messages.KitPvPSword1Enter, KitPvPSword1Rules.getRequirementsMessage());
    }

    private void sendViolationWarning(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastWarningTime.get(player.getUniqueId());
        if (last != null && now - last < WARNING_COOLDOWN_MS) return;
        lastWarningTime.put(player.getUniqueId(), now);

        MessagingUtil.sendMessage(player, TextMode.Warn, Messages.KitPvPSword1Violation);
        MessagingUtil.sendMessage(player, TextMode.Instr, Messages.KitPvPSword1Requirements);
    }

    @Override
    public String getName() {
        return "kitpvp-sword1";
    }

    @Override
    public MessageSpecifier getSetMessage(String parameters) {
        return new MessageSpecifier(Messages.EnableKitPvPSword1);
    }

    @Override
    public MessageSpecifier getUnSetMessage() {
        return new MessageSpecifier(Messages.DisableKitPvPSword1);
    }

    @Override
    public List<FlagType> getFlagType() {
        return Arrays.asList(FlagType.CLAIM, FlagType.DEFAULT);
    }
}
