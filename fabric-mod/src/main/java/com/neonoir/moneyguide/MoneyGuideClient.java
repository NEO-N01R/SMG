package com.neonoir.moneyguide;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.phys.AABB;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MoneyGuideClient implements ClientModInitializer {
    private static final Path BASE_DIR = Paths.get(System.getProperty("user.home"), "HypixelMoneyGuide");
    private static final Path DATA_PATH = BASE_DIR.resolve("guide_data.json");
    private static final Path TRIGGER_PATH = BASE_DIR.resolve("update_request.txt");
    private static final Gson GSON = new Gson();

    private static boolean waitingForSync = false;
    private static String preSyncTimestamp = "";
    private static int syncPollTicks = 0;

    private static boolean highlightBats = true;
    private static final Set<UUID> ANNOUNCED_BATS = new HashSet<>();

    private static boolean routeEnabled = false;
    private static final List<Waypoint> WAYPOINTS = new ArrayList<>(List.of(
        new Waypoint("1. Left Quartz Cave", -460, 68, -656),
        new Waypoint("2. Magma Chamber Road", -300, 67, -850),
        new Waypoint("3. Kada Knight Cavern", -430, 85, -920)
    ));

    private record Waypoint(String name, int x, int y, int z) {}

    @Override
    public void onInitializeClient() {
        registerCommands();
        registerTickListener();
    }

    private void registerTickListener() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;

            // 1. Highlight nearby Cinderbats (Vanilla Bat entities within 64 blocks)
            if (highlightBats) {
                AABB searchBox = client.player.getBoundingBox().inflate(64.0);
                List<Bat> bats = client.level.getEntitiesOfClass(Bat.class, searchBox, bat -> bat.isAlive());
                for (Bat bat : bats) {
                    bat.setGlowingTag(true);
                    if (ANNOUNCED_BATS.add(bat.getUUID())) {
                        int dist = (int) Math.round(client.player.distanceTo(bat));
                        sendMsg("§6[Cinderbat Spotted] §e" + dist + "m away §7at ("
                            + bat.getBlockX() + ", " + bat.getBlockY() + ", " + bat.getBlockZ() + ")");
                    }
                }
            }

            // 2. Poll for /mmg updatestats completion
            if (!waitingForSync) return;
            syncPollTicks++;
            if (syncPollTicks < 20) return;
            syncPollTicks = 0;

            JsonObject data = loadGuideData(false);
            if (data != null && data.has("updated_at")) {
                String updatedAt = data.get("updated_at").getAsString();
                if (!updatedAt.equals(preSyncTimestamp)) {
                    waitingForSync = false;
                    JsonObject s = data.getAsJsonObject("stats");
                    sendMsg("§a§l[MoneyGuide] §aStats synced! §7(" + s.get("status").getAsString() + ")");
                    sendMsg("§7HF: §d" + s.get("hunter_fortune").getAsDouble()
                        + " §8| §7FF: §e" + s.get("farming_fortune").getAsDouble()
                        + " §8| §7MF: §b" + s.get("mining_fortune").getAsDouble()
                        + " §8| §7Pristine: §5" + s.get("pristine").getAsDouble());
                }
            }
        });
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("mmg")
                .executes(ctx -> {
                    sendMsg("§6§l[MoneyGuide 26.2 Mod Commands]");
                    sendMsg("§e/mmg highlight §7- Toggle glowing Cinderbat outlines & radar");
                    sendMsg("§e/mmg methods §7- Rank tasks by personalized live coins/hr");
                    sendMsg("§e/mmg info <#> §7- View item drop rates & gear requirements");
                    sendMsg("§e/mmg updatestats §7- Sync live profile stats from Hypixel API");
                    sendMsg("§e/mmg flips §7- View top Bazaar craft flips");
                    sendMsg("§e/mmg lb <item> §7- Instant 80%/85% lowball offer calculator");
                    sendMsg("§e/mmg route §7- Toggle Cinderbat cave coordinates & distances");
                    sendMsg("§e/mmg addwp <name> §7- Add your current coordinates to route");
                    sendMsg("§e/mmg bz §7- View Cinderbat Shard & Scroll prices");
                    return 1;
                })
                .then(ClientCommands.literal("highlight").executes(ctx -> {
                    highlightBats = !highlightBats;
                    ANNOUNCED_BATS.clear();
                    sendMsg("§6[MoneyGuide] §fCinderbat Highlight & Radar: " + (highlightBats ? "§aENABLED" : "§cDISABLED"));
                    return 1;
                }))
                .then(ClientCommands.literal("methods").executes(ctx -> {
                    JsonObject data = loadGuideData(true);
                    if (data == null) return 0;
                    sendMsg("§6§m----------------------------------------");
                    sendMsg("§e§lPersonalized Money Methods §7(" + data.get("updated_at").getAsString() + ")");
                    JsonArray methods = data.getAsJsonArray("methods");
                    for (int i = 0; i < methods.size(); i++) {
                        JsonObject m = methods.get(i).getAsJsonObject();
                        sendMsg("§a#" + (i + 1) + " §b" + m.get("name").getAsString()
                            + " §f- §6" + formatCoins(m.get("coins_per_hr").getAsDouble()) + "/hr");
                    }
                    sendMsg("§7Tip: Use §e/mmg info <#> §7for drop breakdown.");
                    sendMsg("§6§m----------------------------------------");
                    return 1;
                }))
                .then(ClientCommands.literal("info")
                    .then(ClientCommands.argument("index", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            JsonObject data = loadGuideData(true);
                            if (data == null) return 0;
                            int idx = IntegerArgumentType.getInteger(ctx, "index") - 1;
                            JsonArray methods = data.getAsJsonArray("methods");
                            if (idx < 0 || idx >= methods.size()) {
                                sendMsg("§cUsage: /mmg info <1-" + methods.size() + ">");
                                return 0;
                            }
                            JsonObject m = methods.get(idx).getAsJsonObject();
                            sendMsg("§6§m----------------------------------------");
                            sendMsg("§b§l" + m.get("name").getAsString() + " §6(" + formatCoins(m.get("coins_per_hr").getAsDouble()) + "/hr)");
                            sendMsg("§7Req: §f" + m.get("requirements").getAsString());
                            for (JsonElement el : m.getAsJsonArray("breakdown")) {
                                sendMsg(" §8- §e" + el.getAsString());
                            }
                            sendMsg("§6§m----------------------------------------");
                            return 1;
                        })))
                .then(ClientCommands.literal("updatestats").executes(ctx -> {
                    JsonObject data = loadGuideData(false);
                    preSyncTimestamp = (data != null && data.has("updated_at")) ? data.get("updated_at").getAsString() : "";
                    try {
                        Files.createDirectories(BASE_DIR);
                        Files.writeString(TRIGGER_PATH, "sync", StandardCharsets.UTF_8);
                        waitingForSync = true;
                        syncPollTicks = 0;
                        sendMsg("§e[MoneyGuide] Syncing profile stats from Hypixel API...");
                    } catch (Exception e) {
                        sendMsg("§c[MoneyGuide] Failed to write trigger file: " + e.getMessage());
                    }
                    return 1;
                }))
                .then(ClientCommands.literal("flips").executes(ctx -> {
                    JsonObject data = loadGuideData(true);
                    if (data == null) return 0;
                    sendMsg("§6§m----------------------------------------");
                    sendMsg("§e§lTop Bazaar Craft Flips §7(" + data.get("updated_at").getAsString() + ")");
                    JsonArray flips = data.getAsJsonArray("top_flips");
                    int limit = Math.min(6, flips.size());
                    for (int i = 0; i < limit; i++) {
                        JsonObject f = flips.get(i).getAsJsonObject();
                        sendMsg("§e[" + f.get("type").getAsString() + "] §b" + f.get("name").getAsString()
                            + "§f: §a+" + formatCoins(f.get("profit").getAsDouble())
                            + " §7(" + f.get("roi").getAsDouble() + "% ROI)");
                    }
                    sendMsg("§6§m----------------------------------------");
                    return 1;
                }))
                .then(ClientCommands.literal("lb")
                    .then(ClientCommands.argument("item", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            JsonObject data = loadGuideData(true);
                            if (data == null) return 0;
                            String query = StringArgumentType.getString(ctx, "item").toLowerCase();
                            JsonObject bins = data.getAsJsonObject("all_lowest_bins");
                            List<Map.Entry<String, JsonElement>> matches = new ArrayList<>();
                            for (Map.Entry<String, JsonElement> entry : bins.entrySet()) {
                                if (entry.getKey().toLowerCase().contains(query)) {
                                    matches.add(entry);
                                }
                            }
                            if (matches.isEmpty()) {
                                sendMsg("§c[Lowball] No active BIN found matching '§e" + query + "§c'.");
                                return 0;
                            }
                            matches.sort(Comparator.comparingDouble(e -> e.getValue().getAsDouble()));
                            sendMsg("§6§m----------------------------------------");
                            sendMsg("§e§lLowball Calculator §7(Matches for '" + query + "')");
                            for (int i = 0; i < Math.min(4, matches.size()); i++) {
                                String name = matches.get(i).getKey();
                                double lbin = matches.get(i).getValue().getAsDouble();
                                double offer85 = lbin * 0.85;
                                double offer80 = lbin * 0.80;
                                sendMsg("§b" + name + " §7(LBIN: §f" + formatCoins(lbin) + "§7)");
                                sendMsg("  §a85% Offer: §6" + formatCoins(offer85) + " §7(+" + formatCoins(lbin - offer85) + ")"
                                    + " §8| §a80% Offer: §6" + formatCoins(offer80) + " §7(+" + formatCoins(lbin - offer80) + ")");
                            }
                            sendMsg("§6§m----------------------------------------");
                            return 1;
                        })))
                .then(ClientCommands.literal("bz").executes(ctx -> {
                    JsonObject data = loadGuideData(true);
                    if (data == null) return 0;
                    sendMsg("§6§m----------------------------------------");
                    sendMsg("§e§lTracked Bazaar Prices");
                    JsonObject watched = data.getAsJsonObject("watched_bz");
                    for (Map.Entry<String, JsonElement> entry : watched.entrySet()) {
                        JsonObject p = entry.getValue().getAsJsonObject();
                        sendMsg("§b" + entry.getKey() + "§f: Buy Order §a" + formatCoins(p.get("buy_order").getAsDouble())
                            + " §7| Sell Offer §6" + formatCoins(p.get("sell_offer").getAsDouble()));
                    }
                    sendMsg("§6§m----------------------------------------");
                    return 1;
                }))
                .then(ClientCommands.literal("route").executes(ctx -> {
                    routeEnabled = !routeEnabled;
                    sendMsg("§6[MoneyGuide] §fCinderbat Cave Route: " + (routeEnabled ? "§aENABLED" : "§cDISABLED"));
                    Minecraft mc = Minecraft.getInstance();
                    if (routeEnabled && mc.player != null) {
                        BlockPos pos = mc.player.blockPosition();
                        for (Waypoint wp : WAYPOINTS) {
                            double dx = wp.x - pos.getX();
                            double dy = wp.y - pos.getY();
                            double dz = wp.z - pos.getZ();
                            int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
                            sendMsg("  §e" + wp.name + " §7-> (" + wp.x + ", " + wp.y + ", " + wp.z + ") §b[" + dist + "m]");
                        }
                    }
                    return 1;
                }))
                .then(ClientCommands.literal("addwp")
                    .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null) return 0;
                            String name = StringArgumentType.getString(ctx, "name");
                            BlockPos pos = mc.player.blockPosition();
                            WAYPOINTS.add(new Waypoint(name, pos.getX(), pos.getY(), pos.getZ()));
                            routeEnabled = true;
                            sendMsg("§a[MoneyGuide] Added waypoint §b" + name + " §7at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
                            return 1;
                        })))
            );
        });
    }

    private static JsonObject loadGuideData(boolean warnIfMissing) {
        if (!Files.exists(DATA_PATH)) {
            if (warnIfMissing) {
                sendMsg("§c[MoneyGuide] guide_data.json not found! Run skyblock_guide.py first.");
            }
            return null;
        }
        try {
            String raw = Files.readString(DATA_PATH, StandardCharsets.UTF_8);
            return GSON.fromJson(raw, JsonObject.class);
        } catch (Exception e) {
            if (warnIfMissing) {
                sendMsg("§c[MoneyGuide] Failed to parse guide_data.json: " + e.getMessage());
            }
            return null;
        }
    }

    private static void sendMsg(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        }
    }

    private static String formatCoins(double num) {
        if (num >= 1_000_000_000) return String.format("%.2fB", num / 1_000_000_000.0);
        if (num >= 1_000_000) return String.format("%.2fM", num / 1_000_000.0);
        if (num >= 1_000) return String.format("%.1fk", num / 1_000.0);
        return String.valueOf(Math.round(num));
    }
}

