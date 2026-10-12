package me.crylonz.deadchest.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ItemBytes {
    private ItemBytes() {
    }

    /**
     * We used customs paper methods if available via reflexion to keep java 8 compatibility
     */
    private static volatile Method SERIALIZE_AS_BYTES;   // instance method: byte[] serializeAsBytes()
    private static volatile Method DESERIALIZE_BYTES;    // static method: ItemStack deserializeBytes(byte[])

    public static byte[] toBytes(ItemStack item) {
        if (item == null || LegacySupport.isAir(item.getType())) return new byte[0];

        // paper (via réflexion)
        Method m = getSerializeAsBytes();
        if (m != null) {
            try {
                Object res = m.invoke(item);
                if (res instanceof byte[]) return (byte[]) res;
            } catch (ReflectiveOperationException e) {
                // spigot fallback
            }
        }

        // Spigot fallback
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(item);
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to serialize ItemStack (Spigot fallback)", ex);
        }
    }

    public static ItemStack fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;

        //  Paper (via réflexion)
        Method m = getDeserializeBytes();
        if (m != null) {
            try {
                Object res = m.invoke(null, bytes);
                if (res instanceof ItemStack) return (ItemStack) res;
            } catch (ReflectiveOperationException e) {
                // Spigot fallback
            }
        }

        // Spigot fallback
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            return (ItemStack) ois.readObject();
        } catch (IOException | ClassNotFoundException ex) {
            throw new RuntimeException("Failed to deserialize ItemStack (Spigot fallback)", ex);
        }
    }

    // --- Sérialisation d'une liste d'items ---
    public static byte[] toBytesList(List<ItemStack> items) {
        if (items == null) return new byte[0];
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {

            oos.writeInt(items.size());
            for (ItemStack item : items) {
                byte[] itemBytes = toBytes(item);
                oos.writeObject(itemBytes);
            }
            return baos.toByteArray();

        } catch (IOException ex) {
            throw new RuntimeException("Failed to serialize inventory list", ex);
        }
    }

    public static List<ItemStack> fromBytesList(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return new ArrayList<>();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {

            int size = ois.readInt();
            List<ItemStack> items = new ArrayList<>(size);

            for (int i = 0; i < size; i++) {
                byte[] itemBytes = (byte[]) ois.readObject();
                items.add(fromBytes(itemBytes));
            }
            return items;

        } catch (IOException | ClassNotFoundException ex) {
            throw new RuntimeException("Failed to deserialize inventory list", ex);
        }
    }

    // --- Helpers réflexion (thread-safe, Java 8) ---

    private static Method getSerializeAsBytes() {
        Method cached = SERIALIZE_AS_BYTES;
        if (cached != null) return cached;
        try {
            Method m = ItemStack.class.getMethod("serializeAsBytes");
            m.setAccessible(true);
            SERIALIZE_AS_BYTES = m;
            return m;
        } catch (NoSuchMethodException ignored) {
            SERIALIZE_AS_BYTES = null;
            return null;
        }
    }

    private static Method getDeserializeBytes() {
        Method cached = DESERIALIZE_BYTES;
        if (cached != null) return cached;
        try {
            Method m = ItemStack.class.getMethod("deserializeBytes", byte[].class);
            m.setAccessible(true);
            DESERIALIZE_BYTES = m;
            return m;
        } catch (NoSuchMethodException ignored) {
            DESERIALIZE_BYTES = null;
            return null;
        }
    }
}
