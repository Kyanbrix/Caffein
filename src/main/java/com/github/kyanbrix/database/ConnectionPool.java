package com.github.kyanbrix.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class ConnectionPool {


    private static final HikariConfig hikariConfig = new HikariConfig();
    private static final HikariDataSource dataSource;

    static {

        hikariConfig.setUsername(System.getenv("postgres"));
        hikariConfig.setPassword("hteagfLpsQvvAsRNwYDfpTxMoQvNVjuS");
        hikariConfig.setJdbcUrl("jdbc:postgresql://mainline.proxy.rlwy.net:24727/railway");
        hikariConfig.addDataSourceProperty("cachePrepStmts",true);
        hikariConfig.addDataSourceProperty( "cachePrepStmts" , "true" );
        hikariConfig.addDataSourceProperty( "prepStmtCacheSize" , "250" );
        hikariConfig.addDataSourceProperty( "prepStmtCacheSqlLimit" , "2048" );

        dataSource = new HikariDataSource(hikariConfig);
    }

    private ConnectionPool() {}


    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }




}
