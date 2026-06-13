package network.tserver.tnexus.util;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Parsed stats reset target used by reset command and repository layers.
 *
 * @param type reset target type
 * @param qualifier optional target qualifier
 * @param canonicalKey canonical storage key
 */
public record PlayerStatsResetTarget(Type type, @Nullable String qualifier, String canonicalKey) {

    /**
     * Parses a raw reset key into a normalized target.
     *
     * @param rawKey raw key
     * @return parsed target, or {@code null} when unsupported
     */
    public static @Nullable PlayerStatsResetTarget parse(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }

        String normalized = rawKey.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GENERAL_PLAY_TIME", "PLAY_TIME" -> simple(Type.PLAY_TIME);
            case "GENERAL_DISTANCE", "DISTANCE" -> simple(Type.DISTANCE);
            case "GENERAL_DEATHS", "DEATHS" -> simple(Type.DEATHS);
            case "GENERAL_RESPAWNS", "RESPAWNS" -> simple(Type.RESPAWNS);
            case "GENERAL_CHAT_COUNT", "CHAT_COUNT", "CHAT" -> simple(Type.CHAT_COUNT);
            case "GENERAL_SLEEP_COUNT", "SLEEP_COUNT", "SLEEP" -> simple(Type.SLEEP_COUNT);
            case "GENERAL_PORTAL_COUNT", "PORTAL_COUNT", "PORTAL" -> simple(Type.PORTAL_COUNT);
            case "ACTIVITY_CRAFT_TOTAL", "CRAFT" -> simple(Type.CRAFT_ALL);
            case "ACTIVITY_SMELT_TOTAL", "SMELT" -> simple(Type.SMELT_ALL);
            case "ACTIVITY_BREW_TOTAL", "BREW", "BREW_COUNT" -> simple(Type.BREW_COUNT);
            case "ACTIVITY_ENCHANT_TOTAL", "ENCHANT" -> simple(Type.ENCHANT_ALL);
            case "ACTIVITY_HARVEST_TOTAL", "HARVEST" -> simple(Type.HARVEST_ALL);
            case "ACTIVITY_BREED_TOTAL", "BREED" -> simple(Type.BREED_ALL);
            case "ACTIVITY_FISH_TOTAL", "FISH" -> simple(Type.FISH_ALL);
            case "ACTIVITY_PICKUP_TOTAL", "ACTIVITY_DROP_TOTAL", "ITEM" -> simple(Type.ITEM_ALL);
            case "ACTIVITY_PROJECTILE_TOTAL", "PROJECTILE" -> simple(Type.PROJECTILE_ALL);
            default -> parseQualified(normalized);
        };
    }

    private static @Nullable PlayerStatsResetTarget parseQualified(String normalized) {
        int separatorIndex = normalized.indexOf(':');
        if (separatorIndex <= 0 || separatorIndex >= normalized.length() - 1) {
            return null;
        }

        String prefix = normalized.substring(0, separatorIndex);
        String qualifier = normalized.substring(separatorIndex + 1);
        if (qualifier.isBlank()) {
            return null;
        }

        return switch (prefix) {
            case "BLOCK" -> qualified(Type.BLOCK_MATERIAL, qualifier);
            case "ITEM" -> qualified(Type.ITEM_MATERIAL, qualifier);
            case "PROJECTILE" -> qualified(Type.PROJECTILE_TYPE, qualifier);
            case "CRAFT" -> qualified(Type.CRAFT_MATERIAL, qualifier);
            case "SMELT" -> qualified(Type.SMELT_MATERIAL, qualifier);
            case "ENCHANT" -> qualified(Type.ENCHANTMENT, qualifier);
            case "ENCHANT_ITEM" -> qualified(Type.ENCHANT_ITEM_MATERIAL, qualifier);
            case "HARVEST" -> qualified(Type.HARVEST_MATERIAL, qualifier);
            case "BREED" -> qualified(Type.BREED_ENTITY, qualifier);
            case "FISH" -> qualified(Type.FISH_MATERIAL, qualifier);
            case "KILL" -> qualified(Type.KILL_TARGET, qualifier);
            case "ENTITY_DAMAGE" -> qualified(Type.ENTITY_DAMAGE_TARGET, qualifier);
            case "COMBAT_MOB", "COMBAT_PLAYER" -> qualified(Type.COMBAT_TARGET, qualifier);
            default -> null;
        };
    }

    private static PlayerStatsResetTarget simple(Type type) {
        return new PlayerStatsResetTarget(type, null, type.name());
    }

    private static PlayerStatsResetTarget qualified(Type type, String qualifier) {
        return new PlayerStatsResetTarget(type, qualifier, type.name() + ":" + qualifier);
    }

    /**
     * Supported stats reset target types.
     */
    public enum Type {
        PLAY_TIME,
        DISTANCE,
        DEATHS,
        RESPAWNS,
        CHAT_COUNT,
        SLEEP_COUNT,
        PORTAL_COUNT,
        BREW_COUNT,
        BLOCK_MATERIAL,
        ENTITY_DAMAGE_TARGET,
        KILL_TARGET,
        COMBAT_TARGET,
        CRAFT_ALL,
        CRAFT_MATERIAL,
        SMELT_ALL,
        SMELT_MATERIAL,
        ENCHANT_ALL,
        ENCHANTMENT,
        ENCHANT_ITEM_MATERIAL,
        HARVEST_ALL,
        HARVEST_MATERIAL,
        BREED_ALL,
        BREED_ENTITY,
        FISH_ALL,
        FISH_MATERIAL,
        ITEM_ALL,
        ITEM_MATERIAL,
        PROJECTILE_ALL,
        PROJECTILE_TYPE
    }
}
