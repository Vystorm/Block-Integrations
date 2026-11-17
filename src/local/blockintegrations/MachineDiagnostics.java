package local.blockintegrations;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Read-only construction reports. Uses the same machine matchers/material descriptors as activation. */
final class MachineDiagnostics {
    enum State { COMPLETE, INCOMPLETE, UNLOADED }

    record Finding(String part, Material expected, Material actual, int x, int y, int z) { }

    record Report(State state, List<Finding> findings) {
        Report { findings = List.copyOf(findings); }
    }

    private MachineDiagnostics() { }

    static Component materialName(CommandSender viewer, Material material) {
        return viewer instanceof Player
                ? Component.translatable(material.getTranslationKey())
                : Component.text(TowerType.friendly(material));
    }

    static Report combo(ComboType type, Block control) {
        World world = control.getWorld();
        if (!world.isChunkLoaded(control.getX() >> 4, control.getZ() >> 4)) {
            return new Report(State.UNLOADED, List.of());
        }
        Material[] canonical = type.layout();
        Material[] actual = new Material[canonical.length];
        for (int depth = 0; depth < actual.length; depth++) {
            actual[depth] = (depth == 0 ? control : control.getRelative(0, -depth, 0)).getType();
        }
        if (type.matches(actual[0], actual[1], actual.length == 3 ? actual[2] : Material.AIR)) {
            return new Report(State.COMPLETE, List.of());
        }
        List<Finding> findings = new ArrayList<>();
        String[] parts = {"control", "base", "lower-base"};
        for (int depth = 0; depth < actual.length; depth++) {
            if (comboAcceptsAt(type, depth, actual[depth], canonical)) continue;
            Block position = depth == 0 ? control : control.getRelative(0, -depth, 0);
            findings.add(finding(parts[depth], canonical[depth], actual[depth], position));
        }
        return new Report(findings.isEmpty() ? State.COMPLETE : State.INCOMPLETE, findings);
    }

    /** Helps avoid treating every piece of ice or redstone as a machine attempt. */
    static boolean hasComboEvidence(ComboType type, Block control) {
        if (!control.getWorld().isChunkLoaded(control.getX() >> 4, control.getZ() >> 4)) return false;
        Material[] canonical = type.layout();
        for (int depth = 1; depth < canonical.length; depth++) {
            Material actual = control.getRelative(0, -depth, 0).getType();
            if (comboAcceptsAt(type, depth, actual, canonical)) return true;
        }
        return false;
    }

    private static boolean comboAcceptsAt(ComboType type, int depth, Material actual, Material[] canonical) {
        for (Material candidate : Material.values()) {
            Material[] values = canonical.clone();
            values[depth] = candidate;
            if (type.matches(values[0], values[1], values.length == 3 ? values[2] : Material.AIR)) {
                if (actual == candidate) return true;
            }
        }
        return false;
    }

    static Report tower(TowerType type, Block core) {
        World world = core.getWorld();
        int[][] offsets = {{0, 0, 0}, {0, -1, 0}, {0, -2, 0}, {1, -2, 0}, {-1, -2, 0}, {0, -2, 1}, {0, -2, -1}};
        for (int[] offset : offsets) {
            int x = core.getX() + offset[0], z = core.getZ() + offset[2];
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return new Report(State.UNLOADED, List.of());
        }
        if (type.matches(core)) return new Report(State.COMPLETE, List.of());
        Material[] expected = {type.core, type.pedestal, type.foundation,
                type.ring, type.ring, type.ring, type.ring};
        String[] parts = {"control", "pedestal", "foundation", "ring", "ring", "ring", "ring"};
        List<Finding> findings = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++) {
            int[] offset = offsets[i];
            Block position = offset[0] == 0 && offset[1] == 0 && offset[2] == 0
                    ? core : core.getRelative(offset[0], offset[1], offset[2]);
            Material actual = position.getType();
            boolean valid = i == 2 ? type.acceptsFoundation(actual) : actual == expected[i];
            if (!valid) findings.add(finding(parts[i], expected[i], actual, position));
        }
        return new Report(findings.isEmpty() ? State.COMPLETE : State.INCOMPLETE, findings);
    }

    static boolean hasTowerEvidence(TowerType type, Block core) {
        World world = core.getWorld();
        int[][] offsets = {{0, -1, 0}, {0, -2, 0}, {1, -2, 0}, {-1, -2, 0}, {0, -2, 1}, {0, -2, -1}};
        for (int[] offset : offsets) {
            int x = core.getX() + offset[0], z = core.getZ() + offset[2];
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Material actual = core.getRelative(offset[0], offset[1], offset[2]).getType();
            if (offset[1] == -1 && actual == type.pedestal) return true;
            if (offset[1] == -2 && (offset[0] == 0 && offset[2] == 0
                    ? type.acceptsFoundation(actual) : actual == type.ring)) return true;
        }
        return false;
    }

    private static Finding finding(String part, Material expected, Material actual, Block block) {
        return new Finding(part, expected, actual, block.getX(), block.getY(), block.getZ());
    }
}
