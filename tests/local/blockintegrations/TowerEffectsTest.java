package local.blockintegrations;

import Vystorm.vystorm_core.API;
import Vystorm.vystorm_core._advancedMethods.particleHandler.ParticleInterface;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TowerEffectsTest {
    BlockIntegrations plugin; ParticleInterface particles; World world; TowerEffects effects;
    Location start, end; BukkitTask task;
    @BeforeEach void setup() {
        plugin = mock(BlockIntegrations.class); API core = TestCore.api();
        particles = mock(ParticleInterface.class); task = mock(BukkitTask.class);
        when(plugin.core()).thenReturn(core); when(core.particle()).thenReturn(particles);
        when(plugin.isEnabled()).thenReturn(true); when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(particles.repeat(eq(plugin),any(),eq(1L),eq(4))).thenReturn(task);
        world = mock(World.class); when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        start = new Location(world,0,1,0); end = new Location(world,8,1,0);
        effects = new TowerEffects(plugin);
    }
    @Test void disabledCosmeticsNeverScheduleOrSpawn() {
        plugin.getConfig().set("towers.visual-effects",false);
        effects.shot(TowerType.ARCHER,start,end); effects.impact(TowerType.CANNON,end);
        verifyNoInteractions(particles);
    }
    @Test void animationStopsDrawingAfterChunkUnloadAndIsCancelledOnShutdown() {
        effects.shot(TowerType.CANNON,start,end);
        var frame = ArgumentCaptor.forClass(Runnable.class);
        verify(particles).repeat(eq(plugin),frame.capture(),eq(1L),eq(4));
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        frame.getValue().run(); verify(particles,never()).line(any(),any(),any(),anyInt(),any());
        effects.close(); verify(task).cancel();
        verify(world,never()).getChunkAt(anyInt(),anyInt());
    }
    @Test void cannonArcReachesDestinationWithoutMovingOriginalLocations() {
        assertEquals(2.25,TowerEffects.point(start,end,.5,TowerType.CANNON).getY());
        assertEquals(end,TowerEffects.point(start,end,1,TowerType.CANNON));
        assertEquals(new Location(world,0,1,0),start); assertEquals(new Location(world,8,1,0),end);
    }
    @Test void impactUsesCoreForBurstAndExpandingRings() {
        effects.impact(TowerType.CANNON,end);
        verify(particles).spawn(eq(Particle.EXPLOSION),eq(end),eq(1),anyDouble(),anyDouble(),anyDouble(),anyDouble(),isNull());
        var frame = ArgumentCaptor.forClass(Runnable.class);
        verify(particles).repeat(eq(plugin),frame.capture(),eq(1L),eq(4));
        for (int i=0;i<4;i++) frame.getValue().run();
        var radius = ArgumentCaptor.forClass(Double.class);
        verify(particles,times(4)).circle(eq(Particle.SMOKE),eq(end),radius.capture(),eq(12),isNull());
        assertTrue(radius.getAllValues().getFirst() < radius.getAllValues().getLast());
        effects.close(); verify(task,never()).cancel();
    }
}
