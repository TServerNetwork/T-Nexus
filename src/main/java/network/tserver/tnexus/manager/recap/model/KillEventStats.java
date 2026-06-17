package network.tserver.tnexus.manager.recap.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable kill-and-assist event snapshot produced when one victim dies.
 *
 * @param killerUuid killer UUID
 * @param victimUuid victim UUID
 * @param killerTeamId killer team identifier
 * @param victimTeamId victim team identifier
 * @param assists assist contributions excluding the killer
 * @param occurredAtMicros death timestamp in epoch microseconds
 */
public record KillEventStats(
        UUID killerUuid,
        UUID victimUuid,
        String killerTeamId,
        String victimTeamId,
        List<AssistEntry> assists,
        long occurredAtMicros) {

    /**
     * Creates a validated kill-event snapshot.
     */
    public KillEventStats {
        killerUuid = Objects.requireNonNull(killerUuid, "killerUuid");
        victimUuid = Objects.requireNonNull(victimUuid, "victimUuid");
        killerTeamId = Objects.requireNonNull(killerTeamId, "killerTeamId");
        victimTeamId = Objects.requireNonNull(victimTeamId, "victimTeamId");
        assists = List.copyOf(Objects.requireNonNull(assists, "assists"));
        if (occurredAtMicros < 0L) {
            throw new IllegalArgumentException("occurredAtMicros must be non-negative");
        }
    }
}
