package network.tserver.tnexus.listener;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.PaymentManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Delivers deferred payment notifications when recipients log in.
 */
public final class PaymentNotificationListener implements Listener {

    private final PaymentManager paymentManager;

    /**
     * Creates a new notification listener.
     *
     * @param plugin plugin instance
     */
    public PaymentNotificationListener(TNexus plugin) {
        this.paymentManager = plugin.getPaymentManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.paymentManager.deliverPendingNotifications(event.getPlayer());
    }
}
