package local.blockintegrations;

import java.util.*;
import java.util.random.RandomGenerator;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrewingBonusesTest {
    @Test void chancesAndDurationsMatchRequestedCaps() {
        assertEquals(.01, BrewingBonuses.chance("extra", 1));
        assertEquals(.05, BrewingBonuses.chance("extra", 10));
        assertEquals(.1, BrewingBonuses.chance("strength", 1));
        assertEquals(1, BrewingBonuses.chance("strength", 10));
        assertEquals(0, BrewingBonuses.chance("extra", 0));
        assertEquals(1700, BrewingBonuses.duration(1200, false, 1));
        assertEquals(6200, BrewingBonuses.duration(1200, false, 10));
        assertEquals(1, BrewingBonuses.duration(1, true, 10));
        assertEquals(-1, BrewingBonuses.duration(-1, false, 10));
        assertEquals(Integer.MAX_VALUE, BrewingBonuses.duration(Integer.MAX_VALUE, false, 10));
    }
    ItemStack potion(boolean rolled) {
        var stack = mock(ItemStack.class); var meta = mock(PotionMeta.class);
        var data = mock(PersistentDataContainer.class);
        when(stack.getItemMeta()).thenReturn(meta); when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.has(BrewingBonuses.ROLLED, PersistentDataType.BYTE)).thenReturn(rolled);
        return stack;
    }
    @Test void waterAndAwkwardBottlesDoNotConsumeRolls() {
        var output = potion(false); var random = mock(RandomGenerator.class);
        assertSame(output, BrewingBonuses.apply(null, output, 5, 10, 10, random));
        verifyNoInteractions(random);
        verify(((PotionMeta) output.getItemMeta()).getPersistentDataContainer(), never()).set(any(), any(), any());
    }
    @Test void previouslyRolledBottlesCannotRollAgain() {
        var output = potion(true); var random = mock(RandomGenerator.class);
        assertSame(output, BrewingBonuses.apply(null, output, 5, 10, 10, random));
        verifyNoInteractions(random);
    }
    @Test void conversionCarriesMarkerEvenWhenRecipeClearsMetadata() {
        var source = potion(true); var output = potion(false); var copy = potion(false);
        var random = mock(RandomGenerator.class); when(output.clone()).thenReturn(copy);
        assertSame(copy, BrewingBonuses.apply(source, output, 5, 10, 10, random));
        verify(copy.getItemMeta().getPersistentDataContainer()).set(BrewingBonuses.ROLLED, PersistentDataType.BYTE, (byte) 1);
        verifyNoInteractions(random); verify(output, never()).setItemMeta(any());
    }
    @Test void allFiveUpgradesSurvivePortableStorageAndExtraRankIsBounded() throws Exception {
        var state = new PortableBlockData.State(Material.BREWING_STAND, "workstation", "BREWING_STAND",
                Map.of("speed", 10, "fuel", 10, "extra", 5, "strength", 10, "duration", 10), UUID.randomUUID(), 0);
        assertEquals(state, PortableBlockData.decode(PortableBlockData.encode(state)));
        assertThrows(IllegalArgumentException.class, () -> PortableBlockData.encode(new PortableBlockData.State(
                state.material(), state.kind(), state.type(), Map.of("extra", 6), state.owner(), 0)));
    }
}
