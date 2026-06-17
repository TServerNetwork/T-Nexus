package network.tserver.tnexus.manager.recap.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Mutable in-session combat state for one tracked entity.
 */
public final class RecapEntityState {

    private final UUID entityUuid;
    private String displayName;
    private String entityType;
    private String teamId;
    private boolean alive;
    private int killCount;
    private int deathCount;
    private int assistCount;
    private double totalDealt;
    private double totalTaken;

    /**
     * Creates a new entity state snapshot.
     *
     * @param entityUuid entity UUID
     * @param displayName display name
     * @param entityType serialized entity type name
     * @param teamId team identifier
     * @param alive whether the entity is alive
     * @param killCount total kills
     * @param deathCount total deaths
     * @param assistCount total assists
     * @param totalDealt total damage dealt
     * @param totalTaken total damage taken
     */
    public RecapEntityState(
            UUID entityUuid,
            String displayName,
            String entityType,
            String teamId,
            boolean alive,
            int killCount,
            int deathCount,
            int assistCount,
            double totalDealt,
            double totalTaken) {
        this.entityUuid = Objects.requireNonNull(entityUuid, "entityUuid");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.teamId = Objects.requireNonNull(teamId, "teamId");
        this.alive = alive;
        this.killCount = validateNonNegative(killCount, "killCount");
        this.deathCount = validateNonNegative(deathCount, "deathCount");
        this.assistCount = validateNonNegative(assistCount, "assistCount");
        this.totalDealt = validateNonNegative(totalDealt, "totalDealt");
        this.totalTaken = validateNonNegative(totalTaken, "totalTaken");
    }

    /**
     * Returns the tracked entity UUID.
     *
     * @return entity UUID
     */
    public UUID getEntityUuid() {
        return this.entityUuid;
    }

    /**
     * Returns the display name.
     *
     * @return display name
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Updates the display name.
     *
     * @param displayName display name
     */
    public void setDisplayName(String displayName) {
        this.displayName = Objects.requireNonNull(displayName, "displayName");
    }

    /**
     * Returns the serialized entity type name.
     *
     * @return entity type name
     */
    public String getEntityType() {
        return this.entityType;
    }

    /**
     * Updates the serialized entity type name.
     *
     * @param entityType entity type name
     */
    public void setEntityType(String entityType) {
        this.entityType = Objects.requireNonNull(entityType, "entityType");
    }

    /**
     * Returns the team identifier.
     *
     * @return team identifier
     */
    public String getTeamId() {
        return this.teamId;
    }

    /**
     * Updates the team identifier.
     *
     * @param teamId team identifier
     */
    public void setTeamId(String teamId) {
        this.teamId = Objects.requireNonNull(teamId, "teamId");
    }

    /**
     * Returns whether the entity is currently alive.
     *
     * @return alive flag
     */
    public boolean isAlive() {
        return this.alive;
    }

    /**
     * Updates the alive flag.
     *
     * @param alive alive flag
     */
    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    /**
     * Returns the kill count.
     *
     * @return kill count
     */
    public int getKillCount() {
        return this.killCount;
    }

    /**
     * Increments the kill count by one.
     */
    public void incrementKillCount() {
        this.killCount++;
    }

    /**
     * Returns the death count.
     *
     * @return death count
     */
    public int getDeathCount() {
        return this.deathCount;
    }

    /**
     * Increments the death count by one.
     */
    public void incrementDeathCount() {
        this.deathCount++;
    }

    /**
     * Returns the assist count.
     *
     * @return assist count
     */
    public int getAssistCount() {
        return this.assistCount;
    }

    /**
     * Increments the assist count by one.
     */
    public void incrementAssistCount() {
        this.assistCount++;
    }

    /**
     * Returns total damage dealt.
     *
     * @return total damage dealt
     */
    public double getTotalDealt() {
        return this.totalDealt;
    }

    /**
     * Adds to the dealt-damage total.
     *
     * @param damage damage to add
     */
    public void addTotalDealt(double damage) {
        this.totalDealt += validateNonNegative(damage, "damage");
    }

    /**
     * Returns total damage taken.
     *
     * @return total damage taken
     */
    public double getTotalTaken() {
        return this.totalTaken;
    }

    /**
     * Adds to the taken-damage total.
     *
     * @param damage damage to add
     */
    public void addTotalTaken(double damage) {
        this.totalTaken += validateNonNegative(damage, "damage");
    }

    private static int validateNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    private static double validateNonNegative(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be a finite non-negative value");
        }
        return value;
    }
}
