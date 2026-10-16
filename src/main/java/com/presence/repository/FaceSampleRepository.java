/**
 * Purpose: Reads and writes saved face-photo details in SQLite.
 * Why it is used: It lets the recognition system find the photos needed for training.
 */
package com.presence.repository;

import com.presence.model.FaceSample;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class FaceSampleRepository {
    private final JdbcTemplate jdbc;

    public FaceSampleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void add(long personId, String imagePath) {
        jdbc.update("INSERT INTO face_samples(person_id, image_path, created_at) VALUES (?, ?, ?)",
                personId, imagePath, LocalDateTime.now().withNano(0).toString());
    }

    public List<FaceSample> findAll() {
        return jdbc.query("SELECT id, person_id, image_path FROM face_samples ORDER BY id",
                (rs, row) -> new FaceSample(rs.getLong("id"), rs.getLong("person_id"), rs.getString("image_path")));
    }

    public List<FaceSample> findByPersonId(long personId) {
        return jdbc.query("SELECT id, person_id, image_path FROM face_samples WHERE person_id = ? ORDER BY id",
                (rs, row) -> new FaceSample(rs.getLong("id"), rs.getLong("person_id"), rs.getString("image_path")), personId);
    }

    public boolean belongsTo(long personId, String imagePath) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM face_samples WHERE person_id = ? AND image_path = ?",
                Integer.class, personId, imagePath);
        return count != null && count > 0;
    }

    public void deleteByPersonId(long personId) { jdbc.update("DELETE FROM face_samples WHERE person_id = ?", personId); }
}
