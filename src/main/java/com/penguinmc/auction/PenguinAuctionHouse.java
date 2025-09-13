package com.penguinmc.auction;

import net.kyori.adventure.text.Component;
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

public class PenguinAuctionHouse extends JavaPlugin implements Listener, CommandExecutor {

    private static PenguinAuctionHouse instance;

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
        }, 20*60, 20*60);

        getLogger().info("PenguinAuctionHouse enabled!");
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
                p.sendMessage(Component.text("§cUsage: /ah sell <amount> <currency>"));
                return true;
            }

            Material currency;
            try {
                currency = Material.valueOf(args[2].toUpperCase());
            } catch (Exception e) {
                p.sendMessage(Component.text("§cCurrency invalid! Must be: DIAMOND, IRON_INGOT, NETHERITE_INGOT, EMERALD, GOLD_INGOT"));
                return true;
            }

            if (!validCurrencies.contains(currency)) {
                p.sendMessage(Component.text("§cCurrency not supported!"));
                return true;
            }

            int amount;
            try { amount = Integer.parseInt(args[1]); } catch (Exception e) { p.sendMessage(Component.text("§cAmount invalid!")); return true; }

            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                p.sendMessage(Component.text("§cYou must hold an item to sell!"));
                return true;
            }

            int maxSlots = defaultSlots;
            if (p.hasPermission("penguinah.slots.3")) maxSlots = 3;
            if (p.hasPermission("penguinah.slots.4")) maxSlots = 4;
            if (p.hasPermission("penguinah.slots.5")) maxSlots = 5;

            List<AuctionItem> listings = playerListings.getOrDefault(p.getUniqueId(), new ArrayList<>());
            if (listings.size() >= maxSlots) {
                p.sendMessage(Component.text("§cYou reached your auction slot limit!"));
                return true;
            }

            ItemStack itemClone = hand.clone();
            itemClone.setAmount(amount);
            hand.setAmount(hand.getAmount() - amount);

            AuctionItem ai = new AuctionItem(UUID.randomUUID(), p.getUniqueId(), itemClone, System.currentTimeMillis() + expireMillis, currency);
            listings.add(ai);
            playerListings.put(p.getUniqueId(), listings);
            activeListings.add(ai);

            p.sendMessage(Component.text("§aItem listed successfully!"));
            return true;
        }

        // Open GUI
        openGUI(p, 0);
        return true;
    }

    private void openGUI(Player p, int page) {
        int size = 54;
        Inventory gui = Bukkit.createInventory(null, size, Component.text("§6PenguinAH"));
        currentPages.put(p.getUniqueId(), page);

        int start = page * 45;
        int end = Math.min(start + 45, activeListings.size());

        for (int i = start; i < end; i++) {
            gui.setItem(i - start, activeListings.get(i).item.clone());
        }

        gui.setItem(45, createButton(Material.ARROW, Component.text("§aPrevious Page")));
        gui.setItem(53, createButton(Material.ARROW, Component.text("§aNext Page")));
        gui.setItem(49, createButton(Material.CHEST, Component.text("§eReset / Retrieve Items")));

        p.openInventory(gui);
    }

    private ItemStack createButton(Material mat, Component name) {
        ItemStack b = new ItemStack(mat);
        ItemMeta m = b.getItemMeta();
        m.displayName(name);
        b.setItemMeta(m);
        return b;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().title().equals(Component.text("§6PenguinAH"))) return;
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        String name = meta.displayName().content();

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
            p.sendMessage(Component.text("§aYour unsold items have been returned!"));
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
                p.sendMessage(Component.text("§cYou cannot buy your own item!"));
                return;
            }

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
                p.sendMessage(Component.text("§cYou don't have enough " + target.currency.name() + " to buy this item!"));
                return;
            }

            p.getInventory().addItem(target.item.clone());
            Player seller = Bukkit.getPlayer(target.seller);
            if (seller != null) seller.getInventory().addItem(new ItemStack(target.currency, costAmount));

            target.sold = true;
            activeListings.remove(target);
            playerListings.get(target.seller).remove(target);

            p.sendMessage(Component.text("§aPurchase successful!"));
            if (seller != null) seller.sendMessage(Component.text("§aYour item has been sold!"));
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
