/**
 * Purpose: Applies the attendance rules.
 * Why it is used: It marks a known person only once per day and protects the attendance data from duplicates.
 */
package com.presence.service;

// AttendanceRecord: Data transfer record holding attendance id, person id, name, date, and time.
import com.presence.model.AttendanceRecord;
// StudentAttendanceSummary: Data transfer record holding aggregated per-student attendance history.
import com.presence.model.StudentAttendanceSummary;
// AttendanceRepository: Executes attendance SQL operations against the SQLite database.
import com.presence.repository.AttendanceRepository;
// Autowired: Marks constructor for Spring dependency injection.
import org.springframework.beans.factory.annotation.Autowired;
// Service: Identifies this class as a Spring business service component.
import org.springframework.stereotype.Service;

// Clock: Supplies current system clock for timestamping attendance entries.
import java.time.Clock;
// LocalDate: Calendar date component (YYYY-MM-DD) for attendance filtering and recording.
import java.time.LocalDate;
// LocalDateTime: Current date and time when attendance is marked via camera recognition.
import java.time.LocalDateTime;
// LocalTime: Time component (HH:MM:SS) when attendance is marked or imported.
import java.time.LocalTime;
// List: Represents ordered collections of AttendanceRecord objects.
import java.util.List;

@Service
public class AttendanceService {
    private final AttendanceRepository repository;
    private final Clock clock;

    @Autowired
    public AttendanceService(AttendanceRepository repository) { this(repository, Clock.systemDefaultZone()); }
    AttendanceService(AttendanceRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

    public boolean markPresent(long personId, String personName) {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        return repository.insertIfAbsent(personId, personName, now.toLocalDate(), now.toLocalTime());
    }

    /**
     * Records an attendance entry for a specific date and time.
     * Used by attendance imports and manual logging. Returns true if newly inserted,
     * or false if an attendance entry for that person on that date already exists.
     */
    public boolean record(Long personId, String personName, LocalDate date, LocalTime time) {
        return repository.insertIfAbsent(personId, personName, date, time.withNano(0));
    }

    public List<AttendanceRecord> find(String name, LocalDate date) { return repository.find(name, date); }
    public List<AttendanceRecord> all() { return repository.findAll(); }
    public int countToday() { return repository.countToday(LocalDate.now(clock)); }
    public int countAll() { return repository.countAll(); }

    /**
     * Retrieves overall attendance history and summary for all students (both enrolled and historical).
     */
    public List<StudentAttendanceSummary> studentSummaries() {
        return repository.findStudentSummaries(LocalDate.now(clock));
    }
}
