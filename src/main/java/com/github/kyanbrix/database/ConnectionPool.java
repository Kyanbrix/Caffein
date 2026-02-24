package com.github.kyanbrix.database;

import com.github.kyanbrix.Caffein;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class ConnectionPool {

    private final HikariDataSource dataSource;

    public ConnectionPool() throws IOException {
        String dbUrl = requireEnv("DB_URL");
        String dbUser = requireEnv("DB_USER");
        String dbPassword = requireEnv("DB_PASSWORD");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setUsername(dbUser);
        hikariConfig.setPassword(dbPassword);
        hikariConfig.setJdbcUrl(dbUrl);
        hikariConfig.setSchema("public");
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.setMaximumPoolSize(5);

        dataSource = new HikariDataSource(hikariConfig);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        dataSource.close();
    }

    private static String requireEnv(String key) throws IOException{

        Properties properties = new Properties();
        try (InputStream input = Caffein.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                properties.load(input);
                String keyFromResource = properties.getProperty(key);
                if (keyFromResource != null && !keyFromResource.isBlank()) {
                    return keyFromResource;
                }
            }
        }


        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }
}
