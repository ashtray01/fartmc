package io.github.ashtray01.fartmc;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

final class FoodCommand implements TabExecutor {
    private final FartPlugin plugin;
    private final FoodItems items;
    private final FoodEffects effects;
    FoodCommand(FartPlugin plugin, FoodItems items, FoodEffects effects) {
        this.plugin = plugin; this.items = items; this.effects = effects;
    }
    private boolean permission(CommandSender sender, String permission) {
        if (sender.hasPermission("fartmc.admin") || sender.hasPermission(permission)) return true;
        sender.sendMessage("Нет права " + permission + "."); return false;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { help(sender); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "recipes" -> {
                if (!permission(sender, "fartmc.craft")) return true;
                if (sender instanceof Player p) items.discover(p);
                sender.sendMessage("Рецепты FartMC (ряды верстака сверху вниз):");
                sender.sendMessage("Беляш: пшеница/пшеница/пшеница; стейк/гнилая плоть/стейк; пшеница/хлеб/пшеница.");
                sender.sendMessage("Хачебурек: пшеница/яйцо/пшеница; молоко/печёный картофель/молоко; пшеница/хлеб/пшеница.");
                sender.sendMessage("Отравленный: хлеб/огненный порошок/хлеб; гнилая плоть/маринованный глаз/гнилая плоть; магмовый крем/хлеб/магмовый крем.");
            }
            case "reload" -> {
                if (!permission(sender, "fartmc.reload")) return true;
                plugin.reloadFoodConfiguration(); sender.sendMessage("FartMC: эффекты завершены, настройки и рецепты обновлены.");
            }
            case "stop" -> {
                if (!permission(sender, "fartmc.admin")) return true;
                Player target = args.length == 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) { sender.sendMessage("Укажите игрока онлайн: /fartmc stop <player>"); return true; }
                effects.stop(target, target.isDead()); sender.sendMessage("Эффект завершён. Cooldown сохранён.");
            }
            case "give" -> {
                if (!permission(sender, "fartmc.give")) return true;
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[1]) : null;
                FoodType type = args.length >= 3 ? FoodType.parse(args[2]) : null;
                int amount = 1;
                try { if (args.length == 4) amount = Integer.parseInt(args[3]); }
                catch (NumberFormatException ex) { amount = 0; }
                if (target == null || type == null || args.length > 4 || amount < 1 || amount > 64) {
                    sender.sendMessage("/fartmc give <player> <belyash|khacheburek|poisoned_cheburek> [1–64]"); return true;
                }
                var leftovers = target.getInventory().addItem(items.create(type, amount));
                int remaining = leftovers.values().stream().mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
                sender.sendMessage("Выдано: " + (amount - remaining) + " × " + type.title + (remaining > 0 ? ". Инвентарь заполнен." : "."));
            }
            default -> help(sender);
        }
        return true;
    }
    private void help(CommandSender sender) {
        sender.sendMessage("/fartmc recipes | give <player> <food> [amount] | stop <player> | reload");
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> choices = new ArrayList<>();
        if (args.length == 1) {
            choices.add("recipes");
            if (sender.hasPermission("fartmc.give")) choices.add("give");
            if (sender.hasPermission("fartmc.admin")) choices.add("stop");
            if (sender.hasPermission("fartmc.reload")) choices.add("reload");
        } else if (args.length == 2 && ((args[0].equalsIgnoreCase("give") && sender.hasPermission("fartmc.give"))
                || (args[0].equalsIgnoreCase("stop") && sender.hasPermission("fartmc.admin"))))
            Bukkit.getOnlinePlayers().forEach(p -> choices.add(p.getName()));
        else if (args.length == 3 && args[0].equalsIgnoreCase("give") && sender.hasPermission("fartmc.give"))
            choices.addAll(List.of("belyash", "khacheburek", "poisoned_cheburek"));
        else if (args.length == 4 && args[0].equalsIgnoreCase("give") && sender.hasPermission("fartmc.give")) choices.addAll(List.of("1", "16", "64"));
        return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))).toList();
    }
}
