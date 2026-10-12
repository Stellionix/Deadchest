package me.crylonz.deadchest.utils;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;

/** APIs added after the original 1.13 release must remain optional. */
public final class LegacySupport {
    public static boolean isAir(Material material) {
        String name = material.name();
        return name.equals("AIR") || name.equals("CAVE_AIR") || name.equals("VOID_AIR");
    }
    public static void setForceLoaded(Chunk chunk, boolean forced) {
        try { Chunk.class.getMethod("setForceLoaded", boolean.class).invoke(chunk, forced); }
        catch (NoSuchMethodException absent) { if (forced) chunk.load(); }
        catch (ReflectiveOperationException error) { throw failure(error); }
    }
    public static boolean isForceLoaded(World world, int x, int z) {
        try { return (Boolean) World.class.getMethod("isChunkForceLoaded", int.class, int.class).invoke(world, x, z); }
        catch (NoSuchMethodException absent) { return false; }
        catch (ReflectiveOperationException error) { throw failure(error); }
    }
    private static RuntimeException failure(ReflectiveOperationException error) {
        Throwable cause = error.getCause();
        return cause instanceof RuntimeException ? (RuntimeException) cause : new IllegalStateException(error);
    }
    private LegacySupport() {}
}
