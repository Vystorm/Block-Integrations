package local.blockintegrations;

import Vystorm.vystorm_core._coreMethods.dataStorage.AtomicFilesInterface;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import Vystorm.vystorm_core.schedule.TaskGroup;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FurnacePendingMigrationTest {
    @TempDir Path folder;
    private static final String OWNER = "00000000-0000-0000-0000-000000000001";

    private Path legacy() { return folder.resolve("pending-mcmmo-furnace-xp.yml"); }
    private StateFile<FurnaceManager.PendingState> store() {
        return new StateFile<>(folder.resolve("pending.json"), "test", FurnaceManager.PendingState.class,
                FurnaceManager.PENDING_SCHEMA, Map.of(), FurnaceManager.PendingState::empty, AtomicFilesInterface.DEFAULT);
    }
    private FurnaceManager load(StateFile<FurnaceManager.PendingState> store) throws Exception {
        FurnaceManager manager = mock(FurnaceManager.class, CALLS_REAL_METHODS);
        set(manager, "pendingStore", store);
        set(manager, "legacyPendingFile", legacy().toFile());
        set(manager, "pendingSkillExperience", new HashMap<String, Double>());
        set(manager, "tasks", mock(TaskGroup.class));
        var load = FurnaceManager.class.getDeclaredMethod("loadPendingSkillExperience");
        load.setAccessible(true);
        load.invoke(manager);
        return manager;
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = FurnaceManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test void migrationIsDurableBeforeAnyScheduledTaskRuns() throws Exception {
        Files.writeString(legacy(), "pending:\n  " + OWNER + ": 12.5\n");
        load(store());
        assertEquals(12.5, store().load().pending().get(OWNER + ":smelting"));
        assertFalse(Files.exists(legacy()));
    }

    @Test void failedArchiveDoesNotImportTheSameExperienceTwice() throws Exception {
        Files.writeString(legacy(), "pending:\n  " + OWNER + ": 12.5\n");
        Path blocked = legacy().resolveSibling(legacy().getFileName() + ".migrated");
        Files.createDirectory(blocked);
        Files.writeString(blocked.resolve("keep"), "existing archive");
        FurnaceManager manager = load(store());
        assertTrue(Files.exists(legacy()));
        var save = FurnaceManager.class.getDeclaredMethod("savePendingSkillExperience");
        save.setAccessible(true);
        save.invoke(manager);
        load(store());
        assertEquals(12.5, store().load().pending().get(OWNER + ":smelting"));
    }

    @Test void schemaOneBalancesSurviveTheMigration() throws Exception {
        Files.writeString(folder.resolve("pending.json"), "{\"schema\":1,\"data\":{\"pending\":{\"" + OWNER + ":smelting\":7.5}}}");
        Files.writeString(legacy(), "pending:\n  " + OWNER + ": 12.5\n");
        load(store());
        assertEquals(20.0, store().load().pending().get(OWNER + ":smelting"));
        assertTrue(store().load().legacyImported());
    }

    @Test void failedSaveLeavesLegacyFileAvailableAndCreditsUnchanged() throws Exception {
        Files.writeString(legacy(), "pending:\n  " + OWNER + ": 12.5\n");
        @SuppressWarnings("unchecked") StateFile<FurnaceManager.PendingState> failing = mock(StateFile.class);
        when(failing.load()).thenReturn(FurnaceManager.PendingState.empty());
        when(failing.saveChecked(any())).thenReturn(false);
        FurnaceManager manager = load(failing);
        assertTrue(Files.exists(legacy()));
        Field field = FurnaceManager.class.getDeclaredField("pendingSkillExperience");
        field.setAccessible(true);
        assertTrue(((Map<?, ?>) field.get(manager)).isEmpty());
    }

    @Test void malformedYamlIsNotArchivedAsSuccessfullyMigrated() throws Exception {
        String content = "pending: [\n";
        Files.writeString(legacy(), content);
        load(store());
        assertTrue(Files.exists(legacy()));
        assertEquals(content, Files.readString(legacy()));
    }

    @Test void queuedSkillDisplayTotalsOnlyTheSelectedOwnerWithoutConsumingAnything() throws Exception {
        FurnaceManager manager = mock(FurnaceManager.class, CALLS_REAL_METHODS);
        Map<String, Double> balances = new HashMap<>(Map.of(OWNER + ":smelting", 12.5, OWNER + ":cooking", 7.5,
                "00000000-0000-0000-0000-000000000002:smelting", 999.0));
        set(manager, "pendingSkillExperience", balances);
        assertEquals(20.0, manager.queuedSkillExperience(java.util.UUID.fromString(OWNER)));
        assertEquals(0.0, manager.queuedSkillExperience(null));
        assertEquals(0.0, manager.queuedSkillExperience(java.util.UUID.randomUUID()));
        assertEquals(3, balances.size());
        assertEquals(12.5, balances.get(OWNER + ":smelting"));
    }
}
