package io.github.ashtray01.fartmc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

final class FoodItems implements Listener {
    static final NamespacedKey FOOD_ID = new NamespacedKey("fartmc", "food_id");
    private final FartPlugin plugin;
    FoodItems(FartPlugin plugin) { this.plugin = plugin; }
    static FoodType type(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return FoodType.parse(item.getItemMeta().getPersistentDataContainer().get(FOOD_ID, PersistentDataType.STRING));
    }
    static boolean marked(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(FOOD_ID);
    }
    ItemStack create(FoodType type, int amount) {
        ItemStack item = new ItemStack(Material.BREAD, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.title, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(type.description, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("Действие: " + FoodSettings.read(plugin.getConfig(), type).duration() / 20 + " сек.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(FOOD_ID, PersistentDataType.STRING, type.id);
        meta.setItemModel(type.key());
        var food = meta.getFood();
        food.setNutrition(5); food.setSaturation(6); food.setCanAlwaysEat(true); meta.setFood(food);
        item.setItemMeta(meta);
        return item;
    }
    void register() {
        unregister();
        for (FoodType type : FoodType.values()) {
            if (!FoodSettings.read(plugin.getConfig(), type).enabled()) continue;
            ShapedRecipe recipe = new ShapedRecipe(type.key(), create(type, 1));
            switch (type) {
                case BELYASH -> {
                    recipe.shape("WWW", "CRC", "WBW");
                    ingredient(recipe, 'W', Material.WHEAT); ingredient(recipe, 'C', Material.COOKED_BEEF);
                    ingredient(recipe, 'R', Material.ROTTEN_FLESH); ingredient(recipe, 'B', Material.BREAD);
                }
                case CHEBUREK -> {
                    recipe.shape("WEW", "MPM", "WBW");
                    ingredient(recipe, 'W', Material.WHEAT); ingredient(recipe, 'E', Material.EGG);
                    ingredient(recipe, 'M', Material.MILK_BUCKET); ingredient(recipe, 'P', Material.BAKED_POTATO);
                    ingredient(recipe, 'B', Material.BREAD);
                }
                case SHAURMA -> {
                    recipe.shape("BFB", "RER", "MBM");
                    ingredient(recipe, 'B', Material.BREAD); ingredient(recipe, 'F', Material.BLAZE_POWDER);
                    ingredient(recipe, 'R', Material.ROTTEN_FLESH); ingredient(recipe, 'E', Material.FERMENTED_SPIDER_EYE);
                    ingredient(recipe, 'M', Material.MAGMA_CREAM);
                }
            }
            recipe.setCategory(CraftingBookCategory.MISC);
            if (!Bukkit.addRecipe(recipe)) throw new IllegalStateException("Cannot register " + type.key());
        }
        Bukkit.getOnlinePlayers().forEach(p -> { discover(p); refreshNames(p.getInventory()); });
    }
    private void ingredient(ShapedRecipe recipe, char symbol, Material material) {
        recipe.setIngredient(symbol, new RecipeChoice.ExactChoice(new ItemStack(material)));
    }
    void unregister() {
        for (FoodType type : FoodType.values()) {
            Bukkit.removeRecipe(type.key()); Bukkit.removeRecipe(type.legacyKey());
        }
    }
    void discover(Player p) {
        for (FoodType type : FoodType.values()) {
            p.undiscoverRecipe(type.legacyKey());
            if (Bukkit.getRecipe(type.key()) != null) p.discoverRecipe(type.key());
        }
    }
    boolean refreshName(ItemStack item) {
        FoodType type = type(item);
        if (type == null) return false;
        ItemMeta meta = item.getItemMeta();
        boolean changed = false;
        if (!type.id.equals(meta.getPersistentDataContainer().get(FOOD_ID, PersistentDataType.STRING))) {
            meta.getPersistentDataContainer().set(FOOD_ID, PersistentDataType.STRING, type.id);
            var lore = meta.lore();
            var plain = PlainTextComponentSerializer.plainText();
            if (lore != null && lore.size() == 2 && plain.serialize(lore.getFirst()).equals(type.description)
                    && plain.serialize(lore.get(1)).matches("Действие: \\d+ сек\\.")) {
                lore = new ArrayList<>(lore);
                lore.set(1, Component.text("Действие: " + FoodSettings.read(plugin.getConfig(), type).duration() / 20 + " сек.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
            }
            changed = true;
        }
        if (type.legacyKey().equals(meta.getItemModel())) {
            meta.setItemModel(type.key()); changed = true;
        }
        String previous = meta.displayName() == null ? "" : PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        boolean legacy = (type == FoodType.CHEBUREK && previous.equals("Хачебурек"))
                || (type == FoodType.SHAURMA && previous.equals("Отравленный чебурек"));
        if (legacy) { // Preserve names chosen by players with an anvil, while migrating identity.
            meta.displayName(Component.text(type.title, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            changed = true;
        }
        if (!changed) return false;
        item.setItemMeta(meta);
        return true;
    }
    private void refreshNames(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (refreshName(item)) inventory.setItem(slot, item);
        }
    }
    @EventHandler public void join(PlayerJoinEvent e) { discover(e.getPlayer()); refreshNames(e.getPlayer().getInventory()); }
    @EventHandler(ignoreCancelled = true) public void open(InventoryOpenEvent e) { refreshNames(e.getInventory()); }
    @EventHandler(ignoreCancelled = true) public void pickup(EntityPickupItemEvent e) {
        ItemStack item = e.getItem().getItemStack();
        if (refreshName(item)) e.getItem().setItemStack(item);
    }
    private boolean ours(Recipe recipe) {
        return recipe instanceof Keyed keyed && Arrays.stream(FoodType.values()).anyMatch(t -> t.key().equals(keyed.getKey()));
    }
    private boolean allowed(CraftingInventory inventory, Player player) {
        return player.hasPermission("fartmc.craft") && Arrays.stream(inventory.getMatrix()).noneMatch(FoodItems::marked);
    }
    @EventHandler public void prepare(PrepareItemCraftEvent e) {
        if (ours(e.getRecipe()) && e.getView().getPlayer() instanceof Player p && !allowed(e.getInventory(), p))
            e.getInventory().setResult(null);
    }
    @EventHandler(ignoreCancelled = true) public void craft(CraftItemEvent e) {
        if (ours(e.getRecipe()) && e.getWhoClicked() instanceof Player p && !allowed(e.getInventory(), p)) e.setCancelled(true);
        // Vanilla owns all consumption and milk-bucket remainders, including shift-click.
    }
}
