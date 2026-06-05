package network.tserver.tnexus.manager;

import java.util.Objects;
import java.util.UUID;
import network.tserver.tnexus.util.BlockPosition;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Mutable SignShop domain model backed by the database.
 */
public final class SignShop {

    private long id;
    private final ShopType type;
    private final UUID ownerUuid;
    private final String ownerName;
    private final BlockPosition signPosition;
    private @Nullable BlockPosition linkedChestPosition;
    private @Nullable ItemStack itemStack;
    private String itemName;
    private @Nullable Double buyPrice;
    private @Nullable Double sellPrice;
    private String note;
    private boolean enabled;

    /**
     * Creates a new sign shop.
     *
     * @param id database id
     * @param type shop type
     * @param ownerUuid owner UUID
     * @param ownerName owner name
     * @param signPosition sign position
     * @param linkedChestPosition linked chest position
     * @param itemStack template item
     * @param itemName rendered item name
     * @param buyPrice buy price
     * @param sellPrice sell price
     * @param note sign note
     * @param enabled enabled flag
     */
    public SignShop(
            long id,
            ShopType type,
            UUID ownerUuid,
            String ownerName,
            BlockPosition signPosition,
            @Nullable BlockPosition linkedChestPosition,
            @Nullable ItemStack itemStack,
            String itemName,
            @Nullable Double buyPrice,
            @Nullable Double sellPrice,
            String note,
            boolean enabled) {
        this.id = id;
        this.type = Objects.requireNonNull(type, "type");
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName");
        this.signPosition = Objects.requireNonNull(signPosition, "signPosition");
        this.linkedChestPosition = linkedChestPosition;
        this.itemStack = itemStack == null ? null : itemStack.clone();
        this.itemName = Objects.requireNonNull(itemName, "itemName");
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.note = Objects.requireNonNull(note, "note");
        this.enabled = enabled;
    }

    /**
     * Returns the database id.
     *
     * @return database id
     */
    public long getId() {
        return this.id;
    }

    /**
     * Sets the database id after insert.
     *
     * @param id database id
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Returns the shop type.
     *
     * @return shop type
     */
    public ShopType getType() {
        return this.type;
    }

    /**
     * Returns the owner UUID.
     *
     * @return owner UUID
     */
    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    /**
     * Returns the owner name.
     *
     * @return owner name
     */
    public String getOwnerName() {
        return this.ownerName;
    }

    /**
     * Returns the sign position.
     *
     * @return sign position
     */
    public BlockPosition getSignPosition() {
        return this.signPosition;
    }

    /**
     * Returns the linked chest position.
     *
     * @return linked chest position or {@code null}
     */
    public @Nullable BlockPosition getLinkedChestPosition() {
        return this.linkedChestPosition;
    }

    /**
     * Updates the linked chest position.
     *
     * @param linkedChestPosition linked chest position
     */
    public void setLinkedChestPosition(@Nullable BlockPosition linkedChestPosition) {
        this.linkedChestPosition = linkedChestPosition;
    }

    /**
     * Returns the template item.
     *
     * @return template item or {@code null}
     */
    public @Nullable ItemStack getItemStack() {
        return this.itemStack == null ? null : this.itemStack.clone();
    }

    /**
     * Updates the template item.
     *
     * @param itemStack template item
     */
    public void setItemStack(@Nullable ItemStack itemStack) {
        this.itemStack = itemStack == null ? null : itemStack.clone();
    }

    /**
     * Returns the rendered item name.
     *
     * @return item name
     */
    public String getItemName() {
        return this.itemName;
    }

    /**
     * Updates the rendered item name.
     *
     * @param itemName item name
     */
    public void setItemName(String itemName) {
        this.itemName = Objects.requireNonNull(itemName, "itemName");
    }

    /**
     * Returns the buy price.
     *
     * @return buy price or {@code null}
     */
    public @Nullable Double getBuyPrice() {
        return this.buyPrice;
    }

    /**
     * Updates the buy price.
     *
     * @param buyPrice buy price or {@code null}
     */
    public void setBuyPrice(@Nullable Double buyPrice) {
        this.buyPrice = buyPrice;
    }

    /**
     * Returns the sell price.
     *
     * @return sell price or {@code null}
     */
    public @Nullable Double getSellPrice() {
        return this.sellPrice;
    }

    /**
     * Updates the sell price.
     *
     * @param sellPrice sell price or {@code null}
     */
    public void setSellPrice(@Nullable Double sellPrice) {
        this.sellPrice = sellPrice;
    }

    /**
     * Returns the note text shown on line 4.
     *
     * @return note text
     */
    public String getNote() {
        return this.note;
    }

    /**
     * Updates the note text.
     *
     * @param note note text
     */
    public void setNote(String note) {
        this.note = Objects.requireNonNull(note, "note");
    }

    /**
     * Returns whether the shop is enabled.
     *
     * @return {@code true} when enabled
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * Updates the enabled flag.
     *
     * @param enabled enabled flag
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
