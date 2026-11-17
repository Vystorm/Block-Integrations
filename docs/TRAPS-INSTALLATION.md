# Fallenpaket installieren

Version: BlockIntegrations 2.24.0-26.2. Die Fallen benötigen kein Resourcepack und kein zusätzliches Plugin neben dem vorhandenen Vystorm Core.

1. Server stoppen und die bisherige BlockIntegrations-JAR sowie den Ordner `plugins/BlockIntegrations` sichern.
2. Die bisherige BlockIntegrations-JAR durch `BlockIntegrations-2.24.0-26.2.jar` ersetzen. Im Pluginordner nur eine BlockIntegrations-JAR behalten.
3. Vorhandene Konfiguration, Sprachdateien und Zustandsdateien behalten. Neue Konfigurations-Standardwerte werden beim Start ergänzt; eigene Werte werden beibehalten. Neue Texte stehen durch die eingebauten Sprachressourcen bereit.
4. Server starten. `/bi traps` und `/bi menu` zeigen die Anleitung. Unter `/vsettings server` → BlockIntegrations → Fallen oder im Core-Konfigurationseditor stehen die Einstellungen bereit.
5. Eine kleine Testfalle bauen und per Knopf registrieren. Manuellen Start, Automatik, 10/15 Sekunden Cooldown und Ausrichtung ingame prüfen.

## Standardwerte

```yaml
traps:
  enabled: true
  default-automatic: true
  default-cooldown-seconds: 15
  throw-power: 2.0
  sling-power: 2.0
  sling-lift: 1.1
  spike-damage: 6.0
```

Die Standard-Automatik und Standard-Abklingzeit gelten für **neu registrierte** Fallen. Bereits gespeicherte Fallen behalten ihre Einstellung. Die globalen Werte für Aktivierung, Wurfstärke und Stachelschaden gelten nach dem Speichern/Reload unmittelbar.

`blockintegrations.traps` ist standardmäßig für Spieler aktiviert und Bestandteil von `blockintegrations.*` sowie `blockintegrations.admin`. Die Einstellung einer Falle bleibt auf Eigentümer und Admins beschränkt. Bei ausdrücklich verweigerten Berechtigungen in einem Rechteplugin die betreffende Gruppe prüfen.

`traps.json` speichert die Fallen inklusive Eigentümer und Cooldown; diese Datei in die normale Serversicherung aufnehmen. Keine separate Datenbank-Migration erforderlich.

Ein Rückwechsel zur vorherigen JAR ist durch Stoppen und Austausch möglich. `traps.json` dabei behalten; ältere Versionen ignorieren diese Datei.
