# BlockIntegrations – Anleitung (Deutsch)

_English: the in-game guide (`tutorial_en.yml`) and the [README](README.md)._

Öffne `/vmenu`, wähle **Tutorials** und danach **BlockIntegrations · Anleitung**. Direkt geht es mit `/vmenu tutorials`; der direkte Schlüssel `/vmenu blockintegrations:tutorial` bleibt ebenfalls verfügbar. Für den Eintrag brauchst du `blockintegrations.use`.

Bei einem unvollständigen Aufbau zeigt Schleichen + Rechtsklick auf den wahrscheinlichen Steuerblock mit der passenden Berechtigung fehlende/falsche Blöcke samt Koordinaten. Die Prüfung liest nur geladene Chunks und verändert oder repariert nichts.

## Start und Übersicht

Die sechs Kombos, Türme und erweiterbaren Öfen funktionieren unabhängig. Stelle die beschriebene Struktur auf und nutze den Funktionsblock oder die beschriebene Bewegung. Ausbaubare Objekte öffnen ihr Menü mit Schleichen + Rechtsklick; Upgrades kosten echte XP-Punkte. Die Übersicht liegt unter `/vmenu` → **Bauen & Technik** → **Blöcke und Upgrades** (oder `/bi`); im Chat weiterhin `/bi combos`, `/bi towers` und `/bi furnaces`. Jede Anleitung braucht ihre Fachberechtigung. Upgrade-Menüs führen mit „← Zurück“ zur Übersicht und mit „⌂ Hauptmenü“ zu `/vmenu`.

## Inventar-Sortierer — `blockintegrations.combos`

Setze eine Gold-Druckplatte auf einen beliebigen vollen Kupfer-Baublock. Stelle passende Kisten mit je einem Musteritem in der Nähe bereit. Betritt die Platte: Slots 9–35 werden sortiert, Hotbar, Rüstung und Nebenhand bleiben unberührt. Schleichen + Rechtsklick öffnet Reichweite und Cooldown; Standardreichweite: 10 Blöcke.

## Item-Vakuum — `blockintegrations.combos`

Setze eine Eisen-Druckplatte auf einen beliebigen vollen Kupfer-Baublock. Vorsortierte Kisten in der Nähe nehmen Items auf. Betritt die Platte. Nur wenn im Suchbereich keine Kiste steht, wandern die Items ins Hauptinventar. Schleichen + Rechtsklick öffnet Reichweite und Cooldown; Standard: 20 Blöcke Radius und 20 Ticks.

## Launcher und Slimefeld — `blockintegrations.combos`

Setze einen Schleimblock auf einen Redstoneblock und springe darauf, um dich in Blickrichtung zu beschleunigen. Für ein Feld lege neun Schleimblöcke als waagerechtes 3×3 auf gleicher Höhe; Redstone liegt nur unter der Mitte. Alle neun nutzen Stärke und Cooldown der Mitte. Schleichen + Rechtsklick auf einen Feldblock öffnet das gemeinsame Upgrade-Menü. Standardstärke 1,70 ergibt waagerecht etwa 15 Blöcke Reichweite; Cooldown: 20 Ticks.

## Sprint-Booster — `blockintegrations.combos`

Setze Eis, Packeis oder Blaueis direkt auf einen Redstoneblock und sprinte darüber. Schleichen + Rechtsklick öffnet Upgrades für Stärke oder Cooldown. Standardwerte: Stärke 1,15 und 10 Ticks Cooldown. Das Standardprofil verhindert, dass Booster-Eis schmilzt.

## Float-Lift und Heilblock — `blockintegrations.combos`

Float-Lift: Setze weiße Terrakotta auf einen vollen Kupfer-Baublock auf Redstone, springe und berühre die Terrakotta. Er startet senkrecht und gibt Schwebekraft. Schleichen + Rechtsklick baut Stärke, Dauer oder Cooldown aus; Basis: 5 Sekunden und 60 Ticks Cooldown.

Heilblock: Setze einen Honigblock direkt auf Redstone. Er heilt in seiner Aura alle 3 Sekunden. Nach Schaden pausiert die Heilung mindestens 5 Sekunden. Reichweite, Heilmenge und Cooldown sind ausbaubar.

## Kombo-Upgrades und Transport — `blockintegrations.combos.upgrade`

Upgradepfade: Sortierer/Vakuum Reichweite und Cooldown; Launcher/Booster Stärke und Cooldown; Float-Lift Stärke, Dauer und Cooldown; Heilblock Reichweite, Heilmenge und Cooldown. Schleiche + Rechtsklick öffnet das Menü; wähle den Pfad und bestätige den XP-Preis. Es gibt bis zu 10 Stufen. Reichweite steigt je Stufe um 1 Block, Float-Dauer um 0,5 Sekunden; Cooldowns sinken je Stufe um 5 %, mindestens jedoch auf 5 Ticks. Beim Abbau enthält das gedroppte Funktionsitem seine Upgrades.

## Türme: Aufbauprinzip — `blockintegrations.towers`

Türme bestehen von oben nach unten aus Kopf, Arbeitsblock und Fundament. Auf Fundamenthöhe stehen vier Ringblöcke kreuzförmig nach Norden, Süden, Osten und Westen. Der Kopf ist Angriffspunkt, Upgrade-Menü und Lootzugang. Türme greifen feindliche Mobs in geladenen Chunks an. Mobdrops und Kill-XP des zuletzt treffenden Turms landen in seiner virtuellen Lootkiste.

## Türme: Starttypen — `blockintegrations.towers`

Diese günstigen Türme haben keine Upgrades. Zombie-Wachturm: Zombiekopf → Werkbank → Bruchstein, mit vier Eichenbrettern im Kreuz; 2 Schaden, 8 Blöcke, 30 Ticks. Rekrutenturm: Spielerkopf → Fass → Stein, mit vier Bruchsteinblöcken im Kreuz; 3 Schaden, 9 Blöcke, 26 Ticks. Schleichen + Rechtsklick auf den Kopf öffnet die Lootkiste.

## Türme: Schütze und Kanone — `blockintegrations.towers`

Schützenturm: Skelettschädel → Befiederungstisch → beliebiger voller Kupfer-Baublock, mit vier Steinziegeln im Kreuz. Basis: 4 Schaden, Reichweite 14, 20 Ticks. Kanonenturm: Creeperkopf → Werfer → Hochofen, mit vier Obsidianblöcken im Kreuz. Er verursacht Flächenschaden ohne Blockzerstörung; Basis: 8 Schaden, Reichweite 11, 40 Ticks. Schleichen + Rechtsklick öffnet Loot und Upgrades.

## Türme: Flamme, Piglin, Drache — `blockintegrations.towers`

Flammenturm: Witherskelettschädel → Magmablock → Netherziegel; vier polierte Schwarzsteinziegel im Kreuz. 5 Schaden, 10 Reichweite, 24 Ticks; entzündet Ziele 3 Sekunden. Piglin-Goldturm: Piglinkopf → Schmiedetisch → Goldblock; vier polierte Schwarzsteinziegel. 6 Schaden, 12 Reichweite, 16 Ticks. Drachenturm: Drachenkopf → Respawn-Anker → Purpurblock; vier Endsteinziegel. 12 Schaden, 16 Reichweite, 50 Ticks.

## Turm-Upgrades und Loot — `blockintegrations.towers.upgrade`

Schleiche + Rechtsklick auf den Turmkopf öffnet das Menü. Schaden, Reichweite und Tempo reichen bis Stufe 10; Looting höchstens bis Stufe 3 und kann geeignete stapelbare Drops zusätzlich erzeugen. „Lootkiste öffnen“ nimmt Items heraus; „Kill-XP entnehmen“ zahlt XP aus. Beim Abbau bleiben Upgrades am Kopf erhalten und der Kisteninhalt wird gedroppt.

## Erweiterbare Öfen — `blockintegrations.furnaces`

Unterstützt werden Ofen, Hochofen und Räucherofen. Beim Platzieren wirst du fester Besitzer. Einen alten besitzerlosen Ofen beansprucht Schleichen + Rechtsklick für dich. Öffne den Ofen normal zum Schmelzen; Schleichen + Rechtsklick öffnet Upgrades. Mit Vystorm Skills gibt auch Trichterbetrieb dem Besitzer Schmelz-/Koch-XP. Offline-XP werden gespeichert und beim Login übertragen. Besitzer verwalten den Ofen; für fremde Öfen ist `blockintegrations.furnaces.manage-others` nötig.

## Ofen-Upgrades und Transport — `blockintegrations.furnaces.upgrade`

Öffne per Schleichen + Rechtsklick das Ofenmenü und bezahle echte XP-Punkte. Bis Stufe 10 gibt es Schmelztempo, Brennstoffeffizienz, Bonusertrag und Multismelt. Multismelt verarbeitet je Stufe ein zusätzliches Input-Item pro Brennvorgang, begrenzt durch Input und Ausgabeslot. XP-Boost reicht bis Stufe 5 und steigert gespeicherte Vanilla-Ofen-XP sowie Schmelz-/Koch-XP (Vystorm Skills) pro Stufe um 20 %. „Gespeicherte XP entnehmen“ zahlt Vanilla-XP aus. Beim Abbau bleiben Upgrades, Besitzer und gespeicherte Zusatz-XP im Ofenitem erhalten.

Das Menü zeigt zusätzlich beobachtete Schmelzvorgänge, verbrauchte Brennstoff-Items, verarbeitete Ausgabe-Items (einschließlich Multismelt) und ausgelöste Ertragsbonus-Items von ungefähr den letzten 60 Sekunden. Diese Zähler sind flüchtig und werden bei Neustart oder Chunk-Entladung zurückgesetzt. Tempo- und Ertragsvorschauen zeigen erwartete konfigurierte Werte.

## Werkstationen und Turmeffekte (2.22.0)

Schleichen + Rechtsklick auf Braustand, Lagerfeuer oder Seelenlagerfeuer oeffnet das Upgrade-Menue. Braustaende bekommen je Stufe +12% Tempo und +10% Brennstoffleistung; Lagerfeuer +12% Tempo und +2,5 Prozentpunkte Chance auf ein Extra-Item. Standard: zehn Stufen, Kosten je naechster Stufe 100 * Stufe zum Quadrat in XP-Punkten. Nur Besitzer oder Administratoren koennen Upgrades kaufen, innerhalb von acht Bloecken. Normale Benutzung bleibt offen. Passende Blockdrops behalten Besitzer und Upgrades; Lagerfeuer benoetigen Behutsamkeit. Anleitung: /bi workstations. Einstellungen: workstations in config.yml oder im Core-Konfigurationseditor.

Alle Turmtypen erhalten eigene Abschuss- und Trefferpartikel mit kurzen Animationen. Kanonen haben eine gebogene Spur, Kanonen- und Flammentreffer wachsende Ringe. Trefferpartikel erscheinen nur bei wirklichem Lebens- oder Absorptionsschaden. Angriffstempo und Schaden bleiben unveraendert. Abschaltbar mit towers.visual-effects. Die Effekte nutzen das gemeinsame Partikellimit und die Animationsplanung des Core.
