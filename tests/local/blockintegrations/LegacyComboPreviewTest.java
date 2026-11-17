package local.blockintegrations;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class LegacyComboPreviewTest {
    @Test void givenLegacyCombo_whenPreviewed_thenCountsAndUpgradeSumsConserved() throws Exception {
        String source = "combos:\n  '" + UUID.randomUUID() + ",1,64,3':\n    type: sorter\n    range-level: 3\n    cooldown-level: 2\n";
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        var report = LegacyComboPreview.convert(bytes);
        assertEquals(1, report.beforeCount()); assertEquals(1, report.afterCount()); assertTrue(report.conserved());
        assertEquals(BigDecimal.valueOf(3), report.afterSums().get("range"));
        assertEquals(source, new String(bytes, StandardCharsets.UTF_8));
    }
    @Test void givenInvalidOwner_whenPreviewed_thenSkippedEntryIsReportedNotConserved() throws Exception {
        String source = "combos:\n  '" + UUID.randomUUID() + ",1,64,3':\n    type: sorter\n    owner: bad-id\n";
        var report = LegacyComboPreview.convert(source.getBytes(StandardCharsets.UTF_8));
        assertFalse(report.conserved()); assertEquals(1, report.beforeCount()); assertEquals(0, report.afterCount());
        assertFalse(report.problems().isEmpty());
    }
}
