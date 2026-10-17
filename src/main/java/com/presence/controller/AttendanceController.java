/**
 * Purpose: Shows attendance and creates CSV exports.
 * Why it is used: It gives users a way to review, filter, and download attendance records.
 */
package com.presence.controller;

// AttendanceService: Queries recorded attendance entries from the database.
import com.presence.service.AttendanceService;
// CsvExportService: Transforms attendance rows into CSV spreadsheet format.
import com.presence.service.CsvExportService;
// ContentDisposition: Configures browser file-download behavior and sets the default download filename.
import org.springframework.http.ContentDisposition;
// HttpHeaders: Standard HTTP header constants (like Content-Disposition).
import org.springframework.http.HttpHeaders;
// MediaType: Identifies MIME types such as text/csv and specifies character encoding.
import org.springframework.http.MediaType;
// ResponseEntity: Encapsulates the HTTP status code, headers, and file data into one response.
import org.springframework.http.ResponseEntity;
// Controller: Marks this class as a Spring MVC controller that returns HTML views and file downloads.
import org.springframework.stereotype.Controller;
// Model: Supplies filtered record data to the attendance.html template.
import org.springframework.ui.Model;
// GetMapping: Maps HTTP GET URL paths like /attendance and /attendance/export.csv to methods.
import org.springframework.web.bind.annotation.GetMapping;
// RequestParam: Extracts optional query string parameters (name and date) from the URL.
import org.springframework.web.bind.annotation.RequestParam;

// LocalDate: Represents calendar dates without time zones (e.g. 2026-10-17) for attendance filtering.
import java.time.LocalDate;

@Controller
public class AttendanceController {
    private final AttendanceService attendance;
    private final CsvExportService csv;

    public AttendanceController(AttendanceService attendance, CsvExportService csv) { 
        this.attendance = attendance; 
        this.csv = csv; 
    }

    /**
     * Renders the attendance history page with optional search filters for person name and date.
     */
    @GetMapping("/attendance")
    public String records(@RequestParam(required = false) String name, @RequestParam(required = false) String date, Model model) {
        LocalDate selectedDate = parseDate(date, model);
        model.addAttribute("records", attendance.find(name, selectedDate));
        model.addAttribute("selectedName", name == null ? "" : name);
        model.addAttribute("selectedDate", date == null ? "" : date);
        return "attendance";
    }

    /**
     * Downloads all attendance records as a clean CSV file named "attendance_export.csv".
     * Using parseMediaType with "text/csv;charset=UTF-8" ensures spreadsheet programs correctly
     * interpret UTF-8 international characters and names.
     */
    @GetMapping("/attendance/export.csv")
    public ResponseEntity<byte[]> export() {
        byte[] file = csv.export(attendance.all());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("attendance_export.csv").build().toString())
                .body(file);
    }

    /**
     * Safely parses user date input into a LocalDate object, showing a friendly error message if invalid.
     */
    private LocalDate parseDate(String value, Model model) {
        if (value == null || value.isBlank()) return null;
        try { 
            return LocalDate.parse(value); 
        } catch (RuntimeException exception) {
            model.addAttribute("error", "Choose a valid attendance date.");
            return null;
        }
    }
}
