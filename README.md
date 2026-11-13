# Hypixel SkyBlock Money-Making Guide & Market Tracker

A hybrid **Python + ChatTriggers (JavaScript)** tool for Hypixel SkyBlock that calculates personalized coins-per-hour rates using live profile stats, detects tax-adjusted Bazaar craft flips, tracks Auction House Lowest BINs for lowballing, and renders 3D farming waypoints in-game.

## Architecture & Safety
To comply strictly with Hypixel's server rules, this project uses a **read-only file-based IPC bridge** (`guide_data.json` and `update_request.txt`):
1. **Python Backend (`backend/skyblock_guide.py`):** Polls the public Hypixel REST API every 30 seconds for Bazaar prices, NPC sell values, paginated Auction House BIN listings, and player profile fortunes.
2. **ChatTriggers Frontend (`chattriggers/MoneyGuide`):** Reads the local JSON cache client-side and renders chat tables and 3D world labels without sending automated commands or modifying network packets.

## Features
* **Personalized Hourly Coin Calculator (`/mmg methods`):** Dynamically scales item yields using your synced **Hunter Fortune**, **Mining Fortune**, **Pristine**, **Farming Fortune**, and **Magic Find**.
* **On-Demand Profile Sync (`/mmg updatestats`):** Triggers an immediate profile refresh from the Hypixel Profiles API without restarting the script.
* **Bazaar Craft-Flip Scanner (`/mmg flips`):** Scans Slayer material recipes and Tier 1 → Tier 5 Enchantment Book fusions, factoring in Bazaar taxes and 7-day trading volume.
* **Lowballing Offer Calculator (`/mmg lb <item>`):** Searches live Auction House Lowest BINs and calculates 80% and 85% offer margins in real time.
* **3D Waypoint Renderer (`/mmg route`, `/mmg addwp`):** Renders floating distance beacons in 3D space for Crimson Isle Cinderbat hunting loops.

## Setup & Installation

### 1. Python Backend
1. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
2. Set your environment variables (never hardcode API keys into source files):
   ```bash
   export HYPIXEL_API_KEY="your-api-key"
   export MINECRAFT_USERNAME="your-ign"
   ```
3. Start the background tracker:
   ```bash
   python3 backend/skyblock_guide.py
   ```

### 2. ChatTriggers Module (Minecraft 1.8.9)
1. Copy `chattriggers/MoneyGuide` into your Minecraft `config/ChatTriggers/modules/` directory (accessible via `/ct files` in-game).
2. Run `/ct load` inside Minecraft.

## In-Game Commands

| Command | Description |
| :--- | :--- |
| `/mmg methods` | Rank money-making tasks by your personalized live coins/hour |
| `/mmg info <#>` | View item drop rates, NPC vs. Bazaar pricing, and gear requirements |
| `/mmg updatestats` | Fetch your latest SkyBlock profile stats and recalculate rates |
| `/mmg flips` | Display top tax-adjusted Bazaar craft flips |
| `/mmg lb <item>` | Look up live Lowest BIN and calculate 80% / 85% lowball offers |
| `/mmg route` | Toggle 3D floating waypoints for the Crimson Isle Cinderbat loop |
| `/mmg addwp <name>` | Save your current coordinates as a custom route waypoint |
| `/mmg bz` | View live Buy Order / Sell Offer prices for watched Bazaar items |
