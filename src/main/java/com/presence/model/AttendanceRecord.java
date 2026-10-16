/**
 * Purpose: Represents one attendance entry.
 * Why it is used: It keeps the person's name and the exact date and time they were marked present.
 */
package com.presence.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceRecord(long id, Long personId, String personName, LocalDate attendanceDate,
                               LocalTime attendanceTime) { }
