package network.tserver.tnexus.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import network.tserver.tnexus.TNexus;

/**
 * Formats currency values using the configured T-Nexus symbol.
 */
public final class CurrencyFormatter {

    private static final ThreadLocal<DecimalFormat> FORMATTER = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.##", symbols);
        decimalFormat.setGroupingUsed(true);
        return decimalFormat;
    });
    private static final ThreadLocal<DecimalFormat> COMPACT_FORMATTER = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat decimalFormat = new DecimalFormat("0.#", symbols);
        decimalFormat.setGroupingUsed(false);
        return decimalFormat;
    });

    private CurrencyFormatter() {
    }

    /**
     * Formats an amount with the configured currency symbol.
     *
     * @param plugin plugin instance
     * @param amount amount to format
     * @return formatted amount with symbol
     */
    public static String format(TNexus plugin, double amount) {
        String symbol = plugin.getConfigManager().getString("tnexus.economy.currency-symbol", "¥");
        return symbol + FORMATTER.get().format(amount);
    }

    /**
     * Formats an amount with compact K/M/B suffixes for space-constrained surfaces.
     *
     * @param plugin plugin instance
     * @param amount amount to format
     * @return compact formatted amount with symbol
     */
    public static String formatCompact(TNexus plugin, double amount) {
        String symbol = plugin.getConfigManager().getString("tnexus.economy.currency-symbol", "¥");
        double absoluteAmount = Math.abs(amount);
        double scaledAmount = amount;
        String suffix = "";

        if (absoluteAmount >= 1_000_000_000D) {
            scaledAmount = amount / 1_000_000_000D;
            suffix = "B";
        } else if (absoluteAmount >= 1_000_000D) {
            scaledAmount = amount / 1_000_000D;
            suffix = "M";
        } else if (absoluteAmount >= 1_000D) {
            scaledAmount = amount / 1_000D;
            suffix = "K";
        }

        return symbol + COMPACT_FORMATTER.get().format(scaledAmount) + suffix;
    }
}
