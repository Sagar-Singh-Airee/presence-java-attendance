/**
 * Purpose: Reads and writes attendance data in SQLite.
 * Why it is used: It provides attendance history, filtering, and daily attendance checks.
 */
package com.presence.repository;

// AttendanceRecord: Data transfer record holding attendance id, person id, name, date, and time.
import com.presence.model.AttendanceRecord;
// JdbcTemplate: Spring Framework JDBC helper used to execute SQL queries and parameter mappings safely.
import org.springframework.jdbc.core.JdbcTemplate;
// Repository: Marks this class as a Spring Data repository handling database operations.
import org.springframework.stereotype.Repository;

// LocalDate: Represents the date component (YYYY-MM-DD) for attendance filtering.
import java.time.LocalDate;
// LocalTime: Represents the time component (HH:MM:SS) when attendance was recorded.
import java.time.LocalTime;
// ArrayList: Dynamically builds the parameter list for parameterized SQL queries.
import java.util.ArrayList;
// List: Represents ordered collections of returned AttendanceRecord objects.
import java.util.List;
// Objects: Provides null-safety checks to ensure query strings are non-null.
import java.util.Objects;

@Repository
public class AttendanceRepository {
    private final JdbcTemplate jdbc;

    public AttendanceRepository(JdbcTemplate jdbc) { 
        this.jdbc = jdbc; 
    }

    /**
     * Records attendance for a person on a specific date and time.
     * Uses "INSERT OR IGNORE" combined with the unique index ux_attendance_name_day
     * so that the database itself guarantees a person is never marked present twice on the same day.
     * Returns true if a new row was inserted, or false if they were already recorded today.
     */
    public boolean insertIfAbsent(long personId, String personName, LocalDate date, LocalTime time) {
        int updated = jdbc.update(
                "INSERT OR IGNORE INTO attendance(person_id, person_name, attendance_date, attendance_time) VALUES (?, ?, ?, ?)", 
                personId, personName, date.toString(), time.withNano(0).toString());
        return updated == 1;
    }

    /**
     * Searches attendance records with dynamic filters for person name and date.
     * Uses parameterized SQL (? placeholders) to completely prevent SQL injection.
     */
    public List<AttendanceRecord> find(String name, LocalDate date) {
        StringBuilder sql = new StringBuilder("SELECT id, person_id, person_name, attendance_date, attendance_time FROM attendance WHERE 1=1");
        List<Object> values = new ArrayList<>();

        if (name != null && !name.isBlank()) {
            sql.append(" AND person_name LIKE ? COLLATE NOCASE");
            values.add("%" + name.trim() + "%");
        }
        if (date != null) {
            sql.append(" AND attendance_date = ?");
            values.add(date.toString());
        }

        sql.append(" ORDER BY attendance_date DESC, attendance_time DESC, id DESC");

        // Objects.requireNonNull asserts that the constructed SQL string is non-null before executing the query.
        return jdbc.query(Objects.requireNonNull(sql.toString()), (rs, row) -> new AttendanceRecord(
                rs.getLong("id"),
                rs.getObject("person_id") == null ? null : rs.getLong("person_id"), 
                rs.getString("person_name"),
                LocalDate.parse(rs.getString("attendance_date")), 
                LocalTime.parse(rs.getString("attendance_time"))),
                values.toArray());
    }

    /**
     * Retrieves all attendance records sorted by newest date and time first.
     */
    public List<AttendanceRecord> findAll() { 
        return find(null, null); 
    }

    /**
     * Counts how many distinct people have been recorded present on the given date.
     * Returns 0 safely if the database returns no count value.
     */
    public int countToday(LocalDate date) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM attendance WHERE attendance_date = ?", Integer.class, date.toString());
        return count == null ? 0 : count;
    }

    /**
     * Sets person_id to NULL when a person is deleted from the library.
     * This keeps their past historical attendance records intact while disconnecting the deleted profile.
     */
    public void clearPersonReference(long personId) {
        jdbc.update("UPDATE attendance SET person_id = NULL WHERE person_id = ?", personId);
    }
}
