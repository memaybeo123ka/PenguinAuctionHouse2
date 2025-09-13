package com.penguinah.penguinah;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class PenguinAH extends JavaPlugin implements Listener, CommandExecutor {

    private static PenguinAH instance;
    private final Map<UUID, List<AuctionItem>> playerListings = new HashMap<>();
    private final List<AuctionItem> activeListings = new ArrayList<>();
    private final Map<UUID, Integer> currentPages = new HashMap<>();
    private int defaultSlots;
    private long expireMillis;

    private final List<Material> validCurrencies = List.of(
            Material.DIAMOND,
            Material.IRON_INGOT,
            Material.NETHERITE_INGOT,
            Material.EMERALD,
            Material.GOLD_INGOT
    );

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        defaultSlots = getConfig().getInt("max_slots_per_player.default", 2);
        expireMillis = getConfig().getLong("listing_expire_hours", 23) * 60 * 60 * 1000;

        getCommand("ah").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        // Task kiểm tra hết hạn
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            Iterator<AuctionItem> it = activeListings.iterator();
            while (it.hasNext()) {
                AuctionItem ai = it.next();
                if (!ai.sold && ai.expireTime <= now) {
                    Player seller = Bukkit.getPlayer(ai.seller);
                    if (seller != null) seller.getInventory().addItem(ai.item.clone());
                    it.remove();
                    List<AuctionItem> list = playerListings.get(ai.seller);
                    if (list != null) list.remove(ai);
                }
            }
        }, 20*60, 20*60); // kiểm tra mỗi phút

        getLogger().info("PenguinAH enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("PenguinAH disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (args.length > 0 && args[0].equalsIgnoreCase("sell")) {
            if (args.length < 3) {
                p.sendMessage("§cUsage: /ah sell <amount> <currency>");
                return true;
            }

            Material currency;
            try {
                currency = Material.valueOf(args[2].toUpperCase());
            } catch (Exception e) {
                p.sendMessage("§cCurrency invalid! Must be: DIAMOND, IRON_INGOT, NETHERITE_INGOT, EMERALD, GOLD_INGOT");
                return true;
            }
            if (!validCurrencies.contains(currency)) {
                p.sendMessage("§cCurrency not supported!");
                return true;
            }

            int amount;
            try { amount = Integer.parseInt(args[1]); } catch (Exception e) { p.sendMessage("§cAmount invalid!"); return true; }

            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                p.sendMessage("§cYou must hold an item to sell!");
                return true;
            }

            int maxSlots = defaultSlots;
            if (p.hasPermission("penguinah.slots.3")) maxSlots = 3;
            if (p.hasPermission("penguinah.slots.4")) maxSlots = 4;
            if (p.hasPermission("penguinah.slots.5")) maxSlots = 5;

            List<AuctionItem> listings = playerListings.getOrDefault(p.getUniqueId(), new ArrayList<>());
            if (listings.size() >= maxSlots) {
                p.sendMessage("§cYou reached your auction slot limit!");
                return true;
            }

            ItemStack itemClone = hand.clone();
            itemClone.setAmount(amount);
            hand.setAmount(hand.getAmount() - amount);

            AuctionItem ai = new AuctionItem(UUID.randomUUID(), p.getUniqueId(), itemClone, System.currentTimeMillis() + expireMillis, currency);
            listings.add(ai);
            playerListings.put(p.getUniqueId(), listings);
            activeListings.add(ai);

            p.sendMessage("§aItem listed successfully!");
            return true;
        }

        // Open GUI
        openGUI(p, 0);
        return true;
    }

    private void openGUI(Player p, int page) {
        int size = 54;
        Inventory gui = Bukkit.createInventory(null, size, "§6PenguinAH");
        currentPages.put(p.getUniqueId(), page);

        int start = page * 45;
        int end = Math.min(start + 45, activeListings.size());

        for (int i = start; i < end; i++) {
            gui.setItem(i - start, activeListings.get(i).item.clone());
        }

        // Bottom buttons
        gui.setItem(45, createButton(Material.ARROW, "§aPrevious Page"));
        gui.setItem(53, createButton(Material.ARROW, "§aNext Page"));
        gui.setItem(49, createButton(Material.CHEST, "§eReset / Retrieve Items"));

        p.openInventory(gui);
    }

    private ItemStack createButton(Material mat, String name) {
        ItemStack b = new ItemStack(mat);
        ItemMeta m = b.getItemMeta();
        m.setDisplayName(name);
        b.setItemMeta(m);
        return b;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().title();
        if (!title.equals("§6PenguinAH")) return;
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String name = meta.getDisplayName();

        if (name.equals("§aNext Page")) {
            int current = currentPages.getOrDefault(p.getUniqueId(), 0);
            openGUI(p, current + 1);
        } else if (name.equals("§aPrevious Page")) {
            int current = currentPages.getOrDefault(p.getUniqueId(), 0);
            if (current > 0) openGUI(p, current - 1);
        } else if (name.equals("§eReset / Retrieve Items")) {
            List<AuctionItem> myList = playerListings.getOrDefault(p.getUniqueId(), new ArrayList<>());
            for (AuctionItem ai : new ArrayList<>(myList)) {
                if (!ai.sold) {
                    p.getInventory().addItem(ai.item.clone());
                    activeListings.remove(ai);
                    myList.remove(ai);
                }
            }
            p.sendMessage("§aYour unsold items have been returned!");
            openGUI(p, 0);
        } else {
            // Buying logic
            AuctionItem target = null;
            for (AuctionItem ai : activeListings) {
                if (ai.item.isSimilar(clicked)) {
                    target = ai;
                    break;
                }
            }
            if (target == null) return;
            if (target.seller.equals(p.getUniqueId())) {
                p.sendMessage("§cYou cannot buy your own item!");
                return;
            }

            // Check currency
            int costAmount = target.item.getAmount();
            boolean hasEnough = false;
            for (ItemStack invItem : p.getInventory().getContents()) {
                if (invItem != null && invItem.getType() == target.currency && invItem.getAmount() >= costAmount) {
                    invItem.setAmount(invItem.getAmount() - costAmount);
                    hasEnough = true;
                    break;
                }
            }
            if (!hasEnough) {
                p.sendMessage("§cYou don't have enough " + target.currency.name() + " to buy this item!");
                return;
            }

            // Give item
            p.getInventory().addItem(target.item.clone());
            Player seller = Bukkit.getPlayer(target.seller);
            if (seller != null) seller.getInventory().addItem(new ItemStack(target.currency, costAmount));
            target.sold = true;
            activeListings.remove(target);
            playerListings.get(target.seller).remove(target);

            p.sendMessage("§aPurchase successful!");
            if (seller != null) seller.sendMessage("§aYour item has been sold!");
            openGUI(p, currentPages.getOrDefault(p.getUniqueId(), 0));
        }
    }

    private static class AuctionItem {
        UUID id;
        UUID seller;
        ItemStack item;
        long expireTime;
        Material currency;
        boolean sold;

        AuctionItem(UUID id, UUID seller, ItemStack item, long expireTime, Material currency) {
            this.id = id;
            this.seller = seller;
            this.item = item;
            this.expireTime = expireTime;
            this.currency = currency;
            this.sold = false;
        }
    }

    public static PenguinAH getInstance() { return instance; }
}
