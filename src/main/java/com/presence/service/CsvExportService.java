/**
 * Purpose: Converts attendance records into a beautifully formatted CSV download.
 * Why it is used: It gives teachers and admins a clean, professional file they can open
 * directly in Excel, Google Sheets, or LibreOffice without any formatting hassle.
 */
package com.presence.service;

// AttendanceRecord: Holds the person's name, attendance date, and time for each row.
import com.presence.model.AttendanceRecord;
// Service: Tells Spring Boot to manage this class as an injectable service bean.
import org.springframework.stereotype.Service;

// ByteArrayOutputStream: Builds the file content in memory as a byte stream before downloading.
import java.io.ByteArrayOutputStream;
// OutputStreamWriter: Bridges character output to the byte stream, ensuring correct UTF-8 encoding.
import java.io.OutputStreamWriter;
// PrintWriter: Provides convenient println() and print() methods for writing text line by line.
import java.io.PrintWriter;
// StandardCharsets: Guarantees the file uses UTF-8 encoding so international characters display correctly.
import java.nio.charset.StandardCharsets;
// DateTimeFormatter: Formats raw date and time objects into human-friendly strings like "18 Oct 2026".
import java.time.format.DateTimeFormatter;
// List: Represents the collection of attendance records being exported.
import java.util.List;

@Service
public class CsvExportService {

    // Formats dates as "18 Oct 2026" — easy to read in any spreadsheet program.
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // Formats times as "08:21 AM" — 12-hour clock is more natural for attendance sheets.
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");

    /**
     * Exports attendance records as a professionally formatted CSV byte array.
     *
     * Features that make it look great in Excel:
     *   - UTF-8 BOM (Byte Order Mark) at the start so Excel auto-detects encoding
     *   - A title row and a generated-on timestamp for context
     *   - Serial number column so rows are easy to reference
     *   - Human-readable date ("18 Oct 2026") and 12-hour time ("08:21 AM")
     *   - Proper CSV quoting to handle names with commas, quotes, or accents
     *   - Summary row at the bottom with total record count
     */
    public byte[] export(List<AttendanceRecord> records) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // The UTF-8 BOM (EF BB BF) tells Excel to interpret the file as UTF-8 automatically.
        // Without this, Excel on Windows may display garbled characters for non-ASCII names.
        buffer.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, 0, 3);

        PrintWriter writer = new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8), true);

        // Title row — gives the spreadsheet a clear heading when opened.
        writer.println(escape("SmartVision — Attendance Report"));

        // Generated timestamp — lets the user know when this file was created.
        writer.println(escape("Generated: " + java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))));

        // Blank row to visually separate the header from the data.
        writer.println();

        // Column headers — clear, professional labels.
        writer.println(escape("S.No.") + "," + escape("Person Name") + ","
                + escape("Date") + "," + escape("Day") + "," + escape("Time"));

        // Data rows with serial numbers, formatted dates, day names, and 12-hour times.
        int serial = 1;
        for (AttendanceRecord record : records) {
            String name = record.personName() != null ? record.personName() : "Unknown";
            String date = record.attendanceDate().format(DATE_FORMAT);
            String day = record.attendanceDate().getDayOfWeek().name();
            // Capitalizes only the first letter: "MONDAY" → "Monday"
            day = day.charAt(0) + day.substring(1).toLowerCase();
            String time = record.attendanceTime().format(TIME_FORMAT);

            writer.println(serial + "," + escape(name) + "," + escape(date) + ","
                    + escape(day) + "," + escape(time));
            serial++;
        }

        // Blank row before summary.
        writer.println();

        // Summary row — total count for quick reference at the bottom.
        writer.println("," + escape("Total Records:") + "," + (serial - 1));

        writer.flush();
        return buffer.toByteArray();
    }

    /**
     * Exports only filtered records (when user has active filters on the attendance page).
     * Uses the same formatting as the full export.
     */
    public byte[] exportFiltered(List<AttendanceRecord> records, String nameFilter, String dateFilter) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // UTF-8 BOM for Excel auto-detection.
        buffer.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, 0, 3);

        PrintWriter writer = new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8), true);

        // Title row.
        writer.println(escape("SmartVision — Attendance Report (Filtered)"));

        // Show what filters were applied so the user knows this is a partial export.
        StringBuilder filterInfo = new StringBuilder("Filters: ");
        boolean hasFilter = false;
        if (nameFilter != null && !nameFilter.isBlank()) {
            filterInfo.append("Name = \"").append(nameFilter.trim()).append("\"");
            hasFilter = true;
        }
        if (dateFilter != null && !dateFilter.isBlank()) {
            if (hasFilter) filterInfo.append(", ");
            filterInfo.append("Date = ").append(dateFilter);
            hasFilter = true;
        }
        if (!hasFilter) filterInfo.append("None");
        writer.println(escape(filterInfo.toString()));

        // Generated timestamp.
        writer.println(escape("Generated: " + java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))));

        // Blank row separator.
        writer.println();

        // Column headers.
        writer.println(escape("S.No.") + "," + escape("Person Name") + ","
                + escape("Date") + "," + escape("Day") + "," + escape("Time"));

        // Data rows.
        int serial = 1;
        for (AttendanceRecord record : records) {
            String name = record.personName() != null ? record.personName() : "Unknown";
            String date = record.attendanceDate().format(DATE_FORMAT);
            String day = record.attendanceDate().getDayOfWeek().name();
            day = day.charAt(0) + day.substring(1).toLowerCase();
            String time = record.attendanceTime().format(TIME_FORMAT);

            writer.println(serial + "," + escape(name) + "," + escape(date) + ","
                    + escape(day) + "," + escape(time));
            serial++;
        }

        // Summary.
        writer.println();
        writer.println("," + escape("Total Records:") + "," + (serial - 1));

        writer.flush();
        return buffer.toByteArray();
    }

    /**
     * Wraps a value in double quotes and escapes any internal quotes.
     * This prevents names containing commas, quotes, or newlines from breaking CSV columns.
     * Example: O'Brien, "Dr." → "O'Brien, ""Dr."""
     */
    private String escape(String value) {
        if (value == null) return "\"\"";
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
