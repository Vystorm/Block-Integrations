package local.blockintegrations;

import Vystorm.vystorm_core.API;
import Vystorm.vystorm_core._coreMethods.playerHandler.PlayerHandlerInterface;
import Vystorm.vystorm_core._advancedMethods.particleHandler.ParticleInterface;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreIntegrationTest {
    private Object manager(Class<?> type, BlockIntegrations plugin) throws Exception {
        Object manager = mock(type, CALLS_REAL_METHODS);
        var field = type.getDeclaredField("plugin"); field.setAccessible(true); field.set(manager, plugin);
        return manager;
    }

    @ParameterizedTest @ValueSource(classes = {TowerManager.class, ComboManager.class, FurnaceManager.class})
    void upgradeCostsUseCurrentCoreHandlerAndPreserveFreeCreativeActions(Class<?> type) throws Exception {
        BlockIntegrations plugin = mock(BlockIntegrations.class);
        API core = TestCore.api(); when(plugin.core()).thenReturn(core);
        PlayerHandlerInterface first = mock(PlayerHandlerInterface.class), second = mock(PlayerHandlerInterface.class);
        when(core.player()).thenReturn(first);
        Player player = mock(Player.class); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(first.withdrawExperience(player, 50)).thenReturn(true);
        Object manager = manager(type, plugin);
        Method pay = type.getDeclaredMethod("takeExperience", Player.class, int.class); pay.setAccessible(true);
        assertEquals(true, pay.invoke(manager, player, 50));
        verify(first).withdrawExperience(player, 50);
        when(core.player()).thenReturn(second);
        assertEquals(false, pay.invoke(manager, player, 50));
        verify(second).withdrawExperience(player, 50);
        clearInvocations(core, first, second);
        assertEquals(true, pay.invoke(manager, player, 0));
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertEquals(true, pay.invoke(manager, player, 50));
        verifyNoInteractions(core, first, second);
        verify(player, never()).calculateTotalExperiencePoints();
        verify(player, never()).setExperienceLevelAndProgress(anyInt());
    }

    @Test void towerShotUsesCoreParticlesAndBoundedCoreAnimation() {
        BlockIntegrations plugin = mock(BlockIntegrations.class);
        API core = TestCore.api(); when(plugin.core()).thenReturn(core);
        ParticleInterface particles = mock(ParticleInterface.class); when(core.particle()).thenReturn(particles);
        World world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        Location from = new Location(world, 0, 1, 0), to = new Location(world, 1.3, 1, 0);
        TowerEffects effects = new TowerEffects(plugin);
        effects.shot(TowerType.ARCHER, from, to);
        var frame = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(particles).repeat(eq(plugin), frame.capture(), eq(1L), eq(4));
        for (int i = 0; i < 4; i++) frame.getValue().run();
        verify(particles, times(4)).line(eq(Particle.CRIT), any(Location.class), any(Location.class), eq(2), isNull());
        assertEquals(new Location(world, 0, 1, 0), from);
    }

    @Test void theCoreWeBuildAgainstMeetsTheMinimumVersionAndOffersTheManagers() {
        assertTrue(Vystorm.vystorm_core.CoreApi.compare(Vystorm.vystorm_core.CoreApi.VERSION, BlockIntegrations.MIN_CORE_VERSION) >= 0);
        assertTrue(Vystorm.vystorm_core.CoreApi.has("managers"));
    }
}
