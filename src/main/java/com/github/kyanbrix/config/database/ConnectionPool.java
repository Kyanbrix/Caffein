package com.github.kyanbrix.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class ConnectionPool {

    private final HikariDataSource dataSource;

    public ConnectionPool() {


        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setUsername(System.getenv("DB_USER"));
        hikariConfig.setPassword(System.getenv("DB_PASSWORD"));
        hikariConfig.setJdbcUrl(System.getenv("DB_URL"));
        hikariConfig.setSchema("public");
        hikariConfig.setIdleTimeout(60000);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.setMaximumPoolSize(10);

        dataSource = new HikariDataSource(hikariConfig);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        dataSource.close();
    }

}
