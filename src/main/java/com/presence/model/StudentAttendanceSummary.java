/**
 * Purpose: Represents an aggregated attendance summary for a specific student or person.
 * Why it is used: It gives teachers and administrators a clear overview of each student's
 * total attendance days, first and last attendance dates, and their status today.
 */
package com.presence.model;

// LocalDate: Calendar date without time zones (e.g. 2026-10-18) for first/last attended dates.
import java.time.LocalDate;
// LocalTime: Time of today's attendance check-in.
import java.time.LocalTime;

public record StudentAttendanceSummary(
        String studentName,
        int totalDaysPresent,
        LocalDate firstSeen,
        LocalDate lastSeen,
        boolean presentToday,
        LocalTime todayTime
) {
    /**
     * Human-readable label for today's status.
     */
    public String statusLabel() {
        return presentToday ? "Present" : "Absent";
    }
}
