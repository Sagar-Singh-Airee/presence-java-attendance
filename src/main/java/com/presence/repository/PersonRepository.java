/**
 * Purpose: Reads and writes registered people in SQLite.
 * Why it is used: It keeps SQL database work separate from the website and face-recognition logic.
 */
package com.presence.repository;

import com.presence.model.Person;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PersonRepository {
    private final JdbcTemplate jdbc;

    public PersonRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Person create(String name) {
        String createdAt = LocalDateTime.now().withNano(0).toString();
        try {
            Long id = jdbc.queryForObject("INSERT INTO persons(name, created_at) VALUES (?, ?) RETURNING id",
                    Long.class, name, createdAt);
            if (id == null) throw new IllegalStateException("The database did not return a person ID.");
            return new Person(id, name, LocalDateTime.parse(createdAt), null);
        } catch (DataAccessException exception) {
            // SQLite's JDBC driver reports this INSERT ... RETURNING constraint as an
            // UncategorizedSQLException, so inspect the driver message deliberately.
            String details = exception.getMostSpecificCause().getMessage();
            if (details != null && details.contains("SQLITE_CONSTRAINT_UNIQUE")) {
                throw new DuplicatePersonException(name);
            }
            throw exception;
        }
    }

    public Optional<Person> findByNormalizedName(String name) {
        List<Person> people = jdbc.query("SELECT id, name, created_at FROM persons WHERE name = ? COLLATE NOCASE",
                (rs, row) -> new Person(rs.getLong("id"), rs.getString("name"),
                        LocalDateTime.parse(rs.getString("created_at")), null), name);
        return people.stream().findFirst();
    }

    public Optional<Person> findById(long id) {
        List<Person> people = jdbc.query("SELECT id, name, created_at FROM persons WHERE id = ?",
                (rs, row) -> new Person(rs.getLong("id"), rs.getString("name"),
                        LocalDateTime.parse(rs.getString("created_at")), null), id);
        return people.stream().findFirst();
    }

    public List<Person> findAllWithPreview() {
        return jdbc.query("""
                SELECT p.id, p.name, p.created_at, MIN(s.image_path) AS sample_image_path
                FROM persons p LEFT JOIN face_samples s ON s.person_id = p.id
                GROUP BY p.id, p.name, p.created_at ORDER BY p.name COLLATE NOCASE
                """, (rs, row) -> new Person(rs.getLong("id"), rs.getString("name"),
                LocalDateTime.parse(rs.getString("created_at")), rs.getString("sample_image_path")));
    }

    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM persons", Integer.class);
        return count == null ? 0 : count;
    }
    public int delete(long id) { return jdbc.update("DELETE FROM persons WHERE id = ?", id); }

    public static class DuplicatePersonException extends RuntimeException {
        public DuplicatePersonException(String name) { super("A person named '" + name + "' is already registered."); }
    }
}
