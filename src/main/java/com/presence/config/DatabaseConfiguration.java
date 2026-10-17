/**
 * Purpose: Configures the SQLite database connection.
 * Why it is used: It lets the rest of the application save and read local data reliably.
 */
package com.presence.config;

// Bean: Tells Spring to register the returned object as a managed bean in the application context.
import org.springframework.context.annotation.Bean;
// Configuration: Identifies this class as a source of Spring bean definitions.
import org.springframework.context.annotation.Configuration;
// JdbcTemplate: Spring utility class that simplifies SQL execution and prevents boilerplate JDBC code.
import org.springframework.jdbc.core.JdbcTemplate;
// SQLiteConfig: Used to customize SQLite settings, such as enforcing foreign key constraints.
import org.sqlite.SQLiteConfig;
// SQLiteDataSource: The SQLite-specific implementation of the JDBC DataSource interface.
import org.sqlite.SQLiteDataSource;

// Objects: Provides null-safety checks to guarantee required dependencies are present.
import java.util.Objects;
// DataSource: Standard Java interface for establishing connections to any SQL database.
import javax.sql.DataSource;

@Configuration
public class DatabaseConfiguration {

    /**
     * Creates and configures the SQLite database connection pool.
     * Enforces foreign key constraints so deleting a person cleans up their related records automatically.
     */
    @Bean
    DataSource dataSource(DataPaths paths) {
        SQLiteConfig config = new SQLiteConfig();
        // Enables SQLite foreign key enforcement, which is off by default in SQLite.
        config.enforceForeignKeys(true);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        // Points SQLite to the local file path: data/presence.db
        dataSource.setUrl("jdbc:sqlite:" + paths.database());
        return dataSource;
    }

    /**
     * Creates the JdbcTemplate used by all repositories to execute SQL queries and updates.
     * Objects.requireNonNull ensures the injected DataSource is never null before constructing JdbcTemplate.
     */
    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) { 
        return new JdbcTemplate(Objects.requireNonNull(dataSource)); 
    }
}
