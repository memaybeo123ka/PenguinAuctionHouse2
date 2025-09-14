package com.penguinmc.auction;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.logging.Level;

public class PenguinAuctionHouse extends JavaPlugin implements Listener, CommandExecutor {

    private static PenguinAuctionHouse instance;

    private final Map<UUID, List<AuctionItem>> playerListings = new HashMap<>();
    private final List<AuctionItem> activeListings = new ArrayList<>();
    private final Map<UUID, Integer> currentPages = new HashMap<>();

    private int defaultSlots;
    private long expireMillis;

    private final List<Material> validCurrencies = Arrays.asList(
            Material.DIAMOND,
            Material.IRON_INGOT,
            Material.NETHERITE_INGOT,
            Material.EMERALD,
            Material.GOLD_INGOT
    );

    @Override
    public void onEnable() {
        instance = this;
        try {
            saveDefaultConfig();
            defaultSlots = getConfig().getInt("max_slots_per_player.default", 2);
            expireMillis = getConfig().getLong("listing_expire_hours", 23L) * 60L * 60L * 1000L;

            if (getCommand("ah") == null) {
                getLogger().warning("Command 'ah' not defined in plugin.yml");
            } else {
                getCommand("ah").setExecutor(this);
            }

            getServer().getPluginManager().registerEvents(this, this);

            // Task kiểm tra hết hạn (chạy mỗi 1 phút; đổi nếu muốn)
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                long now = System.currentTimeMillis();
                Iterator<AuctionItem> it = activeListings.iterator();
                while (it.hasNext()) {
                    AuctionItem ai = it.next();
                    if (!ai.sold && ai.expireTime <= now) {
                        Player seller = Bukkit.getPlayer(ai.seller);
                        if (seller != null) {
                            Map<Integer, ItemStack> leftover = seller.getInventory().addItem(ai.item.clone());
                            // nếu leftover không rỗng thì drop ra thế giới hoặc gửi tin nhắn
                        }
                        it.remove();
                        List<AuctionItem> list = playerListings.get(ai.seller);
                        if (list != null) list.remove(ai);
                    }
                }
            }, 20L * 60L, 20L * 60L);

            getLogger().info("PenguinAuctionHouse enabled!");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Failed to enable PenguinAuctionHouse", t);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("PenguinAuctionHouse disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (args.length > 0 && args[0].equalsIgnoreCase("sell")) {
            if (args.length < 3) {
                p.sendMessage(ChatColor.RED + "Usage: /ah sell <amount> <currency>");
                return true;
            }

            Material currency;
            try {
                currency = Material.valueOf(args[2].toUpperCase());
            } catch (Exception e) {
                p.sendMessage(ChatColor.RED + "Currency invalid! Must be: DIAMOND, IRON_INGOT, NETHERITE_INGOT, EMERALD, GOLD_INGOT");
                return true;
            }

            if (!validCurrencies.contains(currency)) {
                p.sendMessage(ChatColor.RED + "Currency not supported!");
                return true;
            }

            int amount;
            try { amount = Integer.parseInt(args[1]); } catch (Exception e) { p.sendMessage(ChatColor.RED + "Amount invalid!"); return true; }
            if (amount <= 0) { p.sendMessage(ChatColor.RED + "Amount must be positive!"); return true; }

            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                p.sendMessage(ChatColor.RED + "You must hold an item to sell!");
                return true;
            }

            if (hand.getAmount() < amount) {
                p.sendMessage(ChatColor.RED + "You don't have enough amount in hand!");
                return true;
            }

            int maxSlots = defaultSlots;
            if (p.hasPermission("penguinah.slots.3")) maxSlots = 3;
            if (p.hasPermission("penguinah.slots.4")) maxSlots = 4;
            if (p.hasPermission("penguinah.slots.5")) maxSlots = 5;

            List<AuctionItem> listings = playerListings.getOrDefault(p.getUniqueId(), new ArrayList<>());
            if (listings.size() >= maxSlots) {
                p.sendMessage(ChatColor.RED + "You reached your auction slot limit!");
                return true;
            }

            ItemStack itemClone = hand.clone();
            itemClone.setAmount(amount);

            int newAmount = hand.getAmount() - amount;
            if (newAmount <= 0) {
                p.getInventory().setItemInMainHand(null);
            } else {
                hand.setAmount(newAmount);
            }

            AuctionItem ai = new AuctionItem(UUID.randomUUID(), p.getUniqueId(), itemClone, System.currentTimeMillis() + expireMillis, currency);
            listings.add(ai);
            playerListings.put(p.getUniqueId(), listings);
            activeListings.add(ai);

            p.sendMessage(ChatColor.GREEN + "Item listed successfully!");
            return true;
        }

        // Open GUI (legacy string title for compatibility)
        openGUI(p, 0);
        return true;
    }

    private void openGUI(Player p, int page) {
        int size = 54;
        Inventory gui = Bukkit.createInventory(null, size, ChatColor.GOLD + "PenguinAH");
        currentPages.put(p.getUniqueId(), page);

        int start = page * 45;
        int end = Math.min(start + 45, activeListings.size());

        for (int i = start; i < end; i++) {
            gui.setItem(i - start, activeListings.get(i).item.clone());
        }

        gui.setItem(45, createButton(Material.ARROW, ChatColor.GREEN + "Previous Page"));
        gui.setItem(53, createButton(Material.ARROW, ChatColor.GREEN + "Next Page"));
        gui.setItem(49, createButton(Material.CHEST, ChatColor.YELLOW + "Reset / Retrieve Items"));

        p.openInventory(gui);
    }

    private ItemStack createButton(Material mat, String name) {
        ItemStack b = new ItemStack(mat);
        ItemMeta m = b.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            b.setItemMeta(m);
        }
        return b;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle(); // legacy-compatible
        if (!ChatColor.stripColor(title).equals("PenguinAH")) return;
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        String name = meta.getDisplayName();

        if (name.equals(ChatColor.GREEN + "Next Page")) {
            int current = currentPages.getOrDefault(p.getUniqueId(), 0);
            openGUI(p, current + 1);
        } else if (name.equals(ChatColor.GREEN + "Previous Page")) {
            int current = currentPages.getOrDefault(p.getUniqueId(), 0);
            if (current > 0) openGUI(p, current - 1);
        } else if (name.equals(ChatColor.YELLOW + "Reset / Retrieve Items")) {
            List<AuctionItem> myList = playerListings.getOrDefault(p.getUniqueId(), new ArrayList<>());
            for (AuctionItem ai : new ArrayList<>(myList)) {
                if (!ai.sold) {
                    p.getInventory().addItem(ai.item.clone());
                    activeListings.remove(ai);
                    myList.remove(ai);
                }
            }
            p.sendMessage(ChatColor.GREEN + "Your unsold items have been returned!");
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
                p.sendMessage(ChatColor.RED + "You cannot buy your own item!");
                return;
            }

            int costAmount = target.item.getAmount();
            Map<Integer, ItemStack> leftover = p.getInventory().removeItem(new ItemStack(target.currency, costAmount));
            if (!leftover.isEmpty()) {
                p.sendMessage(ChatColor.RED + "You don't have enough " + target.currency.name() + " to buy this item!");
                return;
            }

            p.getInventory().addItem(target.item.clone());
            Player seller = Bukkit.getPlayer(target.seller);
            if (seller != null) seller.getInventory().addItem(new ItemStack(target.currency, costAmount));

            target.sold = true;
            activeListings.remove(target);
            List<AuctionItem> sellerList = playerListings.get(target.seller);
            if (sellerList != null) sellerList.remove(target);

            p.sendMessage(ChatColor.GREEN + "Purchase successful!");
            if (seller != null) seller.sendMessage(ChatColor.GREEN + "Your item has been sold!");
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

    public static PenguinAuctionHouse getInstance() { return instance; }
}
