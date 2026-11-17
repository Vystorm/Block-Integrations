# BlockIntegrations

BlockIntegrations turns ordinary blocks into small machines. Put a gold pressure plate on copper and you have an inventory sorter. Put a slime block on redstone and it launches you 15 blocks forward. Stack a skeleton skull on a fletching table and you get an archer tower that guards your base and collects the loot. Everything is upgraded with XP points through Core's dialog menus (clickable chat on older servers), no extra items or resource packs needed.

Player guide in game: `/vmenu tutorials` or `/bi` → Blocks & Upgrades. German guide as a file: [TUTORIAL.md](TUTORIAL.md).

## Features

### Harvester profiles (2.33.0)

Sneak-right-click your harvester and choose **Choose harvest profile**: All plants (default), Replanted crops (wheat, carrots, potatoes, beetroot, nether wart and cocoa), Melons and pumpkins, Sugar cane and bamboo, or Paused. Excluded plants stay untouched. Maturity, replanting, city borders, output handling and upgrade cooldowns still apply. Changes take effect on the next scheduled pass; pausing does not reset the cooldown.

Only the owner or an admin may change the profile, with `blockintegrations.combos` and within the usual 64-block menu range. Profiles persist per machine in `combos.json` across restarts; existing machines default to All plants. Breaking and replacing the composter resets the profile to All plants (upgrades are retained as before). No new commands, permissions or configuration keys.

### Keep supplies when sorting (2.32.0)

Hold food, torches or a tool and run `/bi sortlock item` to toggle a personal exclusion for that entire item type. The sorter leaves all matching items in your inventory, even after you move them to another slot; names, enchantments and other metadata do not affect the exclusion. `/bi sortlock items` lists excluded types; `/bi sortlock clearitems` clears them. These preferences survive reconnects and restarts and work alongside slot locks. `/bi sortlock clear` still clears only slot locks. The item vacuum is unaffected.

### Your combos at a glance (2.31.0)

`/bi combos mine` lists every block combo you built – type, coordinates and distance, nearest first, then the ones in other worlds – with a clickable location button for each.

### Warp pads and XP collector (2.29.0)

**Warp pads** – put a lodestone on crying obsidian. Stand on it and sneak: a menu lists your other warp pads in the same world (nearest first, with name and distance). Click one and you are teleported on top of it. No commands, no coordinates to type.

- Name your pads: sneak-right-click the lodestone → *Rename* (owner only). Unnamed pads show their coordinates.
- Share with friends: sneak-right-click a player → *Warp pads* in Vystorm Core's player menu (Core 0.43.0), or `/bi warppads share NAME`. Shared players can travel on all your pads; one more click takes the access back. `/bi warppads` lists your pads with clickable coordinates and who you share them with.
- Upgrades (XP, sneak-right-click): **Distance** (256 blocks, +128 per level, 1536 at level 10) and **Cooldown** (5 seconds between warps, shorter per level).
- Safe arrival: the target chunk is loaded without freezing the server, the pad must still be complete and the two blocks above it free (no lava, fire or walls). Same world only, 10 pads per player by default (admins exempt).

**XP collector** – put an enchanting table on a lapis block. Every half second it pulls experience orbs within 6 blocks into the block, up to 1500 XP points (+1500 per capacity level). Perfect under a mob farm: the XP waits in the block instead of despawning.

- Sneak-right-click the table: see the stored XP and take **1 level** or **everything** (owner and admins). A normal right-click still opens the vanilla enchanting screen.
- Fair by design: orbs within 3 blocks of a player stay for that player, orbs in another city's area are never taken, and a player's death drop is left alone (orbs from a player's death, and every orb within 8 blocks of where a player died, for 5 minutes).
- Ownership: whoever places the table (or the lapis) owns the collector. A collector built before 2.29.0 has no owner yet; the first player who sneak-right-clicks it takes it over only if they could build at that spot (protection plugins such as city claims decide), otherwise it stays ownerless and only admins can take its XP.
- Breaking the table spills the stored XP as orbs, so nothing is lost. Upgrades: **Range** and **Capacity**.

Both have their own switch and settings under `combos.warp-pad` and `combos.xp-collector` (also in Settings → Server → Block combos and `/vadmin config BlockIntegrations`).

### Workstations and tower effects (2.22.0)

Sneak-right-click a brewing stand, campfire or soul campfire to open its XP upgrade menu. Brewing stands gain speed (+12% per level) and fuel power (+10% per level). Both campfires gain speed (+12% per level) and a chance of one extra cooked item (+2.5 percentage points per level). Bonuses apply to newly started operations; vanilla recipes and ingredient consumption remain in charge. Defaults: ten levels, each next level costs `100 * nextLevel²` XP points. Stay within eight blocks while purchasing. Only the owner or an administrator can upgrade a workstation; ordinary use stays available.

Matching native block drops retain ownership and upgrades, including when that workstation feature is disabled. Campfires need Silk Touch to drop their block. `/bi workstations` shows the guide. Configure these features under `workstations` in `config.yml` or Core's `/vadmin config BlockIntegrations` editor.

Towers now have distinct muzzle flashes, four-frame shot trails, arcing cannon shots and hit bursts. Cannon and flame hits add expanding rings. Hit particles require actual health or absorption damage; attack timing and damage are unchanged. Disable cosmetics with `towers.visual-effects: false`. Particles and animation scheduling use Vystorm Core and its shared particle budget.

### Block combos

| Structure | How to build | What it does |
|---|---|---|
| Inventory Sorter | light (gold) pressure plate on a copper block | Jump on it and items from your main inventory go into chests within 10 blocks that already hold the same item. Hotbar and armour stay put. |
| Item Vacuum | heavy (iron) pressure plate on a copper block | Pulls dropped items within 20 blocks into matching chests, or into your inventory if there is no chest. |
| Directional Launcher | slime block on a redstone block | Launches you about 15 blocks in the direction you look. A 3x3 slime field with redstone under the centre works too. |
| Sprint Booster | ice, packed ice or blue ice on a redstone block | Speed boost while sprinting. The ice doesn't melt. |
| Float Lift | white terracotta on copper on redstone | Jump against it and you float straight up with Levitation for 5 seconds. |
| Heal Block | honey block on a redstone block | Heals players within 3 blocks by half a heart every 3 seconds, pausing 5 seconds after damage. At level 10: 3 hearts in a 13-block aura. |
| Harvester | composter on a hay bale | Every 10 seconds harvests ripe crops (wheat, carrots, potatoes, beetroot, nether wart, cocoa pods), melons, pumpkins and upper sugar cane/bamboo within 4 blocks, replants and fills a chest touching the composter or hay bale. Pauses while that chest has no free slot. |
| Warp Pad | lodestone on crying obsidian | Stand on it and sneak, pick another of your (or a friend's shared) pads in the same world and teleport there. |
| XP Collector | enchanting table on a lapis block | Pulls experience orbs within 6 blocks into the block; the owner takes the XP out in its menu. |

Sneak and right-click the top block to open its upgrade menu. Each path (range, power, cooldown and so on) goes up to level 10. The menu also shows the current radius as a short particle ring, only to you. If a structure is incomplete, the same click tells you which block is missing and where.

Your sorter can skip inventory slots you lock (`/bi sortlock`), and you can give sorter and vacuum a personal chest order with an overflow chest (`/bi chestpriority`).

### Attack towers

A tower is three blocks high (head, work block, foundation) with four matching blocks in a cross around the foundation. Place the head last. Towers shoot the nearest hostile mob, never players, animals or villagers, and by default need line of sight and leave named mobs alone.

| Tower | Top → bottom | Ring | Attack |
|---|---|---|---|
| Zombie Watchtower | zombie head, crafting table, cobblestone | oak planks | cheap starter |
| Recruit Tower | player head, barrel, stone | cobblestone | cheap starter |
| Archer Tower | skeleton skull, fletching table, copper block | stone bricks | fast single target |
| Cannon Tower | creeper head, dispenser, blast furnace | obsidian | slow area damage, no block damage |
| Flame Tower | wither skeleton skull, magma block, nether bricks | polished blackstone bricks | damage plus fire |
| Piglin Gold Tower | piglin head, smithing table, gold block | polished blackstone bricks | very fast precision |
| Dragon Tower | dragon head, respawn anchor, purpur block | end stone bricks | expensive long-range area damage |

- The five big towers upgrade damage, range and attack speed (up to 10) and looting (up to 3). The two starters can't be upgraded.
- Every tower has its own loot chest with up to 54 slots. When it's full, the tower stops starting new attacks until you empty it.
- Kill XP is stored in the tower and withdrawn in its menu while you're within 48 blocks. Spawner mobs give none.
- Towers belong to their builder, 8 per player by default. Owners can share use, loot and upgrade rights with up to 32 players, pick a target profile (undead, arthropods, raid mobs) and switch the tower on or off with redstone.
- Tower blocks resist explosions, pistons and liquids. Breaking the tower drops its loot and XP.

### Upgradable furnaces

Furnaces, blast furnaces and smokers belong to whoever places them (existing ones: first sneak-right-click claims them). Upgrades: up to 2.2x smelting speed, 2x burn time, 25 % chance of an extra result, up to 11 items per cycle and 2x XP. XP from hopper-fed smelting is stored in the furnace and paid out in its menu. With Vystorm Skills installed, the owner also gets smelting and cooking skill XP.

### Take your upgrades with you

Break the main block (combo top, tower head, furnace) and it keeps its upgrade levels as an item. Place it again and the upgrades are back; the rest you rebuild by hand.

### More

- With the Vystorm client mod you get waypoints over your towers, a toast when a loot chest is full, and tooltips on carried upgrade blocks.
- With Vystorm Core's web platform there is a "Blocks & Towers" page (`/bi web`) listing your structures, with upgrades, tower loot and a building guide.
- English, German and Simplified Chinese, per player.

## Installation

Use **Vystorm Core 0.64.0 or newer**. The optional client mod has separate version requirements.


- **Paper 1.20.6 through 26.x; Spigot and CraftBukkit 26.3**, Java **21 or newer** (use the Java version required by your server release). See [compatibility checks](COMPATIBILITY.md).
- [Vystorm Core](https://github.com/Vystorm/VystormCore) 0.64.0 or newer, including its portable dialogs and compatibility helpers.
- Optional: Vystorm Skills (furnace skill XP), VystormCityClaim (sorter and vacuum respect its chest permissions)

Drop the jar into `plugins/` and start the server.

Changes: [CHANGELOG.md](CHANGELOG.md).

## Commands and permissions

| Command | Permission | What it does |
|---|---|---|
| `/bi` (also `/blockintegrations`, `/blockint`) | `blockintegrations.use` | Overview menu for players, status for admins |
| `/bi menu` | combos, towers or furnaces | Overview menu with guides |
| `/bi web [guide]` | | Open the web page |
| `/bi combos`, `/bi towers`, `/bi furnaces` | matching permission | Building guides in chat |
| `/bi towers list [player]`, `/bi towers near` | `blockintegrations.towers` | Your towers with clickable locations, or towers within 48 blocks |
| `/bi towers remove` | `blockintegrations.admin` | Remove the tower you're looking at |
| `/bi warppads [list]`, `/bi warppads share\|unshare NAME` | `blockintegrations.warppads` | Your warp pads with clickable locations; share all your pads with a player or take it back (also in Core's player menu) |
| `/bi sortlock list\|clear\|<slot>` | `blockintegrations.use` | Lock inventory slots (9 to 35) from the sorter |
| `/bi sortlock item\|items\|clearitems` | `blockintegrations.use` | Toggle the held item type, list excluded types, or clear item-type locks |
| `/bi chestpriority add\|overflow\|remove\|list\|clear` | `blockintegrations.use` | Personal chest order for sorter and vacuum (up to 8 chests) |
| `/bi status`, `/bi reload` | `blockintegrations.admin` | Status line, reload config and language files |

| Permission | Default | For |
|---|---|---|
| `blockintegrations.use` | everyone | Using sorter, vacuum, launcher, booster, lift, heal block |
| `blockintegrations.combos`, `.combos.upgrade` | everyone | Open, upgrade combos; owners/admins select harvester profiles |
| `blockintegrations.warppads` | everyone | Travel on, rename and share warp pads |
| `blockintegrations.towers`, `.towers.upgrade` | everyone | Build and use, upgrade towers |
| `blockintegrations.furnaces`, `.furnaces.upgrade` | everyone | Own, upgrade furnaces |
| `blockintegrations.furnaces.manage-others` | OP | Manage other players' furnaces |
| `blockintegrations.admin` | OP | Status, reload, all towers, server settings |
| `blockintegrations.*` | OP | Everything |

German aliases work too (`menue`, `kombos`, `tuerme`, `oefen`, `warpfelder`). Clicking coordinates in chat teleports only with Core's `vystorm_core.chat.teleport`.

## Configuration

Admins can edit the whole `config.yml` in game with `/vadmin config BlockIntegrations` (Core 0.27, permission `vystorm_core.config.blockintegrations`). The main values are also in the menu under Settings → Server (Basic blocks, Block combos, Attack towers, Upgradable furnaces). Changes apply immediately.

`config.yml` covers radii, cooldowns, launcher and lift strength, the heal block, warp pads (`combos.warp-pad`: `enabled`, `base-distance`, `distance-per-level`, `cooldown-ticks`, `max-per-player`, `play-particles`), the XP collector (`combos.xp-collector`: `enabled`, `radius`, `capacity`, `capacity-per-level`, `player-safe-radius`, `play-particles`), upgrade costs, tower stats per type, `towers.max-per-player`, `towers.pause-when-loot-full` and furnace bonuses.

No database is needed. Data is stored as local JSON files in the plugin folder plus block and item data. Old YAML data files from before 2.20.2 are not imported automatically. Don't downgrade the plugin after towers were saved in the newer format.

`language: auto` follows each player's Minecraft language. Texts are in `plugins/BlockIntegrations/lang/`; copy `en.yml` to e.g. `fr.yml` to add a language and run `/bi reload`.

## For developers

Build with JDK 25; all source sets emit Java 21 bytecode. Put the Vystorm Core jar at `libs/Vystorm_Core.jar` or pass it:

```
./gradlew build -PvystormCoreJar=/path/to/Vystorm_Core.jar
```

The offline `check` includes `compat1206` (main sources compiled against Paper 1.20.6) and `test1206` (adapter and linkage tests against that API). Set `-Ppaper1206Libraries=/path/to/smoke-1206/libraries` on other machines; the default is `<home>/source/tools/smoke-1206/libraries`. New API adapters live in `modern/` and are packaged in the same jar. On older servers menus use clickable chat, and anvil enchantment upgrades require a valid vanilla result because the native enchantment-restriction bypass is unavailable.

Output: `build/libs/BlockIntegrations-<version>.jar`. Sources live in `src/`, tests in `tests/`, and `plugin.yml`, `config.yml`, `tutorial*.yml` and `lang/` at the repository root.

Detailed rules for combos, towers, furnaces and portable blocks, plus storage, the client mod, the web page and the acceptance test plugin, are in [docs/TECHNICAL.md](docs/TECHNICAL.md).

## Monsterfallen

**Schleuderfalle:** Schleimblock als Kern im 3×3-Obsidianrahmen, genau ein Knopf vorne am Rahmen. Boden- und Deckenfallen schleudern vom Knopf weg; Wandfallen nach vorne und oben. Stärke und Höhe sind im Fallen-Bereich der Server-Einstellungen konfigurierbar.

3×3-Obsidianrahmen mit Kolben-, Eisen- oder Honigkern für Wurf-, Stachel- und Teerfallen. Knopf auf die Vorderseite des Rahmens setzen und rechtsklicken; Shift + Rechtsklick öffnet Automatik und 10/15 Sekunden Cooldown. Boden, Wand und Decke werden unterstützt. Anleitung: /blockintegrations traps und [docs/TRAPS.md](docs/TRAPS.md).

Amboss-Upgrades: [Bedienung, Abhängigkeiten und Abnahme](docs/ANVIL-UPGRADES.md).


## Free feature update 2.38.0-26.2

Add `/bi dashboard`, a paginated live view of the current player's owned combos, towers and indexed upgraded furnaces. Category permissions and ownership are checked again on every detail action. The dashboard reads existing registries; it does not scan or load world chunks.

Furnaces show missing input, missing fuel, full output, actual cooking progress or waiting for a valid recipe/output. Harvesters report paused profiles and full adjacent output containers; towers report disabled targets/redstone controls and incomplete structures. Ready means the structure can be activated, not that an idle tower currently has a target.

Each machine detail has an explicit alert subscription. Up to 32 targets per player are stored in player PDC and survive normal restarts. Only online owners with the relevant permissions receive state-change alerts, checked every 10 seconds, with a two-minute per-target cooldown; initial observation does not send an alert. Disabling the plugin cancels polling, and offline tracking state is released. The interface uses Core's existing portable dialogs on Spigot/CraftBukkit and Paper.

## License

Copyright (c) 2026 Esoren

Versions after 2.42.0 are licensed under the GNU General Public License v3.0 (GPL-3.0), see [LICENSE](LICENSE).
Version 2.42.0 and earlier were released under the MIT License ([LICENSE-MIT-until-2.42.0.txt](LICENSE-MIT-until-2.42.0.txt)) and stay available under those terms.
