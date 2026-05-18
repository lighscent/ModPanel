package com.xl1te.modpanel.database;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.utils.BestLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.xl1te.modpanel.database.repository.IPBanRepository;
import com.xl1te.modpanel.database.repository.WebWhitelistRepository;
import com.xl1te.modpanel.database.repository.UserRepository;
import com.xl1te.modpanel.database.repository.PermissionRepository;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    public enum Dialect {
        H2, MYSQL
    }

    private final Main plugin;
    private final BestLogger logger;
    private HikariDataSource dataSource;
    private Dialect dialect;
    private WebWhitelistRepository webWhitelistRepository;
    private IPBanRepository ipBanRepository;
    private UserRepository userRepository;
    private PermissionRepository permissionRepository;

    public DatabaseManager(Main plugin, BestLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public void initialize() {
        try {
            String storageType = plugin.getConfig().getString("storage.type", "h2").toLowerCase();
            HikariConfig config = new HikariConfig();

            switch (storageType) {
                case "mysql":
                case "mariadb":
                    dialect = Dialect.MYSQL;
                    String host = plugin.getConfig().getString("storage.mysql.host", "127.0.0.1");
                    int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
                    String database = plugin.getConfig().getString("storage.mysql.database", "modpanel");
                    String user = plugin.getConfig().getString("storage.mysql.username", "root");
                    String pass = plugin.getConfig().getString("storage.mysql.password", "");
                    boolean ssl = plugin.getConfig().getBoolean("storage.mysql.use-ssl", false);
                    config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=" + ssl + "&characterEncoding=utf8mb4");
                    config.setDriverClassName("org.mariadb.jdbc.Driver");
                    config.setUsername(user);
                    config.setPassword(pass);
                    logger.info("Using MySQL/MariaDB storage: " + host + ":" + port + "/" + database);
                    break;
                default:
                    dialect = Dialect.H2;
                    File dataFolder = plugin.getDataFolder();
                    File dataDir = new File(dataFolder, "data");
                    dataDir.mkdirs();
                    String dbPath = new File(dataDir, "database").getAbsolutePath();
                    config.setJdbcUrl("jdbc:h2:" + dbPath);
                    config.setDriverClassName("org.h2.Driver");
                    logger.info("Using H2 storage: " + dbPath);
                    break;
            }

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
            this.userRepository = new UserRepository(this);
            this.permissionRepository = new PermissionRepository(this);

        } catch (Exception e) {
            logger.severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        String autoIncrement = (dialect == Dialect.MYSQL) ? "INT AUTO_INCREMENT" : "INT AUTO_INCREMENT";
        String timestamp = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
        String booleanType = "BOOLEAN";
        String varchar = "VARCHAR";

        executeUpdate("CREATE TABLE IF NOT EXISTS web_whitelist (" +
                "id " + autoIncrement + " PRIMARY KEY, " +
                "ip " + varchar + "(45) NOT NULL UNIQUE, " +
                "added_at " + timestamp + ")");

        executeUpdate("CREATE TABLE IF NOT EXISTS web_bans (" +
                "ip " + varchar + "(45) PRIMARY KEY, " +
                "failure_count INT NOT NULL DEFAULT 0, " +
                "ban_count INT NOT NULL DEFAULT 0, " +
                "banned_until TIMESTAMP NULL, " +
                "permanent " + booleanType + " NOT NULL DEFAULT FALSE)");

        executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                "id " + autoIncrement + " PRIMARY KEY, " +
                "username " + varchar + "(50) NOT NULL UNIQUE, " +
                "password_hash " + varchar + "(255) NOT NULL, " +
                "role " + varchar + "(20) NOT NULL DEFAULT 'moderator', " +
                "enabled " + booleanType + " NOT NULL DEFAULT TRUE, " +
                "created_at " + timestamp + ", " +
                "last_login TIMESTAMP NULL)");

        executeUpdate("CREATE TABLE IF NOT EXISTS user_permissions (" +
                "user_id INT NOT NULL, " +
                "permission " + varchar + "(100) NOT NULL, " +
                "PRIMARY KEY (user_id, permission), " +
				(constraintClause("user_permissions", "user_id", "users(id)")) + ")");

        logger.info("Database tables created/verified.");
    }

    private String constraintClause(String table, String col, String ref) {
        if (dialect == Dialect.MYSQL) {
            return "FOREIGN KEY (" + col + ") REFERENCES " + ref + " ON DELETE CASCADE";
        }
        return "FOREIGN KEY (" + col + ") REFERENCES " + ref + " ON DELETE CASCADE";
    }

    public Dialect getDialect() { return dialect; }

    public WebWhitelistRepository getWebWhitelistRepository() {
        if (webWhitelistRepository == null) throw new IllegalStateException("DatabaseManager not initialized");
        return webWhitelistRepository;
    }

    public IPBanRepository getIpBanRepository() {
        if (ipBanRepository == null) throw new IllegalStateException("DatabaseManager not initialized");
        return ipBanRepository;
    }

    public UserRepository getUserRepository() {
        if (userRepository == null) throw new IllegalStateException("DatabaseManager not initialized");
        return userRepository;
    }

    public PermissionRepository getPermissionRepository() {
        if (permissionRepository == null) throw new IllegalStateException("DatabaseManager not initialized");
        return permissionRepository;
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) throw new SQLException("Database not initialized");
        return dataSource.getConnection();
    }

    public void executeUpdate(String sql) throws SQLException {
        try (Connection conn = getConnection(); var stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }
}
