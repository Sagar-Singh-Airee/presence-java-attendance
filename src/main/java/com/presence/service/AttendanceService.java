/**
 * Purpose: Applies the attendance rules.
 * Why it is used: It marks a known person only once per day and protects the attendance data from duplicates.
 */
package com.presence.service;

import com.presence.model.AttendanceRecord;
import com.presence.repository.AttendanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public List<AttendanceRecord> find(String name, LocalDate date) { return repository.find(name, date); }
    public List<AttendanceRecord> all() { return repository.findAll(); }
    public int countToday() { return repository.countToday(LocalDate.now(clock)); }
}
