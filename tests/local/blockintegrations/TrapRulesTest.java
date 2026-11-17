package local.blockintegrations;

import Vystorm.vystorm_core.region.Region;
import Vystorm.vystorm_core.region.RegionService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TrapRulesTest {
    @Test void sharedCooldownSurvivesModeChangesAndStateRoundTrip() {
        var entry = new TrapManager.Entry(UUID.randomUUID() + ",0,64,0", UUID.randomUUID(), BlockFace.UP,
                TrapStructure.Type.THROW, true, 15, 0).fired(1000);
        assertFalse(TrapManager.ready(entry, 15999));
        assertTrue(TrapManager.ready(entry, 16000));
        var changed = entry.mode(false, 10);
        assertFalse(TrapManager.ready(changed, 11000), "changing settings cannot shorten a running cooldown");
        var gson = new com.google.gson.Gson();
        var restored = gson.fromJson(gson.toJson(new TrapManager.State(List.of(changed))), TrapManager.State.class).traps().getFirst();
        assertEquals(changed, restored);
        assertEquals(26000, restored.fired(16000).readyAt());
    }

    @Test void playersPetsPassiveNeutralAndProtectedEntitiesAreExcluded() {
        assertFalse(TrapManager.hostile(mock(Player.class)));
        assertFalse(TrapManager.hostile(mock(Cow.class)));
        assertFalse(TrapManager.hostile(mock(Wolf.class)));
        Zombie zombie = mock(Zombie.class);
        when(zombie.isValid()).thenReturn(true);
        when(zombie.getScoreboardTags()).thenReturn(Set.of());
        assertTrue(TrapManager.hostile(zombie));
        when(zombie.getScoreboardTags()).thenReturn(Set.of("no-traps"));
        assertFalse(TrapManager.hostile(zombie));
        when(zombie.getScoreboardTags()).thenReturn(Set.of());
        when(zombie.isInvulnerable()).thenReturn(true);
        assertFalse(TrapManager.hostile(zombie));
        Enderman neutral = mock(Enderman.class);
        when(neutral.isValid()).thenReturn(true);
        when(neutral.getScoreboardTags()).thenReturn(Set.of());
        assertFalse(TrapManager.hostile(neutral));
        when(neutral.isScreaming()).thenReturn(true);
        assertTrue(TrapManager.hostile(neutral));
    }

    @Test void protectedRegionsAndCityBoundariesRejectEffects() {
        RegionService regions = mock(RegionService.class);
        Location origin = new Location(mock(World.class), 0, 64, 0), target = origin.clone().add(1, 0, 0);
        when(regions.at(any())).thenReturn(List.of());
        when(regions.top(any(), eq("city"))).thenReturn(Optional.empty());
        try (var service = mockStatic(RegionService.class)) {
            service.when(RegionService::get).thenReturn(regions);
            assertTrue(TrapManager.allowedRegion(RegionService.get(), origin, target));
            Region city = mock(Region.class); when(city.id()).thenReturn("other-city");
            when(regions.top(target, "city")).thenReturn(Optional.of(city));
            assertFalse(TrapManager.allowedRegion(RegionService.get(), origin, target));
            when(regions.top(origin, "city")).thenReturn(Optional.of(city));
            assertTrue(TrapManager.allowedRegion(RegionService.get(), origin, target));
            for (String kind : List.of("arena", "arena-zone", "spawn", "safe", "market")) {
                Region protectedArea = mock(Region.class); when(protectedArea.kind()).thenReturn(kind);
                when(regions.at(target)).thenReturn(List.of(protectedArea));
                assertFalse(TrapManager.allowedRegion(RegionService.get(), origin, target), kind);
            }
        }
    }
}
