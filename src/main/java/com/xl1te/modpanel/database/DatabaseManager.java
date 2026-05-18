package com.xl1te.modpanel.database;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.utils.BestLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.xl1te.modpanel.database.repository.IPBanRepository;
import com.xl1te.modpanel.database.repository.WebWhitelistRepository;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    private final Main plugin;
    private final BestLogger logger;
    private HikariDataSource dataSource;
    private WebWhitelistRepository webWhitelistRepository;
    private IPBanRepository ipBanRepository;

    public DatabaseManager(Main plugin, BestLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public void initialize() {
        try {
            HikariConfig config = new HikariConfig();

            File dataFolder = plugin.getDataFolder();
            File dataDir = new File(dataFolder, "data");
            dataDir.mkdirs();
            String dbPath = new File(dataDir, "database").getAbsolutePath();
            config.setJdbcUrl("jdbc:h2:" + dbPath);

            config.setDriverClassName("org.h2.Driver");

            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);

            try (Connection conn = getConnection()) {
                logger.info("Database connection established successfully.");
            }

            createTables();
            this.webWhitelistRepository = new WebWhitelistRepository(this);
            this.ipBanRepository = new IPBanRepository(this);

        } catch (Exception e) {
            logger.severe("Failed to initialize database: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        executeUpdate("""
                CREATE TABLE IF NOT EXISTS web_whitelist (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    ip VARCHAR(45) NOT NULL UNIQUE,
                    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

        executeUpdate("""
                CREATE TABLE IF NOT EXISTS web_bans (
                    ip VARCHAR(45) PRIMARY KEY,
                    failure_count INT NOT NULL DEFAULT 0,
                    ban_count INT NOT NULL DEFAULT 0,
                    banned_until TIMESTAMP NULL,
                    permanent BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);

        logger.info("Database tables web_whitelist and web_bans created/verified.");
    }

    public WebWhitelistRepository getWebWhitelistRepository() {
        if (webWhitelistRepository == null) {
            throw new IllegalStateException("DatabaseManager not initialized");
        }
        return webWhitelistRepository;
    }

    public IPBanRepository getIpBanRepository() {
        if (ipBanRepository == null) {
            throw new IllegalStateException("DatabaseManager not initialized");
        }
        return ipBanRepository;
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database not initialized");
        }
        return dataSource.getConnection();
    }

    public void executeUpdate(String sql) throws SQLException {
        try (Connection conn = getConnection();
                var stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }
}