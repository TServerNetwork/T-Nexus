package network.tserver.tnexus.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.database.migration.MigrationManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages the plugin database connection pool and async database execution.
 */
public class DatabaseManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Logger logger;
    private final MigrationManager migrationManager;
    private final Object asyncTaskMonitor;
    private HikariDataSource dataSource;
    private int activeAsyncTaskCount;
    private boolean acceptingAsyncTasks;

    /**
     * Creates a new database manager.
     *
     * @param plugin plugin instance
     * @param configManager config manager
     */
    public DatabaseManager(JavaPlugin plugin, ConfigManager configManager) {
        this(plugin, configManager, null);
    }

    /**
     * Creates a new database manager with an explicit migration manager.
     *
     * @param plugin plugin instance
     * @param configManager config manager
     * @param migrationManager migration manager override
     */
    public DatabaseManager(
            JavaPlugin plugin,
            ConfigManager configManager,
            MigrationManager migrationManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.logger = plugin.getLogger();
        this.migrationManager = migrationManager == null
                ? new MigrationManager(plugin, this::getConnection, configManager)
                : migrationManager;
        this.asyncTaskMonitor = new Object();
        this.acceptingAsyncTasks = true;
    }

    /**
     * Initializes the HikariCP data source and applies pending migrations.
     *
     * @return {@code true} when initialization succeeds
     */
    public synchronized boolean initialize() {
        if (this.dataSource != null && !this.dataSource.isClosed()) {
            return true;
        }

        try {
            HikariConfig hikariConfig = createHikariConfig(this.configManager.getDatabaseSettings());
            this.dataSource = new HikariDataSource(hikariConfig);
            try (Connection ignored = this.dataSource.getConnection()) {
                // Force a real connection early so startup failures are logged immediately.
            }
            this.migrationManager.migrate();
            synchronized (this.asyncTaskMonitor) {
                this.acceptingAsyncTasks = true;
            }
            return true;
        } catch (Exception exception) {
            this.logger.log(Level.SEVERE, "Failed to initialize the database connection pool.", exception);
            shutdown();
            return false;
        }
    }

    /**
     * Closes the data source if it is active.
     */
    public synchronized void shutdown() {
        synchronized (this.asyncTaskMonitor) {
            this.acceptingAsyncTasks = false;
        }
        if (this.dataSource != null) {
            this.dataSource.close();
            this.dataSource = null;
        }
    }

    /**
     * Returns a JDBC connection from the active pool.
     *
     * @return pooled connection
     * @throws SQLException when the pool is unavailable or a connection cannot be acquired
     */
    public Connection getConnection() throws SQLException {
        HikariDataSource activeDataSource = this.dataSource;
        if (activeDataSource == null || activeDataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized.");
        }
        return activeDataSource.getConnection();
    }

    /**
     * Runs a task asynchronously on the Bukkit scheduler.
     *
     * @param runnable task to run
     */
    public void executeAsync(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, runnable);
    }

    /**
     * Runs a task synchronously on the current thread.
     *
     * @param task task to run
     */
    public void executeSync(Runnable task) {
        try {
            task.run();
        } catch (Exception exception) {
            this.plugin.getLogger().severe("Sync DB operation failed: " + exception.getMessage());
            throw exception;
        }
    }

    /**
     * Runs a supplier asynchronously and returns its completion future.
     *
     * @param supplier query supplier
     * @param <T> result type
     * @return future for the supplier result
     */
    public <T> CompletableFuture<T> queryAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        synchronized (this.asyncTaskMonitor) {
            if (!this.acceptingAsyncTasks) {
                future.completeExceptionally(new IllegalStateException("Database async tasks are temporarily paused."));
                return future;
            }
            this.activeAsyncTaskCount++;
        }
        executeAsync(() -> {
            try {
                future.complete(supplier.get());
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            } finally {
                finishAsyncTask();
            }
        });
        return future;
    }

    /**
     * Runs a supplier synchronously on the current thread and returns a completed future.
     *
     * @param supplier query supplier
     * @param <T> result type
     * @return completed future for the supplier result
     */
    public <T> CompletableFuture<T> querySync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executeSync(() -> future.complete(supplier.get()));
        } catch (Exception exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }

    /**
     * Returns whether the connection pool is active.
     *
     * @return {@code true} when initialized
     */
    public boolean isInitialized() {
        return this.dataSource != null && !this.dataSource.isClosed();
    }

    /**
     * Prevents new async database work and waits for active async tasks to finish.
     *
     * @param timeout maximum time to wait
     * @param unit timeout unit
     * @return {@code true} when all active async tasks completed before the timeout
     */
    public boolean drainAsyncTasks(long timeout, TimeUnit unit) {
        long timeoutMillis = unit.toMillis(timeout);
        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (this.asyncTaskMonitor) {
            this.acceptingAsyncTasks = false;
            while (this.activeAsyncTaskCount > 0) {
                long waitMillis = deadline - System.currentTimeMillis();
                if (waitMillis <= 0L) {
                    this.acceptingAsyncTasks = true;
                    return false;
                }
                try {
                    this.asyncTaskMonitor.wait(waitMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    this.acceptingAsyncTasks = true;
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Returns the configured table prefix.
     *
     * @return table prefix
     */
    public String getTablePrefix() {
        return this.configManager.getDatabaseSettings().tablePrefix();
    }

    private void finishAsyncTask() {
        synchronized (this.asyncTaskMonitor) {
            this.activeAsyncTaskCount = Math.max(0, this.activeAsyncTaskCount - 1);
            if (this.activeAsyncTaskCount == 0) {
                this.asyncTaskMonitor.notifyAll();
            }
        }
    }

    private HikariConfig createHikariConfig(ConfigManager.DatabaseSettings settings) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("T-Nexus-HikariPool");
        hikariConfig.setMaximumPoolSize(Math.max(1, settings.poolSize()));
        hikariConfig.setJdbcUrl(resolveJdbcUrl(settings));
        hikariConfig.setUsername(settings.username());
        hikariConfig.setPassword(settings.password());
        hikariConfig.setInitializationFailTimeout(1L);
        hikariConfig.setConnectionTimeout(5000L);

        String driverClassName = settings.driverClassName();
        if (driverClassName != null && !driverClassName.isBlank()) {
            hikariConfig.setDriverClassName(driverClassName);
        }
        return hikariConfig;
    }

    private String resolveJdbcUrl(ConfigManager.DatabaseSettings settings) {
        if (settings.jdbcUrl() != null && !settings.jdbcUrl().isBlank()) {
            return settings.jdbcUrl();
        }
        return "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8"
                .formatted(settings.host(), settings.port(), settings.name());
    }
}
