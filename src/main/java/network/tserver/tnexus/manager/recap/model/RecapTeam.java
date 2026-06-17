package network.tserver.tnexus.manager.recap.model;

import java.util.Objects;

/**
 * Immutable team definition used by the combat recap system.
 */
public final class RecapTeam {

    private final String teamId;
    private final String displayName;
    private final boolean defeatWhenAllDead;

    /**
     * Creates a new recap team definition.
     *
     * @param teamId internal team identifier
     * @param displayName team display name
     * @param defeatWhenAllDead whether the team is defeated when all members die
     */
    public RecapTeam(String teamId, String displayName, boolean defeatWhenAllDead) {
        this.teamId = Objects.requireNonNull(teamId, "teamId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.defeatWhenAllDead = defeatWhenAllDead;
    }

    /**
     * Returns the internal team identifier.
     *
     * @return team identifier
     */
    public String getTeamId() {
        return this.teamId;
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
     * Returns whether the team should lose once all members are dead.
     *
     * @return {@code true} when all-dead defeat checks apply
     */
    public boolean isDefeatWhenAllDead() {
        return this.defeatWhenAllDead;
    }
}
