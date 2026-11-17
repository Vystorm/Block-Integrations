# BlockIntegrations: technical details

Companion to the [README](../README.md). Exact rules for every structure, storage, client mod, web page and testing.

## Block combos

| Structure | How to build | What it does | Upgrade paths |
|---|---|---|---|
| Inventory Sorter | light weighted (gold) pressure plate on any full copper building block | Jumping onto the plate moves items from inventory slots 9–35 into chests within 10 blocks that already contain the same item. Hotbar, armour and off hand stay untouched. | range, cooldown |
| Item Vacuum | heavy weighted (iron) pressure plate on any full copper building block | Pulls dropped items within 20 blocks into matching chests; if there is no chest at all, into slots 9–35 of the player. | range, cooldown |
| Directional Launcher | slime block on a redstone block | Launches the player about 15 blocks in the viewing direction. Optional 3x3 slime field (redstone only under the centre) shares the centre's upgrades. | power, cooldown |
| Sprint Booster | ice, packed ice or blue ice on a redstone block | Horizontal speed boost for sprinting players. Booster ice does not melt (configurable). | power, cooldown |
| Float Lift | white terracotta on any full copper building block on a redstone block | Touching the terracotta after a jump launches the player straight up and gives Levitation I for 5 s (+0.5 s per level). | power, duration, cooldown |
| Heal Block | honey block on a redstone block | Heals players in a 3-block aura by half a heart every 3 s; pauses 5 s after damage. Heal blocks need 10 blocks distance. Level 10: 3 hearts per pulse, 13-block aura. | range, healing, cooldown |

- Combos keep their upgrades in `combos.json`. Missing lower blocks pause a structure; only removing the main block deletes its entry.
- Up to 10 levels per path (costs in `config.yml`); cooldowns shrink 5 % per level, never below 5 ticks.
- The radius preview runs four bounded particle passes (about two seconds at 20 TPS), sent only to the authorised player who opened the dialog.
- The incomplete-structure check needs the matching use permission, only inspects loaded chunks, is read-only and repairs nothing.
- Block combos record their builder since 2.20.0. Older combos are claimed by the first player who opens them with sneak + right-click. This only affects the web page; at the block every permitted player can still upgrade them.

### Sorter slot locks and chest priorities

- `/bi sortlock list|clear|<slot>` uses Minecraft's internal main-inventory numbers 9 through 35. Locks belong to the player, persist across sessions and can't be set for others.
- `/bi chestpriority add` appends the loaded chest you look at (within 6 blocks) to your personal order, up to 8 positions. `overflow` makes one chest the final destination, `remove`, `list` and `clear` manage the list. Preferences never change chest or machine ownership.
- Only chests already found by the structure's normal loaded-chunk and radius scan count. The overflow chest still follows the matching-item rule. Double chests count as one target, and either half matches a saved position. Personal priorities grant no access rights.
- Sorter and vacuum recheck the optional VystormCityClaim service's `CONTAINER` permission on both double-chest halves and skip locked chests. If CityClaim is installed but its service is unavailable, insertion is denied. Other claim plugins are not checked. A chest is skipped if a neighbouring chunk needed to resolve a double chest is unloaded.

## Attack towers

- Three blocks high (head, work block, foundation) with four identical ring blocks in a cross (N, S, E, W) at foundation height. The head is the attack point and opens the tower dialog with sneak + right-click.
- Towers attack the nearest hostile mob in loaded chunks, never players, animals or villagers; by default with line of sight only and ignoring named mobs.
- Looting: 0 to *level* bonus items per stackable drop.
- **Loot chest:** up to 54 slots. Drops of mobs the tower kills go there instead of the ground, including drops that mob plugins spawn at the death position. With `towers.pause-when-loot-full` (default on), new attacks pause once every slot is filled to its stack limit and resume after space is freed. Projectiles already in flight can still produce drops, and a drop that can't stack with the contents can still overflow; overflow is destroyed and the owner gets a rate-limited notice.
- **Kill XP** is stored in the tower and withdrawn in the dialog, by default only while the owner (or a town member) is within 48 blocks, and not from spawner mobs.
- **Ownership:** max 8 per player by default (`towers.max-per-player`, admins exempt). Other players can't break them. If Vystorm Core's region register has a region of kind `city` at the tower, members standing in that town may use it too.
- **Target profiles:** owners and admins cycle all allowed hostiles, undead, arthropods, raid mobs or none. Profiles only narrow the existing protections; named, neutral or protected mobs never become eligible. Area damage checks the same protections for every secondary target.
- **Redstone modes:** always on, on with signal, on without signal, off. The input is three blocks east of the head at the same height, shown in the menu, outside the structure pattern. An unloaded input chunk pauses signal-controlled towers without loading it. Modes control new attacks, not ones already in flight.
- **Sharing:** owners and admins can grant up to 32 UUIDs separate Use, Loot (including stored XP) and Upgrade rights. Enter an exact online name or a UUID; grant Use too for menu access. Revocation is rechecked on loot clicks, drags and upgrade callbacks. Grants don't change break permissions or ownership.
- Tower state is saved in batches as schema 2. Schema 1 migrates with all-targets, always-on and no-grants defaults. Unrecognised saved profile or mode values disable that behaviour until corrected. Don't downgrade a schema-2 state file to an older plugin.

## Upgradable furnaces

- Owner is fixed on placement; existing furnaces are claimed by the first sneak + right-click. Only the owner or `blockintegrations.furnaces.manage-others` can manage one.
- Paths: smelting speed (up to 2.2x), fuel efficiency (up to 2x burn time), bonus yield (up to 25 % chance of an extra result), multismelt (up to 11 items per cycle), XP boost (5 levels, up to 2x stored vanilla XP).
- Upgrades, owner and stored XP live in the block itself. `furnaces.json` is only an index of owned furnaces for the web page.
- The dialog shows observed activity from a roughly 60-second sliding window: accepted smelt events, fuel items whose burn events were accepted and configured to consume fuel, processed output items (including multismelt inputs) and yield bonus items actually triggered. Counters are transient and reset on restart or chunk unload. Speed and yield previews show configured multipliers and chances, not predictions.
- **Vystorm Skills (optional, via reflection):** hopper-fed furnaces give the owner smelting or cooking skill XP per smelted item, stored while offline (`pending-furnace-skill-xp.json`) and delivered on login, boosted by the XP boost path.

## Portable main blocks

The main block (combo top, tower head, furnace) keeps its upgrade levels when broken, as versioned item data plus descriptive lore; hand-written lore grants nothing. Normal drop rules apply (e.g. Silk Touch for ice). Creative mode, explosions or drops suppressed by other plugins don't create upgraded items.

## Storage

Local JSON in Vystorm Core's versioned format: `combos.json`, `towers.json`, `furnaces.json`, `pending-furnace-skill-xp.json` (`{"schema": n, "data": …}`), written atomically. Unreadable files are backed up as `<file>.corrupt-<millis>` and never overwritten. Since 2.20.2 the old YAML files (`*.yml`) are not imported automatically.

On Core 0.26.5+, `/vadmin diagnostics` includes machine storage health. `/vadmin migration blockintegrations:legacy-combos` previews legacy `combos.yml` conversion on detached data, comparing record counts and upgrade sums, without applying anything.

## Client mod

With the Vystorm client mod (Core 0.26.4, capability `web-client-ui`):

| Feature | With the mod | Without the mod / UI hidden (H) |
| --- | --- | --- |
| Tower markers | Waypoints over your own towers in your world (nearest 16, up to 512 blocks, withdrawable XP in the label) | `/bi towers list` with clickable locations |
| Loot chest full | Warning toast in addition to the chat message | Chat message with buttons |
| Portable blocks | Two tooltip lines in your language: what the item is and that its upgrades carry over | Item lore (language of the player who broke it) |

With client 0.9.0+ the tooltips also list the upgrade levels read from the item (e.g. "Damage: 3") and the loot toast has a "[Y] Open loot" action with the same checks as the chat button. Config: `client.tower-markers`, `client.portable-tooltips` (default `true`).

## Web page

With Vystorm Core 0.24.0+ and `web.enabled: true` in Core's config, BlockIntegrations adds the page "Blocks & Towers" (module `blocks`) to the Vystorm web panel, in the browser (`/web blocks` or `/bi web`) and as an in-game overlay with the client mod.

- **My structures:** your towers, furnaces and combos with type, levels, world, coordinates and state (active, incomplete, not loaded). Refreshes live on upgrades, builds, removals and new loot.
- **Upgrades from the web** (you must be online in game): the same code, checks and costs as the in-game dialogs: permissions, ownership, complete structure, maximum level, the offer you saw, XP cost. Remote access needs the owner or an admin (furnaces: owner or `manage-others`). Chunks are never loaded for the page; structures in unloaded areas show their last known values and can't be upgraded.
- **Tower loot chest:** view contents from anywhere (owner or admin). Taking items works only while online and within 64 blocks of the tower, the same rule as the chat loot button. Items move server-side into your inventory; what doesn't fit stays in the chest.
- **Building guide:** block diagrams (side view, foundation layer), descriptions and upgrade tables with this server's values and costs, generated from the plugin's definitions and `config.yml`.
- **All structures** (`blockintegrations.admin`): every tower, furnace and combo with owner, filterable.
- In game there is an "Open in web" button in `/bi menu` and in the upgrade dialogs while the web platform runs. Server settings are on Core's settings page (`/app/settings`).

## Languages

`language: auto` follows Vystorm Core: each player sees their Minecraft language or the one chosen in `/vsettings`; unknown languages fall back to English, the console uses English. A fixed code forces one language. Texts are in `plugins/BlockIntegrations/lang/<code>.yml` (MiniMessage, `{placeholders}`). Files are never overwritten by updates; new keys fall back to the jar version.

## Tests

Tests use JUnit 6 and Mockito in `tests/`. `./gradlew integrationJar` builds an optional acceptance test plugin (`integration/`) that only runs with `-Dblock.portability.test=true` on a separate test server; it modifies test blocks and stops that server afterwards.
