const File = Java.type("java.io.File");
const Files = Java.type("java.nio.file.Files");
const Paths = Java.type("java.nio.file.Paths");
const System = Java.type("java.lang.System");

const BASE_DIR = System.getProperty("user.home") + "/HypixelMoneyGuide";
const DATA_PATH = BASE_DIR + "/guide_data.json";
const TRIGGER_PATH = BASE_DIR + "/update_request.txt";

let waitingForSync = false;
let preSyncTimestamp = "";
let routeEnabled = false;

let waypoints = [
    { name: "1. Left Quartz Cave", x: -460, y: 68, z: -656 },
    { name: "2. Magma Chamber Road", x: -300, y: 67, z: -850 },
    { name: "3. Kada Knight Cavern", x: -430, y: 85, z: -920 }
];

function loadGuideData() {
    const file = new File(DATA_PATH);
    if (!file.exists()) {
        ChatLib.chat("&c[MoneyGuide] guide_data.json not found! Start skyblock_guide.py first.");
        return null;
    }
    const content = new String(Files.readAllBytes(Paths.get(DATA_PATH)), "UTF-8");
    return JSON.parse(content);
}

function formatCoins(num) {
    if (num >= 1000000000) return (num / 1000000000).toFixed(2) + "B";
    if (num >= 1000000) return (num / 1000000).toFixed(2) + "M";
    if (num >= 1000) return (num / 1000).toFixed(1) + "k";
    return Math.round(num).toString();
}

register("renderWorld", () => {
    if (!routeEnabled) return;
    const px = Player.getX();
    const py = Player.getY();
    const pz = Player.getZ();

    waypoints.forEach(wp => {
        const dist = Math.round(Math.hypot(wp.x - px, wp.y - py, wp.z - pz));
        Tessellator.drawString(
            "§6[Cinderbat] §b" + wp.name + " §e(" + dist + "m)",
            wp.x + 0.5,
            wp.y + 1.5,
            wp.z + 0.5,
            0xFFFFFF,
            true,
            0.04,
            false
        );
    });
});

register("step", () => {
    if (!waitingForSync) return;
    try {
        const data = loadGuideData();
        if (data && data.updated_at !== preSyncTimestamp) {
            waitingForSync = false;
            const s = data.stats;
            ChatLib.chat("&a&l[MoneyGuide] &aStats synced! &7(" + s.status + ")");
            ChatLib.chat("&7HF: &d" + s.hunter_fortune + " &8| &7FF: &e" + s.farming_fortune + " &8| &7MF: &b" + s.mining_fortune + " &8| &7Pristine: &5" + s.pristine);
            ChatLib.chat("&7Run &e/mmg methods &7to view updated coins/hour.");
        }
    } catch (e) {}
}).setDelay(1);

register("command", (...args) => {
    const data = loadGuideData();
    const sub = args && args[0] ? args[0].toLowerCase() : "help";

    if (sub === "route") {
        routeEnabled = !routeEnabled;
        ChatLib.chat("&6[MoneyGuide] &fCinderbat 3D Waypoints: " + (routeEnabled ? "&aENABLED" : "&cDISABLED"));
        if (routeEnabled) {
            ChatLib.chat("&7Tip: Stand at a bat spawn & run &e/mmg addwp <name> &7to add custom points.");
        }
        return;
    }

    if (sub === "addwp") {
        const wpName = args.slice(1).join(" ") || ("Cave #" + (waypoints.length + 1));
        const x = Math.floor(Player.getX());
        const y = Math.floor(Player.getY());
        const z = Math.floor(Player.getZ());
        waypoints.push({ name: wpName, x: x, y: y, z: z });
        routeEnabled = true;
        ChatLib.chat("&a[MoneyGuide] Added waypoint &b" + wpName + " &7at (" + x + ", " + y + ", " + z + ")");
        return;
    }

    if (sub === "clearwp") {
        waypoints = [];
        ChatLib.chat("&e[MoneyGuide] Cleared all route waypoints.");
        return;
    }

    if (sub === "updatestats" || sub === "sync") {
        try {
            preSyncTimestamp = data ? data.updated_at : "";
            waitingForSync = true;
            Files.write(Paths.get(TRIGGER_PATH), new java.lang.String("sync").getBytes("UTF-8"));
            ChatLib.chat("&e[MoneyGuide] Syncing profile stats from Hypixel API...");
        } catch (e) {
            ChatLib.chat("&c[MoneyGuide] Failed to write trigger file: " + e);
        }
        return;
    }

    if (!data) return;

    if (sub === "lb" || sub === "lowball") {
        const query = args.slice(1).join(" ").toLowerCase();
        if (!query) {
            ChatLib.chat("&cUsage: /mmg lb <item name> (e.g. /mmg lb shadow fury)");
            return;
        }
        const bins = data.all_lowest_bins || {};
        const matches = Object.keys(bins).filter(k => k.toLowerCase().includes(query));
        if (matches.length === 0) {
            ChatLib.chat("&c[Lowball] No active BIN found matching '&e" + query + "&c'.");
            return;
        }
        matches.sort((a, b) => bins[a] - bins[b]);
        ChatLib.chat("&6&m----------------------------------------");
        ChatLib.chat("&e&lLowball Calculator &7(Matches for '" + query + "')");
        matches.slice(0, 4).forEach(name => {
            const lbin = bins[name];
            const offer85 = lbin * 0.85;
            const offer80 = lbin * 0.80;
            ChatLib.chat("&b" + name + " &7(LBIN: &f" + formatCoins(lbin) + "&7)");
            ChatLib.chat("  &a85% Offer: &6" + formatCoins(offer85) + " &7(+" + formatCoins(lbin - offer85) + ") &8| &a80% Offer: &6" + formatCoins(offer80) + " &7(+" + formatCoins(lbin - offer80) + ")");
        });
        ChatLib.chat("&6&m----------------------------------------");
        return;
    }

    if (sub === "methods" || sub === "top") {
        ChatLib.chat("&6&m----------------------------------------");
        ChatLib.chat("&e&lPersonalized Money Methods &7(" + data.updated_at + ")");
        data.methods.forEach((m, i) => {
            ChatLib.chat("&a#" + (i + 1) + " &b" + m.name + " &f- &6" + formatCoins(m.coins_per_hr) + "/hr");
        });
        ChatLib.chat("&7Use &e/mmg info <#> &7for breakdown | &e/mmg route &7for Cinderbat waypoints.");
        ChatLib.chat("&6&m----------------------------------------");
    } 
    else if (sub === "info") {
        const idx = parseInt(args[1]) - 1;
        if (isNaN(idx) || !data.methods[idx]) {
            ChatLib.chat("&cUsage: /mmg info <1-" + data.methods.length + ">");
            return;
        }
        const m = data.methods[idx];
        ChatLib.chat("&6&m----------------------------------------");
        ChatLib.chat("&b&l" + m.name + " &6(" + formatCoins(m.coins_per_hr) + "/hr)");
        ChatLib.chat("&7Req: &f" + m.requirements);
        m.breakdown.forEach(line => ChatLib.chat(" &8- &e" + line));
        ChatLib.chat("&6&m----------------------------------------");
    } 
    else if (sub === "flips") {
        ChatLib.chat("&6&m----------------------------------------");
        ChatLib.chat("&e&lTop Bazaar Craft Flips &7(" + data.updated_at + ")");
        data.top_flips.slice(0, 6).forEach(f => {
            ChatLib.chat("&e[" + f.type + "] &b" + f.name + "&f: &a+" + formatCoins(f.profit) + " &7(" + f.roi + "% ROI)");
        });
        ChatLib.chat("&6&m----------------------------------------");
    } 
    else if (sub === "bz") {
        ChatLib.chat("&6&m----------------------------------------");
        ChatLib.chat("&e&lTracked Bazaar Prices");
        Object.keys(data.watched_bz).forEach(item => {
            const p = data.watched_bz[item];
            ChatLib.chat("&b" + item + "&f: Buy Order &a" + formatCoins(p.buy_order) + " &7| Sell Offer &6" + formatCoins(p.sell_offer));
        });
        ChatLib.chat("&6&m----------------------------------------");
    } 
    else {
        ChatLib.chat("&6&l[MoneyGuide Commands]");
        ChatLib.chat("&e/mmg route &7- Toggle 3D Cinderbat cave waypoints");
        ChatLib.chat("&e/mmg addwp <name> &7- Add custom waypoint at your feet");
        ChatLib.chat("&e/mmg lb <item> &7- Instant 80%/85% lowball offer calculator");
        ChatLib.chat("&e/mmg updatestats &7- Sync live profile stats & hourly rates");
        ChatLib.chat("&e/mmg methods &7- View ranked coins/hour methods");
        ChatLib.chat("&e/mmg flips &7- View live Bazaar craft flips");
        ChatLib.chat("&e/mmg bz &7- View Cinderbat Shard & Scroll prices");
    }
}).setName("mmg");
