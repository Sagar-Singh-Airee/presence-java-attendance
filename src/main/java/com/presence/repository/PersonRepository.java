/**
 * Purpose: Reads and writes registered people in SQLite.
 * Why it is used: It keeps SQL database work separate from the website and face-recognition logic.
 */
package com.presence.repository;

// Person: Data model representing a registered person (id, name, registration timestamp, preview photo).
import com.presence.model.Person;
// DataAccessException: Spring's base exception hierarchy for all SQL and database access failures.
import org.springframework.dao.DataAccessException;
// JdbcTemplate: Spring utility for executing SQL queries and managing database resources automatically.
import org.springframework.jdbc.core.JdbcTemplate;
// Repository: Marks this class as a Spring Data access component.
import org.springframework.stereotype.Repository;

// LocalDateTime: Stores the exact timestamp when a person was enrolled into the library.
import java.time.LocalDateTime;
// List: Represents ordered collections of Person objects returned by queries.
import java.util.List;
// Optional: Container object that may or may not contain a non-null Person value.
import java.util.Optional;

@Repository
public class PersonRepository {
    private final JdbcTemplate jdbc;

    public PersonRepository(JdbcTemplate jdbc) { 
        this.jdbc = jdbc; 
    }

    /**
     * Inserts a new person into SQLite and returns the generated Person record with its auto-incremented ID.
     */
    public Person create(String name) {
        String createdAt = LocalDateTime.now().withNano(0).toString();
        try {
            Long id = jdbc.queryForObject("INSERT INTO persons(name, created_at) VALUES (?, ?) RETURNING id",
                    Long.class, name, createdAt);
            if (id == null) {
                throw new IllegalStateException("The database did not return a person ID.");
            }
            return new Person(id, name, LocalDateTime.parse(createdAt), null);
        } catch (DataAccessException exception) {
            // Checks if SQLite reported a unique constraint violation on the person's name.
            String details = exception.getMostSpecificCause().getMessage();
            if (details != null && details.contains("SQLITE_CONSTRAINT_UNIQUE")) {
                throw new DuplicatePersonException(name);
            }
            throw exception;
        }
    }

    /**
     * Searches for a person by their name, ignoring uppercase/lowercase differences (COLLATE NOCASE).
     */
    public Optional<Person> findByNormalizedName(String name) {
        List<Person> people = jdbc.query("SELECT id, name, created_at FROM persons WHERE name = ? COLLATE NOCASE",
                (rs, row) -> new Person(rs.getLong("id"), rs.getString("name"),
                        LocalDateTime.parse(rs.getString("created_at")), null), name);
        return people.stream().findFirst();
    }

    /**
     * Finds a single person by their unique primary key ID.
     */
    public Optional<Person> findById(long id) {
        List<Person> people = jdbc.query("SELECT id, name, created_at FROM persons WHERE id = ?",
                (rs, row) -> new Person(rs.getLong("id"), rs.getString("name"),
                        LocalDateTime.parse(rs.getString("created_at")), null), id);
        return people.stream().findFirst();
    }

    /**
     * Retrieves all registered people alongside one of their sample photos for display on the registered faces page.
     */
    public List<Person> findAllWithPreview() {
        return jdbc.query("""
                SELECT p.id, p.name, p.created_at, MIN(s.image_path) AS sample_image_path
                FROM persons p LEFT JOIN face_samples s ON s.person_id = p.id
                GROUP BY p.id, p.name, p.created_at ORDER BY p.name COLLATE NOCASE
                """, (rs, row) -> new Person(rs.getLong("id"), rs.getString("name"),
                LocalDateTime.parse(rs.getString("created_at")), rs.getString("sample_image_path")));
    }

    /**
     * Counts the total number of registered people in the database.
     * Stores the query result in an Integer object first to check for null before converting to primitive int,
     * preventing potential NullPointerExceptions if the table is empty or the driver returns null.
     */
    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM persons", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * Deletes a person by their ID. Child face sample rows are automatically cleaned up by SQLite cascading foreign keys.
     */
    public int delete(long id) { 
        return jdbc.update("DELETE FROM persons WHERE id = ?", id); 
    }

    /**
     * Custom runtime exception thrown when attempting to register a person whose name already exists.
     */
    public static class DuplicatePersonException extends RuntimeException {
        public DuplicatePersonException(String name) { 
            super("A person named '" + name + "' is already registered."); 
        }
    }
}
