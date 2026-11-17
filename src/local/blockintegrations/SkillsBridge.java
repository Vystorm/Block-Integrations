package local.blockintegrations;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Optionale Anbindung an Vystorm Skills über den ServicesManager (ohne Compile-Abhängigkeit). Fehlt das Plugin,
 * liefern alle Methoden neutrale Werte und Ofen-XP bleiben vorgemerkt.
 */
final class SkillsBridge {
    /** Besitzer-Schlüssel, über den Vystorm Skills Ofen-Passive dem Spieler zuordnet. */
    static final NamespacedKey OWNER_KEY = new NamespacedKey("vystormskills", "owner");
    private static final String API = "at.esoren.skills.api.VystormSkillsApi";

    private SkillsBridge() {
    }

    private static Object api() {
        for (Class<?> service : Bukkit.getServicesManager().getKnownServices()) {
            if (!service.getName().equals(API)) {
                continue;
            }
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(service);
            return provider == null ? null : provider.getProvider();
        }
        return null;
    }

    static boolean available() {
        return api() != null;
    }

    /** Grund-XP je geschmolzenem Item; 0, wenn Vystorm Skills fehlt oder das Ergebnis keine XP gibt. */
    static double furnaceXp(Material furnace, Material result) {
        Object api = api();
        if (api == null) {
            return 0.0D;
        }
        try {
            String discipline = discipline(api, furnace, result);
            Method table = api.getClass().getMethod("tableXp", String.class, String.class);
            double value = (double) table.invoke(api, discipline, result.name());
            if (value <= 0.0D) {
                value = (double) table.invoke(api, discipline + "-default", "");
            }
            return Math.max(0.0D, value);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return 0.0D;
        }
    }

    static String discipline(Material furnace, Material result) {
        Object api = api();
        if (api == null) {
            return furnace == Material.SMOKER ? "cooking" : "smelting";
        }
        try {
            return discipline(api, furnace, result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return furnace == Material.SMOKER ? "cooking" : "smelting";
        }
    }

    private static String discipline(Object api, Material furnace, Material result) throws ReflectiveOperationException {
        return (String) api.getClass().getMethod("furnaceDiscipline", Material.class, Material.class).invoke(api, furnace, result);
    }

    /** Vergibt XP; liefert, ob Vystorm Skills erreichbar war (sonst bleibt die XP vorgemerkt). */
    static boolean award(Player player, String discipline, double amount) {
        Object api = api();
        if (api == null) {
            return false;
        }
        try {
            api.getClass().getMethod("awardXp", Player.class, String.class, double.class, String.class)
                    .invoke(api, player, discipline, amount, "blockintegrations-furnace");
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return false;
        }
    }
}
