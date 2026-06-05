package network.tserver.tnexus.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * Serializes Bukkit item stacks for persistence.
 */
public final class ItemStackSerializer {

    private ItemStackSerializer() {
    }

    /**
     * Serializes an item stack to a Base64 string.
     *
     * @param itemStack item stack to serialize
     * @return encoded item stack or {@code null} when the item is null
     */
    public static String serialize(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream objectOutputStream = new BukkitObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(itemStack);
            objectOutputStream.flush();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize ItemStack", exception);
        }
    }

    /**
     * Deserializes an item stack from a Base64 string.
     *
     * @param serialized serialized item stack
     * @return decoded item stack or {@code null} when the input is blank
     */
    public static ItemStack deserialize(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return null;
        }

        byte[] bytes = Base64.getDecoder().decode(serialized);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream objectInputStream = new BukkitObjectInputStream(inputStream)) {
            Object object = objectInputStream.readObject();
            if (object instanceof ItemStack itemStack) {
                return itemStack;
            }
            throw new IllegalStateException("Serialized value was not an ItemStack");
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to deserialize ItemStack", exception);
        }
    }
}
