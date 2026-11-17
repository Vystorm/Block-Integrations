package local.blockintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlockKeyTest {
    @Test
    void roundTripsPersistedLocation() {
        BlockKey key = new BlockKey(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                -321, 84, 765);
        assertEquals(key, BlockKey.parse(key.serialize()));
        assertEquals(-21, key.chunkX());
        assertEquals(47, key.chunkZ());
    }
}
