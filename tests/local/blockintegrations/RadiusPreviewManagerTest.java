package local.blockintegrations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadiusPreviewManagerTest {
    @Test
    void givenSmallRadius_whenSamplingRing_thenUsesMinimumVisiblePointCount() {
        assertEquals(16, RadiusPreviewManager.pointCountForRadius(3.0D));
    }

    @Test
    void givenLargeRadius_whenSamplingRing_thenHonorsHardParticleBound() {
        assertEquals(RadiusPreviewManager.MAX_PARTICLES_PER_FRAME,
                RadiusPreviewManager.pointCountForRadius(128.0D));
        assertTrue(RadiusPreviewManager.MAX_ACTIVE_VIEWERS * RadiusPreviewManager.MAX_PARTICLES_PER_FRAME
                * RadiusPreviewManager.MAX_FRAMES <= 3_072);
    }
}
