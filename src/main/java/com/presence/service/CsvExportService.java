/**
 * Purpose: Converts attendance records into a CSV download.
 * Why it is used: It gives teachers and admins an easy file they can open in Excel or similar programs.
 */
package com.presence.service;

// AttendanceRecord: Holds the person's name, attendance date, and time.
import com.presence.model.AttendanceRecord;
// Service: Tells Spring Boot to manage this class as an injectable service bean.
import org.springframework.stereotype.Service;

// StandardCharsets: Guarantees the CSV file is encoded in UTF-8 so special characters are preserved.
import java.nio.charset.StandardCharsets;
// List: Represents the collection of attendance records being exported.
import java.util.List;

@Service
public class CsvExportService {

    /**
     * Converts a list of attendance records into a downloadable byte array in CSV format.
     */
    public byte[] export(List<AttendanceRecord> records) {
        // Starts the CSV with header column names followed by \r\n (CRLF) for standard spreadsheet row splitting.
        StringBuilder csv = new StringBuilder("Name,Date,Time\r\n");

        for (AttendanceRecord record : records) {
            // Appends each person's details, separated by commas and enclosed in quotes to handle any special characters.
            csv.append(escape(record.personName())).append(',')
                    .append(escape(record.attendanceDate().toString())).append(',')
                    .append(escape(record.attendanceTime().toString())).append("\r\n");
        }

        // Converts the entire CSV text into UTF-8 bytes to send directly to the browser.
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Wraps values in double quotes and escapes any internal quotes so names containing commas or quotes do not break columns.
     */
    private String escape(String value) { 
        return '"' + value.replace("\"", "\"\"") + '"'; 
    }
}
