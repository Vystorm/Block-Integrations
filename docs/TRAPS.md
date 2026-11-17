# Monsterfallen (2.24.0)

Eine physische 3×3-Fläche aus acht Obsidianblöcken mit einem Kern in der Mitte:

```text
O O O    O = Obsidian
O K O    K = Fallenkern
O O O
```

Auf die Vorderseite **eines Obsidian-Rahmenblocks** kommt ein beliebiger Knopf. Der Rahmen kann waagerecht im Boden oder an der Decke liegen oder senkrecht an einer Wand stehen. Beim Kolben müssen Kolbenkopf und Knopf in dieselbe Richtung zeigen.

| Kern | Wirkung |
| --- | --- |
| Kolben oder klebriger Kolben | Wirft feindliche Monster in Kolbenrichtung. An Wänden mit zusätzlichem Aufwärtsimpuls. |
| Schleimblock | Schleuderfalle: wirft Monster in einem Bogen; am Boden/der Decke vom einzelnen Rahmenknopf weg, an der Wand nach vorne und oben. |
| Eisenblock | Stachelfalle: standardmäßig 6 Schadenspunkte pro getroffenem Monster. |
| Honigblock | Teerfalle: Verlangsamung III für 5 Sekunden. |

Der erste Rechtsklick auf den Knopf registriert die vollständige Falle auf diesen Spieler. Standard: **Automatik an, 15 Sekunden Cooldown**. Es gibt keine Suche nach unbekannten Fallen in der Welt. Bestehende Bauten lassen sich so ebenfalls aktivieren.

Die **Schleuderfalle** benötigt genau einen Knopf auf der Vorderseite des Rahmens. Bei Boden- und Deckenmontage markiert dieser die Rückseite der Schleuder: ein Knopf im Norden schleudert nach Süden; Eckpositionen schleudern diagonal mit derselben horizontalen Stärke. Bodenfallen geben einen Aufwärtsimpuls, Deckenfallen einen Abwärtsimpuls. An Wänden zählt die Vorderseite des Rahmens. Mehrere Knöpfe machen den Aufbau ungültig. Wurfweite und Landepunkt entstehen durch normale Minecraft-Physik und Hindernisse; es gibt keine Teleportation und keinen zusätzlichen direkten Schaden.

Admin-Einstellungen: `traps.sling-power` (Standard 2.0, Bereich 0.5–3.0) und `traps.sling-lift` (Standard 1.1, Bereich 0.2–2.0), auch im Fallen-Bereich der Server-Einstellungen. Schleuderfallen verwenden dieselben Schutzprüfungen, Zielregeln und gespeicherten 10/15-Sekunden-Cooldowns wie die übrigen Fallen.

- Knopf rechtsklicken: manuell auslösen.
- Shift + Rechtsklick auf den Knopf: Menü für Automatik an/aus und 10/15 Sekunden Cooldown.
- Manuell und automatisch teilen sich den Cooldown. Änderungen der Einstellungen verkürzen keinen bereits laufenden Cooldown.
- Abmelden über das Menü ist nach Ablauf des Cooldowns möglich. Ein späterer Knopfdruck kann die Falle neu registrieren.
- Anleitung: `/blockintegrations traps`, `/blockintegrations fallen` oder das Block-Menü.

Der Wirkbereich ist 3×3 breit und reicht drei Blöcke vor die Falle. Jeder Gegner benötigt freie Sicht zur Vorderseite. Es werden nur feindliche, lebende und verwundbare Monster getroffen: keine Spieler, normalen Tiere oder gezähmten Tiere; friedliche Endermen/Piglins/Zombie-Piglins werden ignoriert. Eigene benannte Monster können getroffen werden. Die Tags `no-traps` und `npc` schließen eine Entität aus.

Arena-, Spawn-, Safe- und Marktregionen sind ausgeschlossen. Zwischen verschiedenen Städten oder zwischen Stadt und Wildnis wirken Fallen nicht. Ein öffentliches, abbrechbares `TrapTriggerEvent` erlaubt weiteren Schutzplugins, auch Werfen und Verlangsamen vor der Wirkung abzulehnen. Stachelschaden verwendet zusätzlich normale Bukkit-Schadensereignisse; bei online befindlichem Besitzer wird dieser als Schadensquelle verwendet. Normale Unverwundbarkeitsphasen werden nicht zurückgesetzt.

Nur Eigentümer und Admins bedienen oder konfigurieren eine registrierte Falle. Abgebrochene Interaktionen und die zweite Hand lösen keine Registrierung/Aktion aus. Maximal 32 Fallen je Spieler und 2048 insgesamt. Ein gemeinsamer Task prüft alle 10 Ticks nur registrierte, geladene Fallen. Die Strukturprüfung lädt keine Chunks; fehlender Knopf/Rahmen pausiert die Falle. Wird der Kern entfernt oder durch einen anderen Fallentyp ersetzt, wird die Registrierung nach Ablauf des Cooldowns entfernt. Redstone kann den registrierten Kolben nicht ausfahren lassen und damit Blöcke verschieben.

Besitz, Typ, Richtung, Automatik, Cooldown und nächster erlaubter Auslösezeitpunkt werden über den bestehenden Core-VersionedStore in `traps.json` gespeichert. Es gibt keine zusätzlichen Drop- oder Belohnungswürfe.

Server-Konfiguration über den Fallen-Bereich unter `/vsettings server` oder den Core-Konfigurationseditor: `traps.enabled`, `traps.default-automatic`, `traps.default-cooldown-seconds` (10/15), `traps.throw-power` (0.5–3.0) und `traps.spike-damage` (0–40). Änderungen gelten nach dem Speichern sofort; Standardmodus und Standardcooldown betreffen nur neue Fallen. Berechtigung: `blockintegrations.traps` (standardmäßig an, auch in Admin-/Gesamtrechten enthalten). Siehe [Installation](TRAPS-INSTALLATION.md).

Automatische Tests decken alle drei Typen in sechs Ausrichtungen, Knopferkennung, unvollständige/ungeladene Aufbauten, Wirkbereich, Zielauswahl, Regionsgrenzen, Cooldown-Persistenz und den gemeinsamen Ablauf von Automatik/Knopf inklusive abgebrochener Wirkungen ab. Physikgefühl und Darstellung müssen noch auf dem Minecraft-Server geprüft werden.
