/**
 * Purpose: Configures the SQLite database connection.
 * Why it is used: It lets the rest of the application save and read local data reliably.
 */
package com.presence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.util.Objects;
import javax.sql.DataSource;

@Configuration
public class DatabaseConfiguration {
    @Bean
    DataSource dataSource(DataPaths paths) {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + paths.database());
        return dataSource;
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(Objects.requireNonNull(dataSource)); }
}
