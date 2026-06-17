package network.tserver.tnexus.manager.recap.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Assist contribution recorded for one kill event.
 *
 * @param assistantUuid assistant UUID
 * @param assistantTeamId assistant team identifier
 * @param damage contributing damage
 * @param damageRate ratio against the victim's recent incoming damage
 */
public record AssistEntry(
        UUID assistantUuid,
        String assistantTeamId,
        double damage,
        double damageRate) {

    /**
     * Creates a validated assist entry.
     */
    public AssistEntry {
        assistantUuid = Objects.requireNonNull(assistantUuid, "assistantUuid");
        assistantTeamId = Objects.requireNonNull(assistantTeamId, "assistantTeamId");
        validateNonNegative(damage, "damage");
        validateNonNegative(damageRate, "damageRate");
    }

    private static void validateNonNegative(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be a finite non-negative value");
        }
    }
}
