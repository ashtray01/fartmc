package io.github.ashtray01.fartmc.integration;

import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.lang.reflect.*;
import java.util.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Runs only as a separate test JAR on the isolated smoke server. No production test commands. */
public final class FoodIntegrationPlugin extends JavaPlugin implements Listener {
    private JavaPlugin plugin;
    private Object items, effects, visuals, protection;
    private ClassLoader loader;
    private World world;
    private Location base;
    private final List<Entity> entities = new ArrayList<>();
    private final List<BlockState> blocks = new ArrayList<>();
    private boolean cancelIgnition, cancelConsumption;
    private int ignitionEvents, checks;
    private ArmorStand movingSeat;
    private Pig rider;
    private double startY;
    private final List<Player> loadPlayers = new ArrayList<>();
    private final Map<UUID, BossBar> visibleBars = new HashMap<>();
    private final Map<UUID, List<String>> messages = new HashMap<>();

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(this::run), 30);
    }
    private void guarded(Runnable body) {
        try { body.run(); }
        catch (Throwable ex) { getLogger().log(java.util.logging.Level.SEVERE, "FOOD TEST FAIL", ex); cleanup(); }
    }
    private Class<?> type(String name) {
        try { return Class.forName("io.github.ashtray01.fartmc." + name, true, loader); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private Object field(Object object, String name) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private Object call(Object object, String name, Object... args) {
        Class<?> clazz = object instanceof Class<?> c ? c : object.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            try { method.setAccessible(true); return method.invoke(object instanceof Class<?> ? null : object, args); }
            catch (IllegalArgumentException ignored) { /* overloaded signature */ }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        throw new IllegalStateException("No method: " + clazz + "." + name);
    }
    private Object food(String id) { return call(type("FoodType"), "parse", id); }
    private ItemStack item(String id) { return (ItemStack)call(items, "create", food(id), 1); }
    private void check(boolean result, String name) {
        if (!result) throw new AssertionError(name);
        checks++;
    }
    private Player player(int index) {
        Cow actor = world.spawn(base.clone().add(index % 5, .1, index / 5), Cow.class, z -> {
            z.setAI(false); z.setSilent(true); z.setInvulnerable(true); z.setPersistent(false);
            z.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20); z.setHealth(20);
        });
        entities.add(actor);
        // Real Bukkit health, potions, PDC, world and velocity behind a headless Player interface.
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "isOnline", "hasPermission" -> true;
                case "getGameMode" -> GameMode.SURVIVAL;
                case "isFlying", "isGliding", "isOp" -> false;
                case "getName" -> "FoodProbe" + index;
                case "sendMessage" -> {
                    if (args[0] instanceof Component component) messages.computeIfAbsent(actor.getUniqueId(), k -> new ArrayList<>()).add(plain(component));
                    else if (args[0] instanceof String text) messages.computeIfAbsent(actor.getUniqueId(), k -> new ArrayList<>()).add(text);
                    yield null;
                }
                case "showBossBar" -> { visibleBars.put(actor.getUniqueId(), (BossBar)args[0]); yield null; }
                case "hideBossBar" -> { visibleBars.remove(actor.getUniqueId()); yield null; }
                case "sendActionBar", "playSound", "spawnParticle" -> null;
                case "equals" -> proxy == args[0] || actor == args[0];
                case "hashCode" -> actor.getUniqueId().hashCode();
                case "toString" -> "FoodProbe" + index;
                default -> {
                    try { yield actor.getClass().getMethod(method.getName(), method.getParameterTypes()).invoke(actor, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                }
            };
        });
    }
    private void run() {
        plugin = (JavaPlugin)Bukkit.getPluginManager().getPlugin("FartPlugin");
        check(plugin != null && plugin.isEnabled(), "plugin enabled");
        loader = plugin.getClass().getClassLoader();
        items = field(plugin, "foodItems"); effects = field(plugin, "foodEffects");
        visuals = field(effects, "visuals"); protection = field(effects, "protection");
        world = Bukkit.getWorlds().getFirst(); base = new Location(world, 32.5, 201, 32.5);
        world.addPluginChunkTicket(2, 2, this);
        for (int x = 31; x <= 40; x++) for (int z = 31; z <= 40; z++) {
            for (int y = 200; y <= 211; y++) {
                var block = world.getBlockAt(x, y, z); blocks.add(block.getState());
                block.setType(y == 200 ? Material.STONE : Material.AIR, false);
            }
        }
        plugin.getConfig().set("effects.sounds-enabled", false);
        plugin.getConfig().set("effects.particles-enabled", false);
        plugin.getConfig().set("foods.belyash.accident-chance", 0d);
        plugin.getConfig().set("messages.actionbar-enabled", false);
        recipes();
        Player p = player(0);
        var command = plugin.getCommand("fartmc");
        check(command.getTabCompleter().onTabComplete(p, command, "fartmc", new String[]{"give", "Player", ""})
                .equals(List.of("belyash", "cheburek", "shaurma")), "canonical command completions");
        command.getExecutor().onCommand(p, command, "fartmc", new String[]{"give", "missing"});
        check(messages.get(p.getUniqueId()).getLast().contains("<belyash|cheburek|shaurma>"), "canonical give usage");
        check(call(type("FoodItems"), "type", new ItemStack(Material.BREAD)) == null, "plain bread excluded");
        // Final cancellation, including another MONITOR handler, must prevent the queued start.
        cancelConsumption = true;
        Bukkit.getPluginManager().callEvent(new PlayerItemConsumeEvent(p, item("cheburek"), EquipmentSlot.HAND));
        cancelConsumption = false;
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
            check(!(boolean)call(effects, "active", p), "cancelled consume never starts");
            health(p);
            potions(p);
            stains(p);
            ignition(p);
            collision(p);
            reserve(p);
        }), 2);
    }
    private void recipes() {
        for (String id : List.of("belyash", "cheburek", "shaurma")) {
            ItemStack food = item(id);
            check(food.getAmount() == 1 && food.getType() == Material.BREAD, "food result " + id);
            check(food.getItemMeta().getItemModel().equals(new NamespacedKey("fartmc", id)), "item model " + id);
            check(food.getItemMeta().getFood().canAlwaysEat(), "always edible " + id);
            check(id.equals(food.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("fartmc", "food_id"), PersistentDataType.STRING)), "PDC " + id);
            ShapedRecipe recipe = (ShapedRecipe)Bukkit.getRecipe(new NamespacedKey("fartmc", id));
            ItemStack[] matrix = matrix(recipe);
            ItemCraftResult result = Bukkit.getServer().craftItemResult(matrix, world);
            check(result.getResult().isSimilar(food), "native craft " + id);
            if (id.equals("cheburek")) {
                ItemStack[] remaining = result.getResultingMatrix();
                int buckets = Arrays.stream(remaining).filter(Objects::nonNull).filter(i -> i.getType() == Material.BUCKET).mapToInt(ItemStack::getAmount).sum();
                check(buckets == 2, "exactly two milk bucket remainders");
                check(Bukkit.getServer().craftItemResult(remaining, world).getResult().getType().isAir(), "remainders cannot duplicate food");
            }
            for (int i = 0; i < matrix.length; i++) if (matrix[i] != null && matrix[i].getType() == Material.BREAD) {
                matrix[i] = item("belyash"); break;
            }
            check(Bukkit.getServer().craftItemResult(matrix, world).getResult().getType().isAir(), "custom food rejected as ingredient " + id);
        }
        for (String[] migration : new String[][]{{"belyash_station", "belyash"}, {"khacheburek", "cheburek"}, {"poisoned_cheburek", "shaurma"}}) {
            ItemStack old = item(migration[1]); old.setAmount(9);
            var meta = old.getItemMeta();
            meta.displayName(Component.text("Моя еда"));
            if (migration[1].equals("shaurma")) {
                var lore = new ArrayList<>(meta.lore()); lore.set(1, Component.text("Действие: 15 сек.")); meta.lore(lore);
            }
            NamespacedKey pdc = new NamespacedKey("fartmc", "food_id");
            meta.getPersistentDataContainer().set(pdc, PersistentDataType.STRING, migration[0]);
            meta.setItemModel(new NamespacedKey("fartmc", migration[0])); old.setItemMeta(meta);
            check(call(type("FoodItems"), "type", old).equals(food(migration[1])), "old PDC recognized");
            check((boolean)call(items, "refreshName", old), "old identity migrated");
            check(migration[1].equals(old.getItemMeta().getPersistentDataContainer().get(pdc, PersistentDataType.STRING)), "PDC rewritten");
            check(old.getItemMeta().getItemModel().equals(new NamespacedKey("fartmc", migration[1])), "model key migrated");
            check(old.getAmount() == 9 && plain(old.getItemMeta().displayName()).equals("Моя еда"), "migration preserves stack and anvil name");
            if (migration[1].equals("shaurma")) check(plain(old.getItemMeta().lore().get(1)).equals("Действие: 20 сек."), "old shaurma duration lore updated");
            check(!(boolean)call(items, "refreshName", old), "identity migration idempotent");
            ShapedRecipe stale = new ShapedRecipe(new NamespacedKey("fartmc", migration[0]), old);
            stale.shape("D").setIngredient('D', Material.DIRT);
            check(Bukkit.addRecipe(stale), "seed legacy recipe");
        }
        call(items, "register"); call(items, "register");
        for (String old : List.of("belyash_station", "khacheburek", "poisoned_cheburek"))
            check(Bukkit.getRecipe(new NamespacedKey("fartmc", old)) == null, "legacy recipe removed");
        int count = 0; var iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) if (iterator.next() instanceof Keyed key && key.getKey().getNamespace().equals("fartmc")) count++;
        check(count == 3, "idempotent recipe registration");
        for (String[] rename : new String[][]{{"cheburek", "Хачебурек", "Чебурек загадочный"},
                {"shaurma", "Отравленный чебурек", "Просроченная шаурма"}}) {
            ItemStack food = item(rename[0]);
            check(plain(food.getItemMeta().displayName()).equals(rename[2]), "new food name");
            food.setAmount(7);
            var meta = food.getItemMeta(); meta.displayName(Component.text(rename[1])); food.setItemMeta(meta);
            check((boolean)call(items, "refreshName", food), "legacy item renamed");
            check(food.getAmount() == 7 && plain(food.getItemMeta().displayName()).equals(rename[2]), "rename preserves amount");
            check(food.getItemMeta().getItemModel().equals(new NamespacedKey("fartmc", rename[0])), "rename preserves model");
            meta = food.getItemMeta(); meta.displayName(Component.text("Моя еда")); food.setItemMeta(meta);
            check(!(boolean)call(items, "refreshName", food), "anvil name preserved");
        }
    }
    private ItemStack[] matrix(ShapedRecipe recipe) {
        ItemStack[] matrix = new ItemStack[9];
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++)
            matrix[y * 3 + x] = recipe.getChoiceMap().get(recipe.getShape()[y].charAt(x)).getItemStack().clone();
        return matrix;
    }
    private Object session(Player p, String id) {
        Object food = food(id), settings = call(type("FoodSettings"), "read", plugin.getConfig(), food);
        try {
            Constructor<?> constructor = type("FoodSession").getDeclaredConstructors()[0]; constructor.setAccessible(true);
            return constructor.newInstance(p, food, settings);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    @SuppressWarnings("unchecked") private void put(Player p, Object session) {
        ((Map<UUID, Object>)field(effects, "sessions")).put(p.getUniqueId(), session);
    }
    private void health(Player p) {
        p.setHealth(18);
        Object session = session(p, "cheburek"); put(p, session); call(session, "begin", effects);
        check(p.getHealth() == 10, "health halved");
        p.setHealth(4); call(effects, "stop", p, false);
        check(p.getHealth() == 12, "damage retained on refund");
        call(effects, "stop", p, false); check(p.getHealth() == 12, "stop idempotent");
        p.setHealth(6); session = session(p, "cheburek"); put(p, session); call(session, "begin", effects);
        check(p.getHealth() == 6, "low health never raised at start"); call(effects, "stop", p, false);
        p.setHealth(18); session = session(p, "cheburek"); put(p, session); call(session, "begin", effects);
        call(effects, "stop", p, true); check(p.getHealth() == 10, "death cleanup never refunds");
    }
    private void potions(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 1200, 2));
        Object session = session(p, "belyash"); put(p, session); call(session, "begin", effects);
        call(effects, "stop", p, false);
        check(p.getPotionEffect(PotionEffectType.POISON).getAmplifier() == 2, "strong foreign potion retained");
        p.removePotionEffect(PotionEffectType.POISON);
        session = session(p, "belyash"); put(p, session); call(session, "begin", effects);
        p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 1000, 1));
        call(effects, "stop", p, false);
        check(p.getPotionEffect(PotionEffectType.POISON).getAmplifier() == 1, "foreign replacement retained");
        p.removePotionEffect(PotionEffectType.POISON);
        session = session(p, "belyash"); put(p, session); call(session, "begin", effects);
        for (int i = 0; i < 600; i++) check((boolean)call(session, "update", effects), "belyash tick " + i);
        check(!(boolean)call(session, "update", effects), "belyash ends at 600 ticks");
        call(effects, "stop", p, false);
        check(!p.hasPotionEffect(PotionEffectType.POISON) && !p.hasPotionEffect(PotionEffectType.NAUSEA), "owned potions removed");
    }
    private void stains(Player p) {
        Material before = world.getBlockAt(base.clone().add(0, -1, 0)).getType();
        long tick = (long)field(effects, "tick");
        call(visuals, "stain", p, tick, 200);
        check(((List<?>)field(visuals, "stains")).size() == 3, "three visual stains; actual="
                + ((List<?>)field(visuals, "stains")).size() + "; player=" + p.getLocation()
                + "; ground=" + call(type("FoodMotion"), "ground", p.getLocation().add(0, .5, 0), 3d));
        check(world.getEntitiesByClass(BlockDisplay.class).stream().noneMatch(Entity::isPersistent), "stains nonpersistent");
        check(world.getBlockAt(base.clone().add(0, -1, 0)).getType() == before, "stains preserve world blocks");
        call(visuals, "tick", tick + 200);
        check(world.getEntitiesByClass(BlockDisplay.class).isEmpty(), "stains expire");
    }
    private void ignition(Player p) {
        plugin.getConfig().set("foods.shaurma.ignition-chance", 1d);
        Object settings = call(type("FoodSettings"), "read", plugin.getConfig(), food("shaurma"));
        Location origin = base.clone().add(0, 6, 0);
        cancelIgnition = true;
        call(protection, "ignite", p, settings, origin);
        check(ignitionEvents == 1 && world.getBlockAt(base).getType().isAir(), "cancelled ignition preserves block");
        cancelIgnition = false;
        call(protection, "ignite", p, settings, origin);
        check(world.getBlockAt(base).getType() == Material.FIRE, "permitted ignition creates fire");
        var fireGround = (org.bukkit.util.RayTraceResult)call(type("FoodMotion"), "ground", origin, 7d);
        check((boolean)call(type("FoodMotion"), "safeGround", fireGround), "ground below own fire remains safe; hit="
                + (fireGround == null ? "none" : fireGround.getHitBlock().getType()));
        check(Math.abs(fireGround.getHitPosition().getY() - 201) < .001, "fire does not raise the supporting floor");
        var support = world.getBlockAt(base.clone().add(0, -1, 0));
        for (Material hazard : List.of(Material.WATER, Material.LAVA)) {
            support.setType(hazard, false);
            Object unsafe = call(type("FoodMotion"), "ground", origin, 7d);
            check(!(boolean)call(type("FoodMotion"), "safeGround", unsafe), "ignoring fire still detects " + hazard);
        }
        support.setType(Material.STONE, false);
        world.getBlockAt(base).setType(Material.AIR, false);
        plugin.getConfig().set("foods.shaurma.ignition-enabled", false);
        settings = call(type("FoodSettings"), "read", plugin.getConfig(), food("shaurma"));
        call(protection, "ignite", p, settings, origin);
        check(world.getBlockAt(base).getType().isAir(), "disabled ignition preserves block");
    }
    private void collision(Player p) {
        Location wall = p.getLocation().clone().add(1, 0, 0);
        world.getBlockAt(wall).setType(Material.STONE, false);
        world.getBlockAt(wall.clone().add(0, 1, 0)).setType(Material.STONE, false);
        call(type("FoodMotion"), "velocity", p, p, new Vector(.65, 0, 0));
        check(p.getVelocity().getX() == 0, "swept body rejects wall movement");
        world.getBlockAt(wall).setType(Material.AIR, false);
        world.getBlockAt(wall.clone().add(0, 1, 0)).setType(Material.AIR, false);
    }
    private void reserve(Player p) {
        p.getPersistentDataContainer().remove(new NamespacedKey("fartmc", "food_cooldown"));
        PlayerItemConsumeEvent first = new PlayerItemConsumeEvent(p, item("cheburek"), EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(first);
        PlayerItemConsumeEvent second = new PlayerItemConsumeEvent(p, item("belyash"), EquipmentSlot.OFF_HAND);
        Bukkit.getPluginManager().callEvent(second);
        check(!first.isCancelled() && second.isCancelled(), "pending session excludes double consumption");
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
            check((boolean)call(effects, "active", p), "successful consumption starts session");
            call(effects, "stop", p, false);
            PlayerItemConsumeEvent cooldown = new PlayerItemConsumeEvent(p, item("cheburek"), EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(cooldown);
            check(cooldown.isCancelled(), "cooldown remains after stop");
            cooldownAndMilk(p);
        }), 2);
    }
    private String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }
    private long readyMessages(Player p) {
        return messages.getOrDefault(p.getUniqueId(), List.of()).stream().filter(s -> s.equals("Желудок готов! Можно снова есть.")).count();
    }
    private void cooldownAndMilk(Player p) {
        NamespacedKey key = new NamespacedKey("fartmc", "food_cooldown");
        long end = p.getPersistentDataContainer().get(key, PersistentDataType.LONG);
        call(effects, "showStatus", p, end - 1501);
        check(plain(visibleBars.get(p.getUniqueId()).name()).contains("2 с"), "visible countdown rounds up");
        long before = readyMessages(p);
        call(effects, "showStatus", p, end);
        check(!visibleBars.containsKey(p.getUniqueId()) && !p.getPersistentDataContainer().has(key), "expired cooldown clears bar and PDC");
        call(effects, "showStatus", p, end + 1);
        check(readyMessages(p) == before + 1, "readiness notification once");
        long future = System.currentTimeMillis() + 45000;
        p.getPersistentDataContainer().set(key, PersistentDataType.LONG, future);
        call(effects, "recover", p);
        check(visibleBars.containsKey(p.getUniqueId()), "login recovers countdown");
        cancelConsumption = true;
        Bukkit.getPluginManager().callEvent(new PlayerItemConsumeEvent(p, new ItemStack(Material.MILK_BUCKET), EquipmentSlot.OFF_HAND));
        cancelConsumption = false;
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
            check(p.getPersistentDataContainer().get(key, PersistentDataType.LONG) == future, "cancelled milk preserves cooldown");
            Bukkit.getPluginManager().callEvent(new PlayerItemConsumeEvent(p, new ItemStack(Material.BREAD), EquipmentSlot.HAND));
            check(p.getPersistentDataContainer().has(key), "ordinary food does not reset cooldown");
            Bukkit.getPluginManager().callEvent(new PlayerItemConsumeEvent(p, new ItemStack(Material.MILK_BUCKET), EquipmentSlot.OFF_HAND));
            Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
                check(!p.getPersistentDataContainer().has(key) && !visibleBars.containsKey(p.getUniqueId()), "drunk milk resets cooldown and bar");
                PlayerItemConsumeEvent nextFood = new PlayerItemConsumeEvent(p, item("cheburek"), EquipmentSlot.HAND);
                Bukkit.getPluginManager().callEvent(nextFood);
                check(!nextFood.isCancelled(), "food allowed after milk");
                Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
                    check((boolean)call(effects, "active", p), "next food starts normally");
                    Bukkit.getPluginManager().callEvent(new PlayerItemConsumeEvent(p, new ItemStack(Material.MILK_BUCKET), EquipmentSlot.HAND));
                    Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
                        check(!p.getPersistentDataContainer().has(key) && (boolean)call(effects, "active", p), "milk resets only cooldown during effect");
                        PlayerItemConsumeEvent overlapping = new PlayerItemConsumeEvent(p, item("belyash"), EquipmentSlot.HAND);
                        Bukkit.getPluginManager().callEvent(overlapping);
                        check(overlapping.isCancelled(), "milk never permits overlapping effects");
                        check(plain(visibleBars.get(p.getUniqueId()).name()).contains("эффект еды ещё действует"), "status explains remaining active effect");
                        call(effects, "stop", p, false);
                        call(effects, "showStatus", p, System.currentTimeMillis());
                        check(!visibleBars.containsKey(p.getUniqueId()), "ready immediately after milk and effect end");
                        p.getPersistentDataContainer().set(key, PersistentDataType.LONG, future);
                        plugin.getConfig().set("foods.milk-resets-cooldown", false);
                        Bukkit.getPluginManager().callEvent(new PlayerItemConsumeEvent(p, new ItemStack(Material.MILK_BUCKET), EquipmentSlot.HAND));
                        Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
                            check(p.getPersistentDataContainer().has(key), "disabled milk reset respected");
                            plugin.getConfig().set("foods.milk-resets-cooldown", true);
                            p.getPersistentDataContainer().remove(key);
                            call(effects, "showStatus", p, System.currentTimeMillis());
                            load();
                        }), 2);
                    }), 2);
                }), 2);
            }), 2);
        }), 2);
    }
    private void load() {
        plugin.getConfig().set("foods.belyash.duration-seconds", 2);
        for (int i = 0; i < 20; i++) {
            Player p = player(i + 1); loadPlayers.add(p);
            Object session = session(p, i % 2 == 0 ? "belyash" : "cheburek");
            put(p, session); call(session, "begin", effects);
        }
        check(((Map<?, ?>)field(effects, "sessions")).size() == 20, "20 concurrent sessions");
        movingSeat = world.spawn(base.clone().add(5, 0, 5), ArmorStand.class, s -> {
            s.setVisible(false); s.setSmall(true); s.setGravity(true); s.setCanMove(true); s.setCanTick(true);
            s.setPersistent(false); s.setInvulnerable(true);
        });
        entities.add(movingSeat);
        rider = world.spawn(base.clone().add(5, 1, 5), Pig.class, pig -> { pig.setAI(false); pig.setPersistent(false); });
        entities.add(rider); check(movingSeat.addPassenger(rider), "native passenger mounting");
        startY = movingSeat.getY();
        Bukkit.getScheduler().runTaskTimer(this, () -> { if (movingSeat.isValid()) movingSeat.setVelocity(new Vector(0, .2, 0)); }, 1, 1);
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
            check(movingSeat.getY() > startY + 1, "native armor stand velocity moves mounted entity; start=" + startY
                    + "; end=" + movingSeat.getY() + "; ticks=" + movingSeat.getTicksLived() + "; velocity=" + movingSeat.getVelocity());
            check(movingSeat.getPassengers().contains(rider), "passenger retained during movement");
            call(effects, "stopAll"); call(effects, "stopAll");
            check(((Map<?, ?>)field(effects, "sessions")).isEmpty(), "all sessions cleaned");
            check(((Map<?, ?>)field(effects, "pending")).isEmpty(), "all pending starts cleaned");
            for (Player p : loadPlayers) check(!p.hasPotionEffect(PotionEffectType.POISON), "load potion cleanup");
            movingSeat.remove();
            flight();
        }), 45);
    }
    private void flight() {
        plugin.getConfig().set("foods.shaurma.horizontal-speed", 0d);
        plugin.getConfig().set("foods.shaurma.ignition-enabled", true);
        Player p = player(26);
        Entity actor = entities.stream().filter(e -> e.getUniqueId().equals(p.getUniqueId())).findFirst().orElseThrow();
        ArmorStand seat = (ArmorStand)call(type("FoodMotion"), "seat", p.getLocation(), actor);
        entities.add(seat);
        check(!seat.isVisible() && !seat.isPersistent() && !seat.isMarker(), "production seat properties");
        Object session = session(p, "shaurma");
        check((int)call(field(session, "settings"), "duration") == 400, "shaurma lasts twenty seconds");
        try { Field f = session.getClass().getDeclaredField("seat"); f.setAccessible(true); f.set(session, seat); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
        put(p, session);
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
            double height = p.getY() - 201;
            check((boolean)call(effects, "active", p), "flight remains active above safe surface");
            check(height >= 5 && height <= 7.1, "controller hovers at 5–7 blocks; actual=" + height);
            // Clear the launch fire and require the running session to ignite again during hover.
            Location origin = (Location)call(visuals, "behind", p);
            var target = world.getBlockAt(origin.getBlockX(), 201, origin.getBlockZ());
            check(target.getType() == Material.FIRE, "flight ignites ground");
            target.setType(Material.AIR, false);
            int eventsBeforeHover = ignitionEvents;
            Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
                check((boolean)call(effects, "active", p), "shaurma still flies after sixteen seconds");
                check(p.getY() - 201 >= 5, "hover retained beyond old fifteen-second duration");
                check(ignitionEvents > eventsBeforeHover && target.getType() == Material.FIRE, "running jet ignites ground again during hover");
                target.setType(Material.AIR, false);
                Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
                    check(!(boolean)call(effects, "active", p), "shaurma ends after twenty seconds");
                    check(!seat.isValid() && !actor.isInsideVehicle(), "flight end removes seat and releases passenger");
                    check(p.hasPotionEffect(PotionEffectType.SLOW_FALLING), "safe landing handed to vanilla physics");
                    getLogger().info("FOOD TEST PASS: " + checks + " assertions; migration/commands/recipes/milk, twenty-second flight and hover ignition, 20-session run. Client rendering not tested.");
                    cleanup();
                }), 85);
            }), 200);
        }), 120);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void ignite(BlockIgniteEvent e) {
        ignitionEvents++; if (cancelIgnition) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void cancel(PlayerItemConsumeEvent e) {
        if (cancelConsumption) e.setCancelled(true); // Deliberately hostile ordering for regression coverage.
    }
    private void cleanup() {
        if (effects != null) call(effects, "stopAll");
        entities.forEach(Entity::remove); entities.clear();
        for (BlockState block : blocks) block.update(true, false);
        blocks.clear();
        if (world != null) world.removePluginChunkTickets(this);
        Bukkit.getScheduler().cancelTasks(this);
    }
    @Override public void onDisable() { cleanup(); }
}
