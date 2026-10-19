/**
 * Purpose: Reads and writes attendance data in SQLite.
 * Why it is used: It provides attendance history, filtering, and daily attendance checks.
 */
package com.presence.repository;

// AttendanceRecord: Data transfer record holding attendance id, person id, name, date, and time.
import com.presence.model.AttendanceRecord;
// StudentAttendanceSummary: Data transfer record holding aggregated per-student attendance history.
import com.presence.model.StudentAttendanceSummary;
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
     * Accepts a nullable Long personId so imported or external records without an enrolled profile can still be saved.
     * Returns true if a new row was inserted, or false if they were already recorded today.
     */
    public boolean insertIfAbsent(Long personId, String personName, LocalDate date, LocalTime time) {
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
     * Counts the total number of attendance records in the database.
     */
    public int countAll() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM attendance", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * Sets person_id to NULL when a person is deleted from the library.
     * This keeps their past historical attendance records intact while disconnecting the deleted profile.
     */
    public void clearPersonReference(long personId) {
        jdbc.update("UPDATE attendance SET person_id = NULL WHERE person_id = ?", personId);
    }

    /**
     * Retrieves aggregated attendance statistics for all students (both currently registered
     * and historical records), including total days attended, first and last attendance dates,
     * and whether they checked in today.
     */
    public List<StudentAttendanceSummary> findStudentSummaries(LocalDate today) {
        String sql = """
            SELECT 
                name,
                COUNT(att_id) AS total_present,
                MIN(att_date) AS first_seen,
                MAX(att_date) AS last_seen,
                MAX(CASE WHEN att_date = ? THEN 1 ELSE 0 END) AS present_today,
                MAX(CASE WHEN att_date = ? THEN att_time ELSE NULL END) AS today_time
            FROM (
                SELECT p.name AS name, a.id AS att_id, a.attendance_date AS att_date, a.attendance_time AS att_time
                FROM persons p
                LEFT JOIN attendance a ON (a.person_id = p.id OR (a.person_id IS NULL AND a.person_name = p.name COLLATE NOCASE))
                UNION ALL
                SELECT a.person_name AS name, a.id AS att_id, a.attendance_date AS att_date, a.attendance_time AS att_time
                FROM attendance a
                WHERE NOT EXISTS (SELECT 1 FROM persons p WHERE p.id = a.person_id OR p.name = a.person_name COLLATE NOCASE)
            )
            GROUP BY name
            ORDER BY name COLLATE NOCASE
            """;
        return jdbc.query(sql, (rs, rowNum) -> {
            String firstStr = rs.getString("first_seen");
            String lastStr = rs.getString("last_seen");
            String timeStr = rs.getString("today_time");
            return new StudentAttendanceSummary(
                    rs.getString("name"),
                    rs.getInt("total_present"),
                    firstStr != null ? LocalDate.parse(firstStr) : null,
                    lastStr != null ? LocalDate.parse(lastStr) : null,
                    rs.getInt("present_today") == 1,
                    timeStr != null ? LocalTime.parse(timeStr) : null
            );
        }, today.toString(), today.toString());
    }
}
