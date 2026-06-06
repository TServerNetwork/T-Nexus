package network.tserver.tnexus.util;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Utility methods for filtering tab-completion candidates.
 */
public final class TabCompleterUtil {

    private TabCompleterUtil() {
    }

    /**
     * Returns online player names matching the provided prefix.
     *
     * @param arg current input
     * @return filtered player names
     */
    public static List<String> filterPlayers(String arg) {
        String normalizedArg = normalize(arg);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> normalize(name).startsWith(normalizedArg))
                .toList();
    }

    /**
     * Returns candidates matching the provided prefix.
     *
     * @param candidates candidate values
     * @param arg current input
     * @return filtered candidates
     */
    public static List<String> filter(Collection<String> candidates, String arg) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedArg = normalize(arg);
        return candidates.stream()
                .filter(candidate -> normalize(candidate).startsWith(normalizedArg))
                .toList();
    }

    private static String normalize(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT);
    }
}
