# Amboss-Upgrades (2.25.0)
Rechtsklick auf einen Amboss öffnet ein 27-Slot-Menü. Links unten (Slot 18): **Amboss verbessern**. Mitte: **Amboss benutzen** öffnet die normale Minecraft-Ambossoberfläche. Der Vanilla-Amboss hat keinen zusätzlichen Knopfplatz; deshalb liegt der Upgrade-Knopf im vorgeschalteten Menü. Schleichen kann weiterhin das VystormItems-Schmiedemenü öffnen.

- Effizienz, 10 Stufen: jeweils 5% weniger XP-Level für Ambossvorgänge, maximal 50%; aufgerundet, bei positiven Kosten mindestens 1 Level.
- Verzauberungen, 5 Stufen: normales Maximum + Stufe. Gleiche Stufen kombinieren sich um eins höher. Vanilla-Kompatibilität und Ausschlüsse bleiben erhalten. Ab Stufe 1 entfällt „Zu teuer!“.
- Haltbarkeit, einmalig: kein Ambossverschleiss durch Benutzung. Explosionen und Fallschaden bleiben Minecraft-Mechaniken.

Upgrades kosten echte XP-Punkte über Core: standardmäßig 100 × neue Stufe²; Haltbarkeit einmalig 2500. Creative ist kostenlos. Nur Besitzer oder Admins dürfen innerhalb von 8 Blöcken kaufen. Fehlgeschlagene Speicherung erstattet den Kauf. Upgrades und Besitzer bleiben beim normalen Abbau und bei fallenden Ambossen erhalten; Speicherung in anvils.json.

Einstellungen: /vsettings server → BlockIntegrations → Ambosse; ebenso im Core-Konfigurationseditor. config.yml: anvils.enabled, anvils.base-xp-cost, anvils.unbreakable-xp-cost. Berechtigung blockintegrations.anvils ist standardmäßig aktiv.

## Items-Bridge
Gemeinsam installieren: Core 0.39.0, VystormItems 0.8.1 und BlockIntegrations 2.25.0 (Java 25, Paper). Andere Plugins brauchen keine direkte Items-Abhängigkeit. ItemServices bereitet Kopien vor; Items signiert über ItemService und behält UUID, Stats, Sockel, Bindung und Haltbarkeit. Ledger-Bestätigung erfolgt erst nach Entnahme. Ohne passende Bridge wird für verwaltete Items kein Upgrade-Ergebnis ausgegeben. Vanilla-Items benötigen keinen Items-Provider.

## Ingame-Abnahme nach Installation
1. Menü öffnen, unten links Effizienz kaufen; XP-Punkte prüfen. Amboss benutzen, Reparatur und Umbenennung vergleichen.
2. Verzauberungsstufe 1 kaufen; zwei Schärfe-V-Items/Bücher zu VI kombinieren, unvereinbare Verzauberungen prüfen und Vorgang über 39 Level testen.
3. Haltbarkeit kaufen, mehrfach benutzen; abbauen, wieder platzieren und fallen lassen, Boni prüfen.
4. Vystorm-Waffe mit Sockel/Bindung reparieren und verzaubern; beide Entnahmewege (Cursor, Shift-Klick) und volles Inventar prüfen.
5. Neustart, anderer Besitzer, abgebrochene Entnahme und gesperrtes Gebiet prüfen.

Builds und automatisierte Tests ersetzen diese Server-Abnahme nicht. Installation und Ingame-Abnahme sind noch offen.
