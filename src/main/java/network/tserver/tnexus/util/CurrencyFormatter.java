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
}
