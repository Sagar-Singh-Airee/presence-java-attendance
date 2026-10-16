/**
 * Purpose: Shows attendance and creates CSV exports.
 * Why it is used: It gives users a way to review, filter, and download attendance records.
 */
package com.presence.controller;

import com.presence.service.AttendanceService;
import com.presence.service.CsvExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Controller
public class AttendanceController {
    private final AttendanceService attendance;
    private final CsvExportService csv;
    public AttendanceController(AttendanceService attendance, CsvExportService csv) { this.attendance = attendance; this.csv = csv; }

    @GetMapping("/attendance")
    public String records(@RequestParam(required = false) String name, @RequestParam(required = false) String date, Model model) {
        LocalDate selectedDate = parseDate(date, model);
        model.addAttribute("records", attendance.find(name, selectedDate));
        model.addAttribute("selectedName", name == null ? "" : name);
        model.addAttribute("selectedDate", date == null ? "" : date);
        return "attendance";
    }

    @GetMapping("/attendance/export.csv")
    public ResponseEntity<byte[]> export() {
        byte[] file = csv.export(attendance.all());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("attendance_export.csv").build().toString())
                .body(file);
    }

    private LocalDate parseDate(String value, Model model) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value); }
        catch (RuntimeException exception) {
            model.addAttribute("error", "Choose a valid attendance date.");
            return null;
        }
    }
}
