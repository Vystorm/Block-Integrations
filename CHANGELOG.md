## 2.43.0-26.2 — 2026-11-17

- Requires Vystorm Core 0.64.0 or newer. Update Core before you update this plugin.
- The order in which Vystorm plugins react to the same event is now set in one place, Vystorm Core (`event-order` in Core's `config.yml`). The protection of towers (explosions, pistons, liquids and breaking by other players) now denies before other plugins act.
- License: GPL-3.0 from this version. Earlier versions stay under the MIT License.

## 2.42.0-26.2 — 2026-11-16

- `/bi status` (admins) now also shows how many upgraded furnaces the server has, in all seven languages.

## 2.41.2-26.2 — 2026-11-14

- Performance: the block-physics check for sorter and vacuum plates (block physics fires constantly) now returns at once for every other block, and the ice booster checks the block under the player before the permission.

## 2.41.1-26.2 — 2026-11-14

- Fixed: On Spigot, `/blockintegrations combos`, `traps`, `towers`, `furnaces` and `workstations` failed with an internal error instead of showing their guide. They now work on Spigot too.

## 2.41.0-26.2 — 2026-11-12

- Machine blueprints: in a furnace or combo menu, **Save as blueprint** remembers its upgrade levels (and a harvester's profile). On another machine of the same type, **Apply blueprint** buys the missing upgrades one by one at the normal price and shows the total first; nothing is ever lowered. Setting `machine-blueprints.enabled`, also in the first-run setup.

## 2.40.0-26.2 — 2026-11-11

- Machine groups: in `/bi dashboard` → Groups, put machines that belong together (a farm, a smeltery, the base defence) into named groups – up to 8 groups of 32 machines. A group shows all its machines with a state summary ("3× Ready, 1× No fuel"), pauses and resumes all its harvesters from anywhere (each gets its previous harvest profile back) and switches alerts for all members at once. Add machines from their detail page.

## 2.39.0-26.2 — 2026-11-10

- First-run setup: when the plugin is installed for the first time, admins answer a few questions or take the recommended settings, with a preview before anything is saved (needs Vystorm Core 0.54.0 or newer).
- All settings, including extra configuration files, appear in Core's new `/vadmin config` menu.

## 2.38.0-26.2 (2026-11-07)

Add `/bi dashboard`, a paginated live view of the current player's owned combos, towers and indexed upgraded furnaces. Category permissions and ownership are checked again on every detail action. The dashboard reads existing registries; it does not scan or load world chunks.

Furnaces show missing input, missing fuel, full output, actual cooking progress or waiting for a valid recipe/output. Harvesters report paused profiles and full adjacent output containers; towers report disabled targets/redstone controls and incomplete structures. Ready means the structure can be activated, not that an idle tower currently has a target.

Each machine detail has an explicit alert subscription. Up to 32 targets per player are stored in player PDC and survive normal restarts. Only online owners with the relevant permissions receive state-change alerts, checked every 10 seconds, with a two-minute per-target cooldown; initial observation does not send an alert. Disabling the plugin cancels polling, and offline tracking state is released. The interface uses Core's existing portable dialogs on Spigot/CraftBukkit and Paper.

## 2.37.0-26.2 (2026-11-07)

- Add genuine Spigot and CraftBukkit 26.3 support through Core 0.51.0: portable messages, menus, XP and entity helpers, plus native furnace recipes, recipe XP accounting, merged experience-orb counts and location-bound anvils. Paper keeps its existing native paths.
- Protect upgraded anvils after completed Bukkit result takes; cancelled takes and explicitly replaced or broken blocks are left alone. The Paper damage listener loads only when its event exists.
- Fix tower collection losing XP from merged orbs. Protect player-death orbs on Bukkit using persistent markers, including when XP collectors are disabled.
- Verify native furnace and orb behavior on official Spigot/CraftBukkit 26.3 and anvil opening with a connected client. Add regression tests for orb counts, furnace snapshot accounting and cancelled/completed anvil uses; retain the Paper 1.20.6 compile and linkage checks.

## 2.36.0-26.2 (2026-11-06)

- Add complete French (`fr`), Brazilian Portuguese (`pt_br`), Italian (`it`) and Russian (`ru`) language bundles. Minecraft client locales and the Core language selector choose them automatically.
- Include translated tutorials in all four languages.
- Validate packaged language keys, placeholders, formatting and tutorial coverage in server-loader tests.

## 2.35.0-26.2 (2026-11-05)

- Furnace menus show the owner's queued skill XP across all furnaces when any is pending. This read-only amount is separate from withdrawable vanilla XP; existing owner/admin menu permissions apply. Includes English, German and Simplified Chinese labels.
- Legacy furnace XP migration now persists the merged balance and an import marker before archiving the YAML source. Interrupted startup no longer loses imported XP; an archive failure no longer imports it repeatedly. Failed writes and malformed YAML leave the source available for retry. The pending state format is now schema 2; schema 1 balances upgrade automatically.

## 2.34.0-26.2 (2026-11-03)

- Runs on Paper 1.20.6 – 26.x. Java 21 bytecode throughout; requires Vystorm Core 0.48.0 for portable dialogs and compatibility helpers.
- Older servers use Core's clickable chat menus, including text input, instead of native dialogs. Anvil cost, rename and maximum-cost handling and brewing speed use the older APIs; newer servers retain native behavior through a separately compiled adapter in the same jar. Health attributes and inventory views use compatibility helpers.
- On 1.20.6 the native anvil enchantment-restriction bypass is unavailable: enchantment upgrades apply to valid vanilla results; combinations rejected by vanilla cannot be unlocked. No entire machine feature is disabled.
- Added offline `compat1206` compilation and compatibility/linkage tests against the actual 1.20.6 libraries to `check`.

## 2.33.0-26.2 (2026-11-03)

- New: persistent harvester operating profiles in the Core upgrade menu: all plants, replanted crops, melons/pumpkins, sugar cane/bamboo, or pause. Owners and admins can select a profile within the normal menu range; actions recheck ownership, permissions and the original complete machine. Filtering leaves other crops untouched and preserves existing maturity, region, replanting and cooldown rules.
- Existing harvesters default to all plants; profiles are an optional field in `combos.json`. Breaking and replacing the composter resets its profile. English, German and Simplified Chinese menus and feedback; no new commands, permissions or config keys.

## 2.32.0-26.2 (2026-11-02)

- New: personal sorter item-type locks. Hold an item and use `/bi sortlock item` to keep every item of that type in your inventory, regardless of its slot, name, enchantments or other metadata. Repeat to unlock; `/bi sortlock items` lists protected types and `/bi sortlock clearitems` clears them. Existing slot locks and their `clear` command stay independent. Preferences persist on the player; no config migration or new permission is needed. Commands and feedback are available in English, German and Simplified Chinese.

## 2.31.0-26.2 (unreleased)

- New: `/bi combos mine` (German `/bi kombos meine`, Chinese `/bi 组合 我的`) lists the block combos you built: type, coordinates and distance, nearest first in your world, then your combos in other worlds, each with a clickable location button (up to 20 lines, then "and N more"). Combos built before 2.20.0 have no builder recorded and appear once you have used them.

## 2.30.0-26.2 (unreleased)

Needs Vystorm Core 0.45.0.

- Simplified Chinese (zh_cn) translation.
- `/blockintegrations` (`/bi`, `/blockint`) now runs through Vystorm Core's command system: subcommands, help, tab completion and error messages in the player's language (`/bi tuerme liste`, `/bi truhenvorrang ueberlauf`, `/bi 传送垫 共享 Alex` work next to the English names for everyone), a clickable `/bi help` (`hilfe`, `帮助`), a "did you mean" for typos and an entry in `/vhelp`. The old German names (`menue`, `kombos`, `fallen`, `tuerme`, `oefen`, `ofen`, `werkstationen`, `warpfelder`) keep working. New localized root commands `/blockkombos` (German) and `/方块组合` (Chinese) from the language files (`commands.blockintegrations.*`); which languages are active is set server-wide in Core (`commands.localized-aliases`). All English subcommands, arguments and permissions are unchanged; an unknown second word after `towers` or `warppads` now gets Core's "unknown subcommand" hint instead of the guide or usage text. The command is no longer declared in plugin.yml.

## 2.29.0-26.2 (2026-10-30)

- **New block combo: Warp Pad** – lodestone on crying obsidian. Stand on it and sneak to open a travel menu with your other pads in the same world (nearest first, up to 24); one click teleports you on top of the chosen pad. The target chunk is loaded asynchronously, the pad must still be complete and the two blocks above it free. Owners rename pads in the upgrade menu (sneak + right-click). Share all your pads with a player through Vystorm Core's player menu (Core 0.43.0: sneak + right-click the player → *Warp pads*) or `/bi warppads share|unshare NAME`; `/bi warppads` lists your pads and guests. Upgrades: distance (256 blocks + 128 per level) and cooldown (5 s). At most 10 pads per player (admins exempt). New permission `blockintegrations.warppads` (default: everyone), settings under `combos.warp-pad`.
- **New block combo: XP Collector** – enchanting table on a lapis block. Pulls experience orbs within 6 blocks into the block every half second, up to 1500 XP points (+1500 per capacity level). The owner (or an admin) takes one level or everything in its menu; a normal right-click still opens the enchanting screen. Orbs within 3 blocks of a player and orbs in another city stay where they are. Breaking the table spills the stored XP. Upgrades: range and capacity. Settings under `combos.xp-collector`.
- Both are on by default, have their own `enabled` switch and appear in Settings → Server → Block combos and in Core's config editor. Existing `combos.json` files load unchanged; pad names, stored XP and warp access are added as new optional fields.

Fixed:

- XP collector no longer pulls a player's death drop: orbs spawned by a player's death and every orb within 8 blocks of a death place stay for 5 minutes, so the player can come back for them.
- An ownerless combo (an XP collector or warp pad built before this version, or any combo from before 2.20.0) is only taken over on sneak + right-click by a player who could build at that spot (protection plugins decide through a test placement). Otherwise it stays ownerless and only admins can take its XP.

## 2.28.0-26.2 (2026-10-29)

- Harvester now also picks ripe cocoa pods around it and replants them on the same jungle log side.
- Harvester pauses while its touching chest or barrel has no free slot, so a full chest no longer turns the farm into a pile of dropped items; the crops wait until there is room again. New switch `combos.harvester.pause-when-full` (default `true`; `false` restores the old drop-on-top behaviour).

## 2.27.1-26.2 (2026-10-28)

- Registers itself for the Vystorm Core update check (Core 0.40.2+); older Cores are unaffected.

## 2.27.0 - Harvester combo

- New block combo: **Harvester** – a composter on a hay bale. Every 10 seconds it harvests ripe wheat, carrots, potatoes, beetroot and nether wart, stem-grown melons and pumpkins and the upper segments of sugar cane and bamboo within 4 blocks (one block above to two below the composter), replants the crops with a seed from the harvest (or from its chest) and puts the yield into a chest, barrel or other container touching the composter or the hay bale; without one it drops on top. Up to 64 blocks per pass, never across a city border. Range and cooldown are upgradable like the other combos (sneak + right-click the composter). Settings under `combos.harvester`.
- Spike traps in automatic mode no longer count as the owner's kill, so they cannot run AFK loot/XP farms with player-only drops. Firing a trap by hand still credits the owner.

## 2.26.0 - Brewed potion bonuses

- Add extra-effect chance (5 x 1%), strength chance (10 x 10%, +1 level), and duration (10 x 25 seconds) upgrades.
- Use configurable quadratic XP prices, a positive extra-effect pool and one persisted roll per finished bottle; no rerolls when brewing further.
- Preserve upgrades in portable brewing stands; leave managed Items potions to their provider.

## 2.25.1 - Brewing stand menu

- Add a bottom-left brewing stand upgrade button and a center entry to the native brewing inventory.
- Reuse owned speed/fuel upgrades and XP payments; preserve shift-right-click access and normal use by other players.
- Reject stale/distant menu actions, protect menu items and close menus on disable.

## 2.25.0 - Upgradeable anvils

- Add bottom-left upgrade entry, XP efficiency, raised enchantment caps and unlimited use durability.
- Preserve owned upgrades in block drops and falling anvils; expose bilingual server settings.
- Integrate managed item previews and delivery through Core item-anvil (Core 0.40.1 / Items 0.8.1).

## 2.24.0 - Sling traps

- Add slime-core sling traps with floor/ceiling direction controlled by a single frame button, plus wall-mounted arcs.
- Expose sling power and lift in server settings and the config editor; reuse automatic/button triggering, protection and persistent cooldowns.
- Extend bilingual guides and tutorials and test direction, ambiguous buttons and protected automatic activation.

## 2.23.0 - Monster traps

- Add 3×3 obsidian-frame throw, spike and tar traps for floors, walls and ceilings.
- Button activation and automatic hostile-mob detection share a persisted 10/15 second cooldown.
- Owner settings, loaded-only checks, protected-region filtering, line of sight and cancellable effect events.
- Add bilingual guide and /blockintegrations traps (fallen).

## 2.22.0 (2026-10-25)

- Add owner-managed XP upgrades for brewing stands (speed and fuel efficiency), campfires and soul campfires (speed and bonus yield). Sneak-right-click for the Core dialog; `/bi workstations` opens the guide.
- Preserve upgrades and ownership on matching native block drops. Campfires require Silk Touch to drop their block. Reject stale purchases, out-of-range purchases and unloaded chunks; refund XP when persisting an upgrade fails.
- Give each tower a distinct shot trail and muzzle effect, including arcing cannon shots and expanding area-hit rings. Hit particles require actual health or absorption damage. Animation tasks stop on shutdown; cosmetics can be disabled with `towers.visual-effects`.
- Reuse Core XP transactions, dialogs, translations, particle budget and animation scheduling. Vanilla continues handling workstation recipes and ingredients.

## 2.21.5 (2026-10-25)

- Return permission-filtered command suggestions for completion requests with no arguments, matching completion of an empty first argument.

# Changelog

## 2.21.4 (2026-10-24)

- `towers near` lists the closest towers first and shows at most 30 entries, while the header retains the total nearby count. This bounds chat output in tower-dense bases.

## 2.21.3 (2026-10-22)

- Build against the older Paper/Adventure API and allow servers from 1.21.8; Java 25 remains required. Use Core 0.28.1 or newer. Generate the plugin descriptor version from Gradle to keep JAR and runtime versions consistent.

## 2.21.2-26.2 — 2026-10-18

- **Konfiguration im Core-Editor:** ab Vystorm Core 0.27.0 ist die ganze `config.yml` unter `/vadmin config BlockIntegrations` bearbeitbar (Recht `vystorm_core.config.blockintegrations`); 49 Werte tragen die deutschen/englischen Beschriftungen und Bereiche der Einstellungsabschnitte. Speichern lädt wie `/blockintegrations reload` neu. Ältere Cores überspringen das still. `towers.experience.player-radius` steht jetzt als Dezimalwert (`48.0`) in der Vorlage.

## 2.21.1-26.2 — 2026-10-18

- **Rechte:** alle Knoten mit deutscher Beschreibung, neuer Sammelknoten `blockintegrations.*`; `blockintegrations.admin` enthält jetzt `.use` und `.furnaces.manage-others`; `/blockintegrations` trägt `permission: blockintegrations.use`.
- **Tab-Vorschläge:** jede Ebene nach Rechten und Präfix (Groß-/Kleinschreibung egal) gefiltert, inklusive deutscher Aliase, `sortlock`/`chestpriority`, `web guide`; `towers remove` und Spielernamen nur für Admins. Test `BlockCommandsTest`.

## 2.21.0-26.2 — 2026-10-13

- **Machine diagnostics:** sneak-interaction reports the exact missing or wrong block and coordinates for incomplete registered towers/combos, using loaded chunks only and without repair; eligible players can preview bounded effect radii privately.
- **Tower controls:** owners/admins can filter eligible hostile targets, select a separate redstone-control input, and grant bounded per-player use, loot and upgrade rights. These controls and sharing apply to towers; redstone control is not extended to every combo or furnace.
- **Loot and furnace visibility:** towers can pause new attacks when their loot chest is full; furnace menus show a bounded recent activity window for accepted smelts, fuel-consuming burn events, output items and observed yield events. The output count includes multismelt and is not labeled as free bonus yield.
- **Personal routing controls:** sorters can protect selected main-inventory slots; players can prioritize personal sorter/vacuum chests. These preferences do not transfer ownership or grant access.
- **Chest protections and preview tools:** sorter/vacuum insertion checks the optional CityClaim container permission; legacy combo conversion can be previewed without applying it. Other claim providers are not automatically integrated.
- Machine configuration/schema updates preserve supported older data; personal slot and chest preferences remain per-player.

## Unreleased

- **Reichweitenvorschau:** Sneak + Rechtsklick zeigt den aktuellen Wirkungsradius von Angriffstürmen, Heilblock, Sortierer und Vakuum für vier begrenzte Partikeldurchläufe (etwa zwei Sekunden bei 20 TPS) nur dem berechtigten Betrachter.
- **Turm bei voller Lootkiste pausieren:** Standardmäßig beginnen keine neuen Angriffe, sobald alle Slots bis zum Stack-Limit belegt sind; nach dem Entnehmen von Loot geht es automatisch weiter. Bereits fliegende Geschosse können weiter Drops erzeugen, und nicht stapelbare Drops können auch vorher überlaufen.

## 2.20.5

- Die Turm-Bauanleitungen in Chat und Dialogen verwenden die Minecraft-Blocknamen in der Sprache des Spielers. Die Konsole behält lesbare englische Namen.

## 2.20.4

### Client-Mod
- **Stufen im Tooltip** tragbarer Blöcke (Client ab 0.9.0): Beim Abbauen bekommen Türme, Kombinationen und Öfen zusätzlich lesbare Zahl-Tags je Upgrade (`blockintegrations:lvl_damage` …); der Tooltip zeigt sie über Platzhalter (`ClientElement.data`) in der Sprache des Betrachters, z. B. „Schaden: 3“. Nicht vorhandene Upgrades blendet der Client aus; Start-Türme ohne Upgrades behalten die zwei Zeilen. Ältere Clients bekommen die bisherige Regel. Maßgeblich beim Setzen bleibt die binäre Nutzlast.
- **Toast „Lootkiste voll“ mit Aktion** „[Y] Beute öffnen“: dieselben Prüfungen wie der Chat-Knopf (Turm vollständig, Besitzer/Admin, Entfernung). Ohne Aktion-Unterstützung der bisherige Toast, bei ausgeblendeter UI (`ClientUi.visible`) gar keiner – die Chat-Meldung mit Knopf kommt immer.
- Marker werden beim Abschalten über `ClientUi.clear(player, "tower/")` entfernt.
- Tests über Cores öffentliche Test-API (`ClientUiTesting`) statt über Core-Interna.

## 2.20.3

### Client-Mod
- **Turm-Markierungen** (Core `web-client-ui`): Spieler mit Vystorm-Client sehen über ihren eigenen Türmen in der aktuellen Welt eine Wegmarke (nächste 16, bis 512 Blöcke, Typfarbe und Kopf-Symbol, abhebbare XP im Text). Neu gebaut, versetzt, aufgewertet oder abgebaut → spätestens nach einer Sekunde aktualisiert (gebündelt, nur der Text per PATCH), sonst alle fünf Sekunden abgeglichen; Weltwechsel und Abmelden räumen auf. Abschaltbar mit `client.tower-markers`.
- **Toast „Turm-Lootkiste voll“** zusätzlich zur Chat-Meldung (die ihre Knöpfe behält); nicht bei ausgeblendeter UI (H).
- **Tooltips für tragbare Blöcke**: Türme, Kombinationen und aufgewertete Öfen bekommen beim Abbauen zusätzlich das String-Tag `blockintegrations:portable_kind` (`tower:archer` …); Spieler mit Mod sehen dazu zwei Zeilen in ihrer eigenen Sprache (was es ist, was beim Setzen übernommen wird) – die Lore bleibt in der Sprache dessen, der den Block abgebaut hat. 16 Regeln je Spieler, gesendet beim Handshake. Abschaltbar mit `client.portable-tooltips`. Ältere tragbare Items ohne Tag zeigen nur die Lore.
- Ohne Mod oder mit abgeschaltetem Client-Kanal (`web.client.enabled: false`) ändert sich nichts. Texte in `lang/*.yml` (`client.*`).
- Tests: Marker/Patch/Toast/Tooltip-Regeln gegen Core's Client-Schema, Marker-IDs, Beschriftung mit und ohne XP.

## 2.20.2
- Start-Prüfung über die Capability-Flags von Vystorm Core (`CoreApi.has(...)`: settings, chat-actions, regions, task-groups, localization) statt Reflection-Proben einzelner Methoden/Klassen; die API kommt aus `CoreApi.api()` statt `main.getApi()`. Mindestversion bleibt Core 0.22.0.
- Angriffsschleife der Türme und Heil-Aura laufen in einer Core-`TaskGroup`: Beim Abschalten werden genau diese Timer beendet, Fehler in einem Tick werden gedrosselt geloggt.
- `towers.yml`, `combos.yml`, `furnaces.yml` und die offene Skills-XP werden über Core-`AtomicFiles` geschrieben (Temp-Datei, fsync, atomarer Austausch); `combos.yml` und die Skills-XP-Datei wurden vorher direkt überschrieben. Dateiformat unverändert.
- **Neues Speicherformat:** Türme, Kombinationen, Ofen-Index und offene Skills-XP liegen als Core-`VersionedStore` in `towers.json`, `combos.json`, `furnaces.json` und `pending-furnace-skill-xp.json` (Schema-Umschlag, atomar geschrieben, unlesbare Dateien werden gesichert und nie überschrieben; Turm-Loot als Core-kodierte Items). Die alten `*.yml`-Dateien werden **nicht automatisch importiert**.
- Mindestversion Vystorm Core 0.26.4 (Capabilities `atomic-files`, `tutorials`, `versioned-store`, `task-group-one-shots`); alle Einmal-Tasks (verzögerte Prüfungen, gebündeltes Speichern, XP-Zustellung) und der Web-Flush laufen in Core-`TaskGroup`s. Konsolenmeldungen über Core-`DebugMessages`.
- Eigenes `/vadmin`-Modul `blocks` (ersetzt den Core-Katalogeintrag): Status, Türme (alle, eines Spielers, in der Nähe), anvisierten Turm entfernen, Neu laden, Knopf zum nativen Menü.

## 2.20.1
- Behoben: Türme griffen keine SpawnDirector-Mobs an. Diese tragen einen Namen („[Lv. 5] Zombie“, „[Elite] …“) und fielen deshalb unter `towers.targeting.ignore-named-mobs`. Mobs mit SpawnDirector-Markierung (`spawndirector:director_mob`, `spawndirector:structure_slot`) gelten jetzt trotz Namen als Ziel; Haustiere und andere benannte Mobs bleiben ausgenommen.

## 2.20.0-26.2

- **Optional web page "Blocks & Towers"** (Vystorm Core 0.24.0+ with `web.enabled`, module `blocks`): my towers,
  furnaces and block combos with type, levels, location and state, live refresh; upgrades from the web through the same
  check-and-apply code as the in-game dialogs (online only, owner/admin, chunk must be loaded); tower loot chest view and
  taking items within 64 blocks (same rule as the loot chat button); building guides with block diagrams and upgrade
  tables from the plugin's definitions; admin list of all structures. In-game overlay with the Vystorm client mod.
- "🌐 Open in web" button in `/bi menu` and the upgrade dialogs, new command `/bi web [guide]` (only shown/active while
  Core's web platform runs; without it nothing changes).
- Block combos remember their builder (`owner` in `combos.yml`); older combos are claimed on the first sneak +
  right-click (message shown). Only used by the web page.
- New `furnaces.yml`: index of owned furnaces (filled on place, claim, upgrade, use and when chunks load; pruned on
  break/explosion) so the page can list furnaces without loading chunks.
- Fix: claiming an existing furnace with sneak + right-click is now written to the block (the claim could be lost).
- Fix: default upgrade cost lists no longer fail when `config.yml` has no cost list.

## 2.19.0-26.2

First public release (SpigotMC, free, MIT license).

- **Localization:** all player and admin texts moved to `lang/en.yml` and `lang/de.yml` (Vystorm Core 0.22.0
  localization). Every player sees the plugin in their own language (Minecraft language or the choice in
  `/vsettings`); unknown languages fall back to English. New config key `language: auto` (or a fixed code such as
  `en`/`de`). Admins can edit the files in `plugins/BlockIntegrations/lang/` or add languages; `/bi reload` reloads them.
- English tutorial (`tutorial_en.yml`); German players keep `tutorial.yml`.
- Menus, dialogs, chat buttons, server settings (labels and descriptions), item lore of moved blocks and the tower
  loot chest title are localized per viewer.
- `plugin.yml` command/permission descriptions and console messages in English.
- The sorter/vacuum message texts are no longer read from `config.yml` (`messages.success`, `messages.nothing`,
  `vacuum.messages.chests|inventory|nothing`); they are now `sorter.*` / `vacuum.*` in the language files. The
  `show-when-nothing-moved` switches stay in `config.yml`. Old keys in an existing config are ignored.
- `config.yml` comments translated to English (existing configs are not rewritten; values are unchanged).
- Requires **Vystorm Core 0.22.0** or newer; with an older Core the plugin disables itself with a clear console message.
- Removed the unused chest-style upgrade menus of towers, furnaces and combos (replaced by native dialogs since 2.17.0).
- Tutorial/README: removed outdated mcMMO references; furnace skill XP comes from the optional Vystorm Skills plugin.
- MIT `LICENSE` added (also inside the jar).

## 2.18.0-26.2

- Clickable chat actions for tower, furnace and combo messages (Vystorm Core 0.8.0 `ChatActions`).

## 2.17.0-26.2

- Block menus moved to native Core dialogs; server settings section.

## 2.16.0-26.2

- Furnaces award smelting/cooking XP through Vystorm Skills instead of mcMMO; mcMMO dependency removed.

## 2.15.0-26.2

- Towers: owner and town access, kill XP only with a player nearby, line of sight and target filters, protected
  structures, admin commands.
