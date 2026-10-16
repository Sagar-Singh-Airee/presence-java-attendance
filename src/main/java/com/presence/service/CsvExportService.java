/**
 * Purpose: Converts attendance records into a CSV download.
 * Why it is used: It gives teachers an easy file they can open in Excel or similar programs.
 */
package com.presence.service;

import com.presence.model.AttendanceRecord;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class CsvExportService {
    public byte[] export(List<AttendanceRecord> records) {
        StringBuilder csv = new StringBuilder("Name,Date,Time\\r\\n");
        for (AttendanceRecord record : records) {
            csv.append(escape(record.personName())).append(',')
                    .append(escape(record.attendanceDate().toString())).append(',')
                    .append(escape(record.attendanceTime().toString())).append("\\r\\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escape(String value) { return '"' + value.replace("\"", "\"\"") + '"'; }
}
