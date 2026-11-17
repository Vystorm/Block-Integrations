package local.blockintegrations;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

/** Genuine CraftBukkit equivalents for the remaining Paper conveniences. Server thread only. */
final class BukkitFeatures {
    private static final NamespacedKey DEATH_XP=new NamespacedKey("blockintegrations","player_death_xp");
    private BukkitFeatures() {}
    static boolean screaming(Enderman entity) {
        try{return entity.isScreaming();}catch(NoSuchMethodError missing){
            try{Object handle=call(entity,"getHandle");return (boolean)call(handle,"isCreepy");}
            catch(ReflectiveOperationException failure){throw unavailable(failure);}
        }
    }
    static int orbCount(ExperienceOrb orb) {
        try{return orb.getCount();}catch(NoSuchMethodError missing){try{Object handle=call(orb,"getHandle");return field(handle,"count").getInt(handle);}catch(ReflectiveOperationException failure){throw unavailable(failure);}}
    }
    static void orbCount(ExperienceOrb orb,int count) {
        if(count<1)throw new IllegalArgumentException("Orb count must be positive");
        try{orb.setCount(count);}catch(NoSuchMethodError missing){try{Object handle=call(orb,"getHandle");field(handle,"count").setInt(handle,count);}catch(ReflectiveOperationException failure){throw unavailable(failure);}}
    }
    static boolean deathOrb(ExperienceOrb orb) {
        try{return "PLAYER_DEATH".equals(orb.getSpawnReason().name());}catch(NoSuchMethodError|NoClassDefFoundError missing){return orb.getPersistentDataContainer().has(DEATH_XP,PersistentDataType.BYTE);}
    }
    static void markDeathOrb(ExperienceOrb orb) { orb.getPersistentDataContainer().set(DEATH_XP,PersistentDataType.BYTE,(byte)1); }
    static void markDeathOrbs(Location death) {
        if(death.getWorld()==null)return;
        for(Entity entity:death.getWorld().getNearbyEntities(death,DeathOrbGuard.RADIUS,DeathOrbGuard.RADIUS,DeathOrbGuard.RADIUS))
            if(entity instanceof ExperienceOrb orb && entity.getLocation().distanceSquared(death)<=DeathOrbGuard.RADIUS*DeathOrbGuard.RADIUS)markDeathOrb(orb);
    }
    static boolean consumesFuel(FurnaceBurnEvent event) { try{return event.willConsumeFuel();}catch(NoSuchMethodError missing){return event.isBurning();} }
    static void clearRecipes(Furnace furnace) {
        try{furnace.setRecipesUsed(Map.of());}catch(NoSuchMethodError missing){
            try{Object snapshot=call(furnace,"getSnapshot");((Map<?,?>)field(snapshot,"recipesUsed").get(snapshot)).clear();}
            catch(ReflectiveOperationException failure){throw unavailable(failure);}
        }
    }
    static CookingRecipe<?> recipe(FurnaceSmeltEvent event,Furnace furnace) {
        try{return event.getRecipe();}catch(NoSuchMethodError missing){
            try{
                Object live=call(furnace,"getTileEntity"),cache=field(live,"quickCheck").get(live),key=field(cache,"lastRecipe").get(cache);
                if(key==null)return null;
                Object identifier;try{identifier=call(key,"identifier");}catch(NoSuchMethodException older){identifier=call(key,"location");}
                Recipe recipe=Bukkit.getRecipe(NamespacedKey.fromString(identifier.toString()));
                return recipe instanceof CookingRecipe<?> cooking?cooking:null;
            }catch(ReflectiveOperationException failure){throw unavailable(failure);}
        }
    }
    static Collection<BlockState> furnaceStates(Chunk chunk) {
        try{return chunk.getTileEntities(block->block.getType()==Material.FURNACE||block.getType()==Material.BLAST_FURNACE||block.getType()==Material.SMOKER,false);}
        catch(NoSuchMethodError missing){return Arrays.stream(chunk.getTileEntities()).filter(Furnace.class::isInstance).toList();}
    }
    static void openAnvil(Player player,Location at,String title) {
        try{player.openAnvil(at,false);}catch(NoSuchMethodError missing){
            try{
                Class<?> menu=Class.forName("org.bukkit.inventory.MenuType"),typed=Class.forName("org.bukkit.inventory.MenuType$Typed");
                Object builder=typed.getMethod("builder").invoke(menu.getField("ANVIL").get(null));
                Class<?> locationBuilder=Class.forName("org.bukkit.inventory.view.builder.LocationInventoryViewBuilder");
                locationBuilder.getMethod("title",String.class).invoke(builder,title);
                locationBuilder.getMethod("location",Location.class).invoke(builder,at);
                locationBuilder.getMethod("checkReachable",boolean.class).invoke(builder,true);
                InventoryView view=(InventoryView)Class.forName("org.bukkit.inventory.view.builder.InventoryViewBuilder").getMethod("build",HumanEntity.class).invoke(builder,player);
                player.openInventory(view);
            }catch(ReflectiveOperationException failure){throw unavailable(failure);}
        }
    }
    private static Object call(Object target,String name)throws ReflectiveOperationException {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Method method=type.getDeclaredMethod(name);method.setAccessible(true);return method.invoke(target);}catch(NoSuchMethodException absent){}
        throw new NoSuchMethodException(target.getClass().getName()+"."+name);
    }
    private static Field field(Object target,String name)throws NoSuchFieldException {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException absent){}
        throw new NoSuchFieldException(target.getClass().getName()+"."+name);
    }
    private static IllegalStateException unavailable(Exception failure){return new IllegalStateException("Native Bukkit machine feature unavailable",failure);}
}
