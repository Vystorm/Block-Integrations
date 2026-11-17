package local.blockintegrations;

import Vystorm.vystorm_core._coreMethods.dataStorage.AtomicFilesInterface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.EntityType;
import static org.junit.jupiter.api.Assertions.*;
class TowerControlsTest {
 @Test void independentRightsAndMalformedDataFailClosed() {
  var controls = new TowerControls(); UUID user = UUID.randomUUID();
  assertTrue(controls.toggle(user, TowerControls.Right.USE));
  assertFalse(controls.allows(user, TowerControls.Right.LOOT));
  assertFalse(controls.allows(user, TowerControls.Right.UPGRADE));
  assertTrue(controls.toggle(user, TowerControls.Right.LOOT));
  var restored = new TowerControls(); restored.load(controls.snapshot());
  assertTrue(restored.allows(user, TowerControls.Right.LOOT));
  restored.remove(user); assertFalse(restored.allows(user, TowerControls.Right.LOOT));
  restored.load(Map.of(user.toString(), 8, "bad", 7)); assertTrue(restored.snapshot().isEmpty());
 }
 @Test void boundedMembersCanStillRevokeAtCapacity() {
  var controls = new TowerControls(); UUID first = UUID.randomUUID();
  controls.toggle(first, TowerControls.Right.USE);
  for (int i=1;i<32;i++) assertTrue(controls.toggle(UUID.randomUUID(), TowerControls.Right.USE));
  assertFalse(controls.toggle(UUID.randomUUID(), TowerControls.Right.USE));
  assertTrue(controls.toggle(first, TowerControls.Right.USE));
  assertEquals(31, controls.snapshot().size());
 }
 @Test void profileNeverExpandsKnownGroupAndUnknownModesDisable() {
  assertTrue(TowerControls.TargetProfile.UNDEAD.includes(EntityType.ZOMBIE));
  assertFalse(TowerControls.TargetProfile.UNDEAD.includes(EntityType.CREEPER));
  assertFalse(TowerControls.TargetProfile.RAIDERS.includes(EntityType.WOLF));
  assertEquals(TowerControls.TargetProfile.NONE, TowerControls.TargetProfile.load("typo"));
  assertEquals(TowerControls.TargetProfile.ALL, TowerControls.TargetProfile.load(null));
  assertFalse(TowerControls.RedstoneMode.POWERED.active(false));
  assertTrue(TowerControls.RedstoneMode.UNPOWERED.active(false));
  assertFalse(TowerControls.RedstoneMode.load("typo").active(true));
 }
 @Test void schemaOneMigrationPreservesLootAndOwnership(@TempDir Path dir) throws Exception {
  Path file=dir.resolve("towers.json");
  Files.writeString(file, """
   {"schema":1,"data":{"towers":{"w:0:64:0":{"type":"archer","owner":"00000000-0000-0000-0000-000000000001","damageLevel":3,"storedExperience":42,"loot":{"4":"encoded-item"}}}}}
   """);
  var store=new StateFile<>(file,"Test",TowerManager.TowersState.class,TowerManager.STATE_SCHEMA,TowerManager.STATE_MIGRATIONS,TowerManager.TowersState::empty, AtomicFilesInterface.DEFAULT);
  var record=store.load().towers().get("w:0:64:0");
  assertNotNull(record); assertEquals(42,record.storedExperience()); assertEquals(3,record.damageLevel());
  assertEquals("encoded-item",record.loot().get(4)); assertTrue(record.grants().isEmpty());
  assertEquals(TowerControls.TargetProfile.ALL,TowerControls.TargetProfile.load(record.targetProfile()));
  assertEquals(TowerControls.RedstoneMode.ALWAYS,TowerControls.RedstoneMode.load(record.redstoneMode()));
  assertEquals("00000000-0000-0000-0000-000000000001",record.owner());
 }
}
