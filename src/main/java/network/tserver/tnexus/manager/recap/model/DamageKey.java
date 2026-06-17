package network.tserver.tnexus.manager.recap.model;

import java.util.UUID;

/**
 * Identifies one attacker-to-victim damage pair.
 *
 * @param attackerUuid attacker UUID
 * @param victimUuid victim UUID
 */
public record DamageKey(UUID attackerUuid, UUID victimUuid) {
}
