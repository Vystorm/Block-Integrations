package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.bukkit.entity.ExperienceOrb;
import org.junit.jupiter.api.Test;

class DeathOrbGuardTest {
    private static final UUID WORLD = new UUID(0, 1), NETHER = new UUID(0, 2);

    @Test
    void orbsNearARecentDeathStayForFiveMinutes() {
        DeathOrbGuard guard = new DeathOrbGuard();
        guard.died(WORLD, 100.5, 64, -20.5, 1_000L);
        assertTrue(guard.protects(WORLD, 100.5, 64, -20.5, 1_000L));
        assertTrue(guard.protects(WORLD, 105, 66, -16, 2_000L), "orbs scatter and drift a few blocks");
        assertFalse(guard.protects(WORLD, 110, 64, -20.5, 2_000L), "farther than 8 blocks");
        assertFalse(guard.protects(NETHER, 100.5, 64, -20.5, 2_000L), "other world");
        assertTrue(guard.protects(WORLD, 100.5, 64, -20.5, 1_000L + DeathOrbGuard.MILLIS - 1));
        assertFalse(guard.protects(WORLD, 100.5, 64, -20.5, 1_000L + DeathOrbGuard.MILLIS));
        assertTrue(guard.isEmpty(), "expired deaths are forgotten");
    }

    @Test
    void manyDeathsStayBounded() {
        DeathOrbGuard guard = new DeathOrbGuard();
        for (int i = 0; i < DeathOrbGuard.MAX_PLACES + 50; i++) guard.died(WORLD, i * 100, 64, 0, i);
        assertFalse(guard.protects(WORLD, 0, 64, 0, 1_000L), "the oldest made room");
        assertTrue(guard.protects(WORLD, (DeathOrbGuard.MAX_PLACES + 49) * 100, 64, 0, 1_000L));
    }

    @Test
    void orbsFromAPlayersDeathAreNeverCollected() {
        assertTrue(XpCollector.deathDrop(ExperienceOrb.SpawnReason.PLAYER_DEATH));
        assertFalse(XpCollector.deathDrop(ExperienceOrb.SpawnReason.ENTITY_DEATH), "mob farm XP is what collectors are for");
        assertFalse(XpCollector.deathDrop(ExperienceOrb.SpawnReason.CUSTOM));
        assertEquals(8.0D, DeathOrbGuard.RADIUS);
    }
}
