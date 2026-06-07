package network.tserver.tnexus.listener;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.PlayerStatsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * プレイヤーによるキル統計を追跡するリスナー。
 */
public final class PlayerKillStatsListener implements Listener {

    private final PlayerStatsManager playerStatsManager;

    /**
     * 新しいプレイヤーキル統計リスナーを作成します。
     *
     * @param plugin プラグインのインスタンス
     */
    public PlayerKillStatsListener(TNexus plugin) {
        this.playerStatsManager = plugin.getPlayerStatsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        // 対象がプレイヤーの場合はUUID文字列、それ以外（Mob）の場合はエンティティタイプ名をターゲット識別子とする
        String targetIdentifier = event.getEntity() instanceof Player targetPlayer
                ? targetPlayer.getUniqueId().toString()
                : event.getEntity().getType().name();
        this.playerStatsManager.recordKill(killer, targetIdentifier);
    }
}
