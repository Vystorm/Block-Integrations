package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.bukkit.block.Furnace;
import org.bukkit.entity.ExperienceOrb;
import org.junit.jupiter.api.Test;

public class BukkitFeaturesTest {
    public interface Handle { Object getHandle(); }
    public interface Snapshot { Object getSnapshot(); }
    static class OrbState { private int count=4; }
    static class FurnaceState { private final Map<String,Integer> recipesUsed=new HashMap<>(Map.of("iron",7)); }
    @Test void givenMergedBukkitOrbsWhenPartiallyCollectedThenRemainingCountIsPreserved() {
        ExperienceOrb orb=mock(ExperienceOrb.class,withSettings().extraInterfaces(Handle.class));
        OrbState state=new OrbState();when(((Handle)orb).getHandle()).thenReturn(state);
        when(orb.getCount()).thenThrow(new NoSuchMethodError());doThrow(new NoSuchMethodError()).when(orb).setCount(anyInt());
        assertEquals(4,BukkitFeatures.orbCount(orb));BukkitFeatures.orbCount(orb,2);assertEquals(2,BukkitFeatures.orbCount(orb));
        assertThrows(IllegalArgumentException.class,()->BukkitFeatures.orbCount(orb,0));
    }
    @Test void givenFurnaceSnapshotWhenXpWithdrawnThenOnlyItsRecipeLedgerIsCleared() {
        Furnace furnace=mock(Furnace.class,withSettings().extraInterfaces(Snapshot.class));
        FurnaceState snapshot=new FurnaceState();when(((Snapshot)furnace).getSnapshot()).thenReturn(snapshot);
        doThrow(new NoSuchMethodError()).when(furnace).setRecipesUsed(Map.of());
        BukkitFeatures.clearRecipes(furnace);assertTrue(snapshot.recipesUsed.isEmpty());
        verify(furnace,never()).update(anyBoolean(),anyBoolean());
    }
    @Test void givenUnrelatedBlockReplacementWhenAnvilTakeFinishesThenItCannotBeRestored() {
        assertTrue(AnvilManager.vanillaDamageTransition(org.bukkit.Material.ANVIL,org.bukkit.Material.CHIPPED_ANVIL));
        assertTrue(AnvilManager.vanillaDamageTransition(org.bukkit.Material.DAMAGED_ANVIL,org.bukkit.Material.AIR));
        assertFalse(AnvilManager.vanillaDamageTransition(org.bukkit.Material.ANVIL,org.bukkit.Material.AIR));
        assertFalse(AnvilManager.vanillaDamageTransition(org.bukkit.Material.ANVIL,org.bukkit.Material.STONE));
        assertFalse(AnvilManager.vanillaDamageTransition(org.bukkit.Material.CHIPPED_ANVIL,org.bukkit.Material.CHIPPED_ANVIL));
    }
}
