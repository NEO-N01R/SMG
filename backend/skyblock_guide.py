import json
import os
import time
import requests

# --- USER CONFIGURATION (Loaded safely from Environment Variables) ---
HYPIXEL_API_KEY = os.getenv("HYPIXEL_API_KEY", "")
MINECRAFT_USERNAME = os.getenv("MINECRAFT_USERNAME", "YOUR_IGN_HERE")
PROFILE_NAME = os.getenv("SKYBLOCK_PROFILE", "")
# ---------------------------------------------------------------------

BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar"
ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items"
AUCTIONS_URL = "https://api.hypixel.net/v2/skyblock/auctions"
PROFILES_URL = "https://api.hypixel.net/v2/skyblock/profiles"
MOJANG_UUID_URL = f"https://api.mojang.com/users/profiles/minecraft/{MINECRAFT_USERNAME}"


DEFAULT_DATA_DIR = os.path.join(os.path.expanduser("~"), "HypixelMoneyGuide")
OUTPUT_DIR = os.getenv("MMG_DATA_DIR", DEFAULT_DATA_DIR)
DATA_FILE = os.path.join(OUTPUT_DIR, "guide_data.json")
TRIGGER_FILE = os.path.join(OUTPUT_DIR, "update_request.txt")
STATS_CACHE_FILE = os.path.join(OUTPUT_DIR, "player_stats.json")
TAX_RATE = 0.01125  # 1.125% Bazaar tax

CRAFT_RECIPES = {
    "GOLDEN_TOOTH": {"WOLF_TOOTH": 128, "ENCHANTED_GOLD": 32},
    "TARANTULA_SILK": {"TARANTULA_WEB": 128, "ENCHANTED_FLINT": 32},
    "REVENANT_VISCERA": {"REVENANT_FLESH": 128, "ENCHANTED_STRING": 32},
    "NULL_OVOID": {"NULL_SPHERE": 128, "ENCHANTED_OBSIDIAN": 32},
    "REFINED_MITHRIL": {"ENCHANTED_MITHRIL": 160},
    "ENCHANTED_GOLDEN_CARROT": {"ENCHANTED_CARROT": 128, "GOLDEN_CARROT": 32}
}


def get_uuid():
    if not MINECRAFT_USERNAME or MINECRAFT_USERNAME == "YOUR_IGN_HERE":
        return ""
    try:
        res = requests.get(MOJANG_UUID_URL, timeout=10).json()
        return res.get("id", "")
    except Exception:
        return ""


def fetch_player_stats(uuid):
    default_stats = {
        "farming_fortune": 400.0,
        "mining_fortune": 800.0,
        "pristine": 6.0,
        "magic_find": 100.0,
        "hunter_fortune": 45.0,
        "status": "Default (Set HYPIXEL_API_KEY & MINECRAFT_USERNAME to auto-sync)"
    }

    if not HYPIXEL_API_KEY or not uuid:
        return default_stats

    try:
        headers = {"API-Key": HYPIXEL_API_KEY}
        res = requests.get(f"{PROFILES_URL}?uuid={uuid}", headers=headers, timeout=15).json()
        if not res.get("success"):
            default_stats["status"] = f"API Error: {res.get('cause', 'Unknown')}"
            return default_stats

        profiles = res.get("profiles", [])
        target_profile = None
        for p in profiles:
            if PROFILE_NAME and p.get("cute_name", "").lower() == PROFILE_NAME.lower():
                target_profile = p
                break
            if p.get("selected"):
                target_profile = p

        if not target_profile:
            return default_stats

        member = target_profile.get("members", {}).get(uuid, {})
        player_stats = member.get("player_stats", {})
        experience = member.get("player_data", {}).get("experience", {})

        ff_stat = player_stats.get("farming_fortune", 0)
        mf_stat = player_stats.get("mining_fortune", 0)
        pristine_stat = player_stats.get("pristine", 0)
        magic_find = player_stats.get("magic_find", 100)
        hunter_fortune = player_stats.get("hunter_fortune", 0)

        if ff_stat == 0:
            farming_xp = experience.get("SKILL_FARMING", 0)
            farming_lvl = min(60, int((farming_xp / 111672425) * 60)) if farming_xp > 0 else 26
            ff_stat = 100 + (farming_lvl * 4) + 250

        if mf_stat == 0:
            mining_xp = experience.get("SKILL_MINING", 0)
            mining_lvl = min(60, int((mining_xp / 111672425) * 60)) if mining_xp > 0 else 29
            mf_stat = 100 + (mining_lvl * 4) + 400

        if pristine_stat == 0:
            pristine_stat = 6.0

        if hunter_fortune == 0:
            hunting_xp = experience.get("SKILL_HUNTING", 0)
            hunting_lvl = min(25, int((hunting_xp / 5000000) * 25)) if hunting_xp > 0 else 16
            hunter_fortune = hunting_lvl + 30

        synced = {
            "profile": target_profile.get("cute_name", "Selected"),
            "farming_fortune": round(ff_stat, 1),
            "mining_fortune": round(mf_stat, 1),
            "pristine": round(pristine_stat, 2),
            "magic_find": round(magic_find, 1),
            "hunter_fortune": round(hunter_fortune, 1),
            "status": f"Synced ({target_profile.get('cute_name', 'Main')})"
        }
        with open(STATS_CACHE_FILE, "w", encoding="utf-8") as f:
            json.dump(synced, f, indent=2)
        return synced

    except Exception as e:
        default_stats["status"] = f"Sync Failed: {e}"
        return default_stats


def build_dynamic_methods(stats):
    ff_mult = (100 + stats["farming_fortune"]) / 100.0
    mf_mult = (100 + stats["mining_fortune"]) / 100.0
    hf_mult = (100 + stats["hunter_fortune"]) / 100.0
    pristine_mult = 1 + (stats["pristine"] * 0.79)
    magic_find_mult = 1 + (stats["magic_find"] / 100.0)

    cinderbats_caught_hr = 420
    melon_blocks_hr = 68000
    gem_blocks_hr = 4200
    red_sand_blocks_hr = 65000
    bruiser_kills_hr = 1100

    cinderbat_shards_hr = cinderbats_caught_hr * hf_mult
    ench_melon_blocks_hr = (melon_blocks_hr * 5 * ff_mult) / 25600.0
    flawless_ruby_hr = (gem_blocks_hr * 3.5 * mf_mult * pristine_mult) / 512000.0
    ench_red_sand_hr = (red_sand_blocks_hr * 1.0 * mf_mult) / 160.0
    summoning_eyes_hr = (bruiser_kills_hr / 210.0) * (1 + (magic_find_mult - 1) * 0.25)

    return [
        {
            "id": "cinderbat_hunting",
            "name": f"Crimson Isle Cinderbat Hunting ({stats['hunter_fortune']} HF)",
            "category": "Hunting",
            "requirements": "Everstretch Lasso + David's Cloak (/mmg route)",
            "rates_per_hr": {"SHARD_CINDER_BAT": round(cinderbat_shards_hr, 1)}
        },
        {
            "id": "gemstone_mining",
            "name": f"Crystal Hollows Ruby Mining ({stats['mining_fortune']} MF | {stats['pristine']} Pris)",
            "category": "Mining",
            "requirements": "HotM 7 + Crystal Hollows Route",
            "rates_per_hr": {"FLAWLESS_RUBY_GEMSTONE": round(flawless_ruby_hr, 2)}
        },
        {
            "id": "red_sand_mining",
            "name": f"Crimson Isle Red Sand ({stats['mining_fortune']} MF)",
            "category": "Mining",
            "requirements": "HotM 7 + Promising Shovel",
            "rates_per_hr": {"ENCHANTED_RED_SAND": round(ench_red_sand_hr, 1)}
        },
        {
            "id": "garden_melons",
            "name": f"Garden Melon Farming ({stats['farming_fortune']} FF)",
            "category": "Farming",
            "requirements": "The Garden + Melon Dicer",
            "rates_per_hr": {"ENCHANTED_MELON_BLOCK": round(ench_melon_blocks_hr, 1)}
        },
        {
            "id": "zealot_bruisers",
            "name": f"Zealot Bruiser Farming ({stats['magic_find']} MF)",
            "category": "Combat",
            "requirements": "Bruiser Hideout + Epic Enderman Pet",
            "rates_per_hr": {
                "SUMMONING_EYE": round(summoning_eyes_hr, 2),
                "ENCHANTED_ENDER_PEARL": 180
            }
        },
        {
            "id": "rift_berries",
            "name": "Rift Dreadfarm Wilted Berries",
            "category": "The Rift",
            "requirements": "Rift Dreadfarm + Farming Wand",
            "rates_per_hr": {"WILTED_BERBERIS_BUNCH": 185}
        }
    ]


def fetch_npc_prices():
    try:
        res = requests.get(ITEMS_URL, timeout=15).json()
        return {i["id"]: i["npc_sell_price"] for i in res.get("items", []) if "npc_sell_price" in i}
    except Exception:
        return {}


def fetch_ah_lowest_bins(max_pages=12):
    lowest_bins = {}
    try:
        first = requests.get(f"{AUCTIONS_URL}?page=0", timeout=15).json()
        total = min(first.get("totalPages", 1), max_pages)
        for p in range(total):
            data = first if p == 0 else requests.get(f"{AUCTIONS_URL}?page={p}", timeout=15).json()
            for auc in data.get("auctions", []):
                if auc.get("bin"):
                    name = auc.get("item_name", "").strip()
                    price = auc.get("starting_bid", 0)
                    if price > 0 and (name not in lowest_bins or price < lowest_bins[name]):
                        lowest_bins[name] = price
    except Exception:
        pass
    return lowest_bins


def update_all(npc_prices, current_stats):
    bz_res = requests.get(BAZAAR_URL, timeout=15).json()
    products = bz_res.get("products", {})
    ah_bins = fetch_ah_lowest_bins(max_pages=12)
    dynamic_methods = build_dynamic_methods(current_stats)

    method_results = []
    for info in dynamic_methods:
        hourly_coins = 0
        breakdown = []
        for item_id, qty_per_hr in info["rates_per_hr"].items():
            bz_sell = products.get(item_id, {}).get("quick_status", {}).get("buyPrice", 0) * (1 - TAX_RATE)
            npc_sell = npc_prices.get(item_id, 0)
            best_price = max(bz_sell, npc_sell)
            source = "NPC" if npc_sell > bz_sell else "BZ"

            earned = best_price * qty_per_hr
            hourly_coins += earned
            breakdown.append(f"{item_id}: {qty_per_hr}/hr @ {best_price:,.0f} ({source})")

        method_results.append({
            "id": info["id"],
            "name": info["name"],
            "category": info["category"],
            "requirements": info["requirements"],
            "coins_per_hr": round(hourly_coins),
            "breakdown": breakdown
        })

    method_results.sort(key=lambda x: x["coins_per_hr"], reverse=True)

    flips = []
    for out_id, ingredients in CRAFT_RECIPES.items():
        if out_id not in products:
            continue
        out_q = products[out_id].get("quick_status", {})
        sell_price = out_q.get("buyPrice", 0) * (1 - TAX_RATE)
        vol = out_q.get("buyMovingWeek", 0)
        cost = sum(products.get(i, {}).get("quick_status", {}).get("sellPrice", 0) * q for i, q in ingredients.items())
        
        if cost > 0 and sell_price > cost and vol >= 500:
            profit = sell_price - cost
            flips.append({
                "name": out_id.replace("_", " ").title(),
                "type": "MAT",
                "profit": round(profit),
                "roi": round((profit / cost) * 100, 1)
            })

    enchants = {}
    for item_id, data in products.items():
        if item_id.startswith("ENCHANTMENT_"):
            parts = item_id.rsplit("_", 1)
            if len(parts) == 2 and parts[1].isdigit():
                enchants.setdefault(parts[0], {})[int(parts[1])] = data.get("quick_status", {})

    for base, tiers in enchants.items():
        if 1 in tiers and 5 in tiers:
            cost = tiers[1].get("sellPrice", 0) * 16
            sell = tiers[5].get("buyPrice", 0) * (1 - TAX_RATE)
            vol = tiers[5].get("buyMovingWeek", 0)
            if cost > 0 and sell > cost and vol >= 500:
                profit = sell - cost
                flips.append({
                    "name": base.replace("ENCHANTMENT_", "").replace("_", " ").title() + " V",
                    "type": "BOOK",
                    "profit": round(profit),
                    "roi": round((profit / cost) * 100, 1)
                })

    flips.sort(key=lambda x: x["profit"], reverse=True)

    watched_bz = {}
    for target in [
        "SHARD_CINDER_BAT",
        "SUMMONING_EYE",
        "RECOMBOBULATOR_3000",
        "IMPLOSION_SCROLL",
        "WITHER_SHIELD_SCROLL",
        "SHADOW_WARP_SCROLL"
    ]:
        if target in products:
            q = products[target]["quick_status"]
            watched_bz[target] = {
                "buy_order": round(q.get("sellPrice", 0)),
                "sell_offer": round(q.get("buyPrice", 0))
            }

    payload = {
        "updated_at": time.strftime("%H:%M:%S"),
        "stats": current_stats,
        "methods": method_results,
        "top_flips": flips[:10],
        "watched_bz": watched_bz,
        "all_lowest_bins": ah_bins
    }

    with open(DATA_FILE, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    npc_prices = fetch_npc_prices()
    uuid = get_uuid()
    current_stats = fetch_player_stats(uuid)

    last_full_scan = 0
    while True:
        try:
            force_sync = False
            if os.path.exists(TRIGGER_FILE):
                os.remove(TRIGGER_FILE)
                force_sync = True
                print("[Trigger] /mmg updatestats detected! Syncing profile...")
                current_stats = fetch_player_stats(uuid)

            if force_sync or (time.time() - last_full_scan >= 30):
                update_all(npc_prices, current_stats)
                last_full_scan = time.time()
                print(f"[{time.strftime('%H:%M:%S')}] Updated guide_data.json ({current_stats['status']})")

            time.sleep(1)
        except Exception as e:
            print(f"[Error] {e}")
            time.sleep(5)


if __name__ == "__main__":
    main()
