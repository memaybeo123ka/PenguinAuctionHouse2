package com.penguinmc.auction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class PenguinAuctionHouse extends JavaPlugin {

    private final Map<UUID, List<AuctionItem>> listings = new HashMap<>();
    private final Set<Material> allowed = new HashSet<>(Arrays.asList(
            Material.IRON_INGOT, Material.GOLD_INGOT, Material.COPPER_INGOT,
            Material.NETHERITE_INGOT, Material.DIAMOND, Material.EMERALD
    ));

    @Override
    public void onEnable() {
        getLogger().info("PenguinAuctionHouse enabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("ah")) {
            if (args.length == 0) {
                openAuction(player);
                return true;
            } else if (args[0].equalsIgnoreCase("sell") && args.length == 3) {
                int amount;
                try {
                    amount = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid amount.");
                    return true;
                }
                Material mat = Material.matchMaterial(args[2].toUpperCase());
                if (mat == null || !allowed.contains(mat)) {
                    player.sendMessage("§cInvalid or unsupported material.");
                    return true;
                }
                if (!player.getInventory().contains(mat, amount)) {
                    player.sendMessage("§cYou don't have enough items.");
                    return true;
                }
                listings.putIfAbsent(player.getUniqueId(), new ArrayList<>());
                if (listings.get(player.getUniqueId()).size() >= 2) {
                    player.sendMessage("§cYou can only list 2 items at a time.");
                    return true;
                }
                player.getInventory().removeItem(new ItemStack(mat, amount));
                listings.get(player.getUniqueId()).add(new AuctionItem(mat, amount, player.getUniqueId()));
                player.sendMessage("§aListed " + amount + " " + mat + " for auction.");
                return true;
            }
        }
        return false;
    }

    private void openAuction(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "§bAuction House §7(by PenguinMC)");
        for (List<AuctionItem> items : listings.values()) {
            for (AuctionItem ai : items) {
                ItemStack stack = new ItemStack(ai.getMaterial(), ai.getAmount());
                ItemMeta meta = stack.getItemMeta();
                meta.setDisplayName("§a" + ai.getAmount() + " " + ai.getMaterial());
                List<String> lore = new ArrayList<>();
                lore.add("§7Seller: " + Bukkit.getOfflinePlayer(ai.getSeller()).getName());
                lore.add("§eClick to buy");
                meta.setLore(lore);
                stack.setItemMeta(meta);
                gui.addItem(stack);
            }
        }
        player.openInventory(gui);
    }

    static class AuctionItem {
        private final Material material;
        private final int amount;
        private final UUID seller;

        public AuctionItem(Material material, int amount, UUID seller) {
            this.material = material;
            this.amount = amount;
            this.seller = seller;
        }

        public Material getMaterial() { return material; }
        public int getAmount() { return amount; }
        public UUID getSeller() { return seller; }
    }
}
