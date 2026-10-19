/**
 * Purpose: Shows attendance records, manages CSV/Excel exports, and handles attendance CSV imports.
 * Why it is used: It gives users a complete interface to review, filter, download (in Excel or CSV),
 * and upload external attendance records.
 */
package com.presence.controller;

// AttendanceRecord: Data model holding person name, date, and time for each attendance entry.
import com.presence.model.AttendanceRecord;
// StudentAttendanceSummary: Aggregated per-student attendance summary model.
import com.presence.model.StudentAttendanceSummary;
// AttendanceImportService: Handles CSV upload parsing and database insertion.
import com.presence.service.AttendanceImportService;
// AttendanceService: Queries and records attendance entries in the SQLite database.
import com.presence.service.AttendanceService;
// CsvExportService: Transforms attendance rows into clean, UTF-8 encoded CSV spreadsheets.
import com.presence.service.CsvExportService;
// ExcelExportService: Produces beautifully styled Microsoft Excel spreadsheets (.xls) with brand colors.
import com.presence.service.ExcelExportService;
// ContentDisposition: Configures browser file-download behavior and sets the default download filename.
import org.springframework.http.ContentDisposition;
// HttpHeaders: Standard HTTP header constants (like Content-Disposition for file downloads).
import org.springframework.http.HttpHeaders;
// MediaType: Identifies MIME types such as text/csv and application/vnd.ms-excel.
import org.springframework.http.MediaType;
// ResponseEntity: Encapsulates the HTTP status code, headers, and file data into one response.
import org.springframework.http.ResponseEntity;
// Controller: Marks this class as a Spring MVC controller that returns HTML views and file downloads.
import org.springframework.stereotype.Controller;
// Model: Supplies filtered record data to the attendance.html template.
import org.springframework.ui.Model;
// GetMapping: Maps HTTP GET URL paths like /attendance and /attendance/export.xls to methods.
import org.springframework.web.bind.annotation.GetMapping;
// PostMapping: Maps HTTP POST requests (like CSV file uploads) to handler methods.
import org.springframework.web.bind.annotation.PostMapping;
// RequestParam: Extracts optional query string parameters (name and date) or uploaded files.
import org.springframework.web.bind.annotation.RequestParam;
// MultipartFile: Represents the uploaded CSV file from the user's browser.
import org.springframework.web.multipart.MultipartFile;
// RedirectAttributes: Stores temporary "flash" messages that survive a redirect.
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// LocalDate: Calendar date without time zones (e.g. 2026-10-18) for attendance filtering.
import java.time.LocalDate;
// DateTimeFormatter: Formats the current date into a readable string for filenames.
import java.time.format.DateTimeFormatter;
// List: Represents ordered collections of attendance records.
import java.util.List;

@Controller
public class AttendanceController {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AttendanceService attendance;
    private final CsvExportService csv;
    private final ExcelExportService excel;
    private final AttendanceImportService importService;

    public AttendanceController(AttendanceService attendance,
                                CsvExportService csv,
                                ExcelExportService excel,
                                AttendanceImportService importService) {
        this.attendance = attendance;
        this.csv = csv;
        this.excel = excel;
        this.importService = importService;
    }

    /**
     * Renders the attendance history page with student summaries, daily check-in log,
     * and optional search filters for person name and date.
     */
    @GetMapping("/attendance")
    public String records(@RequestParam(name = "name", required = false) String name,
                          @RequestParam(name = "date", required = false) String date,
                          @RequestParam(name = "tab", required = false) String tab,
                          Model model) {
        LocalDate selectedDate = parseDate(date, model);
        List<AttendanceRecord> records = attendance.find(name, selectedDate);
        List<StudentAttendanceSummary> studentSummaries = attendance.studentSummaries();
        int totalRecords = attendance.countAll();

        int studentsPresentToday = (int) studentSummaries.stream().filter(StudentAttendanceSummary::presentToday).count();
        int studentsAbsentToday = studentSummaries.size() - studentsPresentToday;
        boolean hasFilters = (name != null && !name.isBlank()) || selectedDate != null;

        // Clean tab name in case quotes were passed
        String cleanTab = tab != null ? tab.replace("'", "").replace("\"", "").trim() : null;
        String activeTab = (cleanTab != null && !cleanTab.isBlank()) ? cleanTab : (hasFilters ? "log" : "students");

        model.addAttribute("records", records);
        model.addAttribute("totalRecords", totalRecords);
        model.addAttribute("studentSummaries", studentSummaries);
        model.addAttribute("totalStudents", studentSummaries.size());
        model.addAttribute("studentsPresentToday", studentsPresentToday);
        model.addAttribute("studentsAbsentToday", studentsAbsentToday);
        model.addAttribute("selectedName", name == null ? "" : name);
        model.addAttribute("selectedDate", date == null ? "" : date);
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("hasFilters", hasFilters);

        return "attendance";
    }

    /**
     * Downloads ALL attendance records as a beautifully styled Microsoft Excel spreadsheet (.xls).
     * Includes SmartVision brand colors, zebra rows, borders, and centered status badges.
     */
    @GetMapping("/attendance/export.xls")
    public ResponseEntity<byte[]> exportExcelAll() {
        byte[] file = excel.export(attendance.all());
        String filename = "attendance_" + LocalDate.now().format(FILE_DATE) + ".xls";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(file);
    }

    /**
     * Downloads FILTERED attendance records as a styled Microsoft Excel spreadsheet (.xls).
     */
    @GetMapping("/attendance/export-filtered.xls")
    public ResponseEntity<byte[]> exportExcelFiltered(@RequestParam(name = "name", required = false) String name,
                                                       @RequestParam(name = "date", required = false) String date) {
        LocalDate selectedDate = (date != null && !date.isBlank()) ? parseDateSilent(date) : null;
        List<AttendanceRecord> records = attendance.find(name, selectedDate);
        byte[] file = excel.exportFiltered(records, name, date);

        StringBuilder filename = new StringBuilder("attendance_filtered");
        if (selectedDate != null) {
            filename.append("_").append(selectedDate.format(FILE_DATE));
        }
        filename.append(".xls");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename.toString()).build().toString())
                .body(file);
    }

    /**
     * Downloads ALL attendance records as a clean UTF-8 CSV spreadsheet with Byte Order Mark.
     */
    @GetMapping("/attendance/export.csv")
    public ResponseEntity<byte[]> exportCsvAll() {
        byte[] file = csv.export(attendance.all());
        String filename = "attendance_" + LocalDate.now().format(FILE_DATE) + ".csv";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(file);
    }

    /**
     * Downloads FILTERED attendance records as a clean CSV spreadsheet.
     */
    @GetMapping("/attendance/export-filtered.csv")
    public ResponseEntity<byte[]> exportCsvFiltered(@RequestParam(name = "name", required = false) String name,
                                                    @RequestParam(name = "date", required = false) String date) {
        LocalDate selectedDate = (date != null && !date.isBlank()) ? parseDateSilent(date) : null;
        List<AttendanceRecord> records = attendance.find(name, selectedDate);
        byte[] file = csv.exportFiltered(records, name, date);

        StringBuilder filename = new StringBuilder("attendance_filtered");
        if (selectedDate != null) {
            filename.append("_").append(selectedDate.format(FILE_DATE));
        }
        filename.append(".csv");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename.toString()).build().toString())
                .body(file);
    }

    /**
     * Imports attendance records from an uploaded CSV/TSV/TXT file.
     * Skips duplicates and reports a friendly summary flash message.
     */
    @PostMapping("/attendance/import")
    public String importCsv(@RequestParam("file") MultipartFile file, RedirectAttributes flash) {
        try {
            AttendanceImportService.ImportResult result = importService.importFromCsv(file);
            if (result.hasSuccess()) {
                flash.addFlashAttribute("success", result.summaryMessage());
            } else if (result.duplicates() > 0) {
                flash.addFlashAttribute("warning", result.summaryMessage());
            } else {
                flash.addFlashAttribute("error", result.summaryMessage());
            }
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            flash.addFlashAttribute("error", "Failed to import attendance file: " + ex.getMessage());
        }
        return "redirect:/attendance";
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

    /**
     * Parses a date string silently without adding UI error messages.
     */
    private LocalDate parseDateSilent(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
