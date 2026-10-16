/**
 * Purpose: Reads and writes attendance data in SQLite.
 * Why it is used: It provides attendance history, filtering, and daily attendance checks.
 */
package com.presence.repository;

import com.presence.model.AttendanceRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AttendanceRepository {
    private final JdbcTemplate jdbc;

    public AttendanceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Database uniqueness is deliberately the final authority for duplicate prevention. */
    public boolean insertIfAbsent(long personId, String personName, LocalDate date, LocalTime time) {
        int updated = jdbc.update("INSERT OR IGNORE INTO attendance(person_id, person_name, attendance_date, attendance_time) "
                        + "VALUES (?, ?, ?, ?)", personId, personName, date.toString(), time.withNano(0).toString());
        return updated == 1;
    }

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
        return jdbc.query(sql.toString(), (rs, row) -> new AttendanceRecord(rs.getLong("id"),
                rs.getObject("person_id") == null ? null : rs.getLong("person_id"), rs.getString("person_name"),
                LocalDate.parse(rs.getString("attendance_date")), LocalTime.parse(rs.getString("attendance_time"))),
                values.toArray());
    }

    public List<AttendanceRecord> findAll() { return find(null, null); }

    public int countToday(LocalDate date) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM attendance WHERE attendance_date = ?", Integer.class, date.toString());
        return count == null ? 0 : count;
    }

    public void clearPersonReference(long personId) {
        jdbc.update("UPDATE attendance SET person_id = NULL WHERE person_id = ?", personId);
    }
}
