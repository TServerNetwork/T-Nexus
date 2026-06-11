package network.tserver.tnexus.listener;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerEntityDamageStatsListenerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldRecordDirectPlayerDamageDealtAndMobDamageTaken() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock attacker = this.server.addPlayer("Attacker");
        PlayerMock victim = this.server.addPlayer("Victim");
        World world = attacker.getWorld();
        Zombie zombie = world.spawn(new Location(world, 5.0D, 64.0D, 5.0D), Zombie.class);

        this.server.getPluginManager().callEvent(new EntityDamageByEntityEvent(
                attacker,
                zombie,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                5.5D));
        this.server.getPluginManager().callEvent(new EntityDamageByEntityEvent(
                zombie,
                victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                3.25D));
        plugin.getPlayerStatsManager().flushPendingEntityDamageStats().join();

        assertEquals(5.5D, readEntityDamage(plugin, attacker, "ZOMBIE", "damage_dealt"));
        assertEquals(3.25D, readEntityDamage(plugin, victim, "ZOMBIE", "damage_taken"));
    }

    @Test
    void shouldRecordProjectileDamageUsingShooterPlayerIdentifier() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock shooter = this.server.addPlayer("Shooter");
        PlayerMock victim = this.server.addPlayer("Target");
        World world = shooter.getWorld();
        Arrow arrow = world.spawn(new Location(world, 3.0D, 64.0D, 3.0D), Arrow.class);
        arrow.setShooter(shooter);

        this.server.getPluginManager().callEvent(new EntityDamageByEntityEvent(
                arrow,
                victim,
                EntityDamageEvent.DamageCause.PROJECTILE,
                4.0D));
        plugin.getPlayerStatsManager().flushPendingEntityDamageStats().join();

        String shooterId = shooter.getUniqueId().toString();
        assertEquals(4.0D, readEntityDamage(plugin, shooter, victim.getUniqueId().toString(), "damage_dealt"));
        assertEquals(4.0D, readEntityDamage(plugin, victim, shooterId, "damage_taken"));
    }

    @Test
    void shouldRecordPlayerIgnitedTntAsPlayerDamage() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock igniter = this.server.addPlayer("Igniter");
        PlayerMock victim = this.server.addPlayer("BlastVictim");
        World world = igniter.getWorld();
        TNTPrimed tnt = world.spawn(new Location(world, 6.0D, 64.0D, 6.0D), TNTPrimed.class);
        tnt.setSource(igniter);

        this.server.getPluginManager().callEvent(new EntityDamageByEntityEvent(
                tnt,
                victim,
                EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                7.0D));
        plugin.getPlayerStatsManager().flushPendingEntityDamageStats().join();

        String igniterId = igniter.getUniqueId().toString();
        assertEquals(7.0D, readEntityDamage(plugin, igniter, victim.getUniqueId().toString(), "damage_dealt"));
        assertEquals(7.0D, readEntityDamage(plugin, victim, igniterId, "damage_taken"));
    }

    @Test
    void shouldIgnoreCancelledZeroDamageAndTamedWolfDamage() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock owner = this.server.addPlayer("Owner");
        PlayerMock victim = this.server.addPlayer("IgnoredVictim");
        World world = owner.getWorld();
        Zombie zombie = world.spawn(new Location(world, 8.0D, 64.0D, 8.0D), Zombie.class);
        Wolf wolf = world.spawn(new Location(world, 9.0D, 64.0D, 9.0D), Wolf.class);
        wolf.setOwner(owner);
        wolf.setTamed(true);

        EntityDamageByEntityEvent cancelledEvent = new EntityDamageByEntityEvent(
                owner,
                zombie,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                4.0D);
        cancelledEvent.setCancelled(true);
        this.server.getPluginManager().callEvent(cancelledEvent);

        this.server.getPluginManager().callEvent(new EntityDamageByEntityEvent(
                owner,
                zombie,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                0.0D));
        this.server.getPluginManager().callEvent(new EntityDamageByEntityEvent(
                wolf,
                victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                2.0D));
        plugin.getPlayerStatsManager().flushPendingEntityDamageStats().join();

        assertEquals(0.0D, readEntityDamage(plugin, owner, "ZOMBIE", "damage_dealt"));
        assertEquals(0.0D, readEntityDamage(plugin, victim, "WOLF", "damage_taken"));
    }

    @Test
    void shouldRecordFallDamageAsEnvironmentDamageTaken() throws Exception {
        TNexus plugin = loadPlugin();
        PlayerMock victim = this.server.addPlayer("FallVictim");

        this.server.getPluginManager().callEvent(new EntityDamageEvent(
                victim,
                EntityDamageEvent.DamageCause.FALL,
                6.5D));
        plugin.getPlayerStatsManager().flushPendingEntityDamageStats().join();

        assertEquals(6.5D, readEntityDamage(plugin, victim, "FALL", "damage_taken"));
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
    }

    private double readEntityDamage(TNexus plugin, Player player, String identifier, String columnName) {
        return plugin.getDatabaseManager().queryAsync(() -> {
            try (var connection = plugin.getDatabaseManager().getConnection();
                 var statement = connection.prepareStatement(
                         "SELECT COALESCE(SUM(" + columnName + "), 0) AS total FROM tnexus_entity_damage_stats "
                                 + "WHERE player_uuid = ? AND entity_type = ?")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, identifier);
                try (var resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getDouble("total") : 0.0D;
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).join();
    }
}
