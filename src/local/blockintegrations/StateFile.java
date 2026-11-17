package local.blockintegrations;

import Vystorm.vystorm_core._coreMethods.dataStorage.AtomicFilesInterface;
import Vystorm.vystorm_core._parsersDebuggerEtc.debugger.DebugMessages;
import Vystorm.vystorm_core.storage.VersionedStore;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * One JSON state file of the plugin ({@code towers.json}, {@code combos.json}, …) as a Core {@link VersionedStore}:
 * envelope {@code {"schema": n, "data": …}}, step-by-step migrations, atomic writes through Core {@link AtomicFiles}.
 * Unreadable content is copied to {@code <file>.corrupt-<millis>} and the file is then never overwritten in this run
 * (saving is switched off and logged), so nothing is silently replaced by an empty state.
 *
 * <p>{@link #save} may run on any thread; writes of one file are serialized.</p>
 */
final class StateFile<T> {
    private final Path path;
    /** Core's file part, {@code core.data().files()}. */
    private final AtomicFilesInterface files;
    private final String marker;
    private final Supplier<T> empty;
    private final VersionedStore<T> store;
    private volatile boolean readOnly;
    private volatile boolean saveFailed;

    StateFile(Path path, String marker, Class<T> type, int schema, Map<Integer, UnaryOperator<JsonObject>> migrations, Supplier<T> empty,
            AtomicFilesInterface files) {
        this.files = files;
        this.path = path;
        this.marker = marker;
        this.empty = empty;
        this.store = new VersionedStore<>(new VersionedStore.Backend() {
            @Override public String read() {
                try {
                    return Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }

            @Override public void write(String value) {
                try {
                    files.write(path, value.getBytes(StandardCharsets.UTF_8));
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }
        }, type, schema, migrations, empty, null, this::quarantine);
    }

    /** Loads (and migrates) the state; an unreadable file yields the empty state and switches saving off. */
    T load() {
        try {
            return store.load();
        } catch (RuntimeException failure) {
            readOnly = true;
            DebugMessages.high("[" + marker + "] " + path.getFileName() + " could not be read; it stays untouched and changes are NOT saved "
                    + "until it is fixed or removed (restart afterwards)", failure);
            return empty.get();
        }
    }

    /** Writes the state atomically; failures are logged, a read-only (unreadable) file is never overwritten. */
    synchronized void save(T state) {
        if (readOnly) return;
        try {
            store.save(state);
            saveFailed = false;
        } catch (RuntimeException failure) {
            saveFailed = true;
            DebugMessages.high("[" + marker + "] Could not save " + path.getFileName(), failure);
        }
    }

    boolean readOnly() {
        return readOnly;
    }

    synchronized boolean saveChecked(T state) { save(state); return !readOnly && !saveFailed; }

    void registerDiagnostics(Vystorm.vystorm_core.lifecycle.FeatureDiagnosticsInterface diagnostics, org.bukkit.plugin.Plugin owner) {
        diagnostics.register(owner,
                path.getFileName().toString().replace('.', '-'), viewer ->
                new Vystorm.vystorm_core.lifecycle.FeatureDiagnostics.Status(path.getFileName().toString(), owner.getName(),
                        readOnly || saveFailed ? Vystorm.vystorm_core.lifecycle.FeatureDiagnostics.State.UNAVAILABLE
                                : Vystorm.vystorm_core.lifecycle.FeatureDiagnostics.State.ACTIVE,
                        "{lang:blockintegrations:diagnostics." + (readOnly ? "storage-readonly" : saveFailed ? "storage-failed" : "storage-active") + "}"));
    }

    private void quarantine(String raw) {
        Path backup = path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            files.write(backup, raw.getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            DebugMessages.high("[" + marker + "] Could not back up unreadable " + path.getFileName(), failure);
        }
    }
}
