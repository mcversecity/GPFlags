package me.ryanhamshire.GPFlags.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public final class KitPvPSword1Rules {

    private static final Set<Material> ALLOWED_ARMOR;
    private static final Set<Material> ALLOWED_WEAPONS;

    static {
        Set<Material> armor = EnumSet.of(
                Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS
        );
        addIfPresent(armor,
                "COPPER_HELMET", "COPPER_CHESTPLATE", "COPPER_LEGGINGS", "COPPER_BOOTS");
        ALLOWED_ARMOR = Collections.unmodifiableSet(armor);

        Set<Material> weapons = EnumSet.of(
                Material.DIAMOND_SWORD, Material.IRON_SWORD, Material.WOODEN_SWORD, Material.STONE_SWORD,
                Material.DIAMOND_AXE, Material.IRON_AXE, Material.WOODEN_AXE, Material.STONE_AXE
        );
        addIfPresent(weapons, "COPPER_SWORD", "COPPER_AXE");
        ALLOWED_WEAPONS = Collections.unmodifiableSet(weapons);
    }

    private KitPvPSword1Rules() {
    }

    private static void addIfPresent(Set<Material> set, String... names) {
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                set.add(material);
            }
        }
    }

    public static boolean isCompliant(Player player) {
        return isCompliant(player.getInventory());
    }

    public static boolean isCompliant(PlayerInventory inventory) {
        for (ItemStack armor : inventory.getArmorContents()) {
            if (!isAllowedArmor(armor)) return false;
        }
        if (!isAllowedMainHand(inventory.getItemInMainHand())) return false;
        return isAllowedOffHand(inventory.getItemInOffHand());
    }

    private static boolean isAllowedArmor(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        Material type = item.getType();
        if (type == Material.ELYTRA) return false;
        return ALLOWED_ARMOR.contains(type);
    }

    private static boolean isAllowedMainHand(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        return ALLOWED_WEAPONS.contains(item.getType());
    }

    private static boolean isAllowedOffHand(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        return item.getType() == Material.SHIELD;
    }

    public static String getRequirementsMessage() {
        return "Allowed: Diamond/Iron/Copper/Leather armor. Diamond/Iron/Copper/Wood/Stone swords & axes. Shield (offhand). No Netherite. No Elytra.";
    }
}
