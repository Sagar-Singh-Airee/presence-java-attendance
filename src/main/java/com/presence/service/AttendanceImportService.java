/**
 * Purpose: Parses and imports attendance records from CSV or TSV files into SQLite.
 * Why it is used: It allows teachers, managers, and admins to import historic attendance,
 * merge spreadsheets, or restore backed-up attendance records with automatic duplicate prevention.
 */
package com.presence.service;

// Person: Data model for registered people.
import com.presence.model.Person;
// PersonRepository: Used to match imported names with registered people so person_id is linked.
import com.presence.repository.PersonRepository;
// Logger + LoggerFactory: Logs import outcomes and parsing details.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// Service: Registers this class as a Spring business service component.
import org.springframework.stereotype.Service;
// MultipartFile: Represents the uploaded CSV file received from the HTML form.
import org.springframework.web.multipart.MultipartFile;

// BufferedReader: Reads lines efficiently from the uploaded file's input stream.
import java.io.BufferedReader;
// IOException: Thrown when file stream reading fails.
import java.io.IOException;
// InputStreamReader: Decodes the file's raw bytes into characters using UTF-8.
import java.io.InputStreamReader;
// StandardCharsets: Standard UTF-8 character encoding.
import java.nio.charset.StandardCharsets;
// LocalDate: Calendar date of the imported attendance record.
import java.time.LocalDate;
// LocalTime: Time of the imported attendance record.
import java.time.LocalTime;
// DateTimeFormatter: Collection of formatters for parsing different international date styles.
import java.time.format.DateTimeFormatter;
// DateTimeParseException: Caught when a date or time string doesn't match an expected format.
import java.time.format.DateTimeParseException;
// ArrayList: Collects any non-fatal parsing warning messages.
import java.util.ArrayList;
// List: Represents ordered collections of parsed column tokens or warnings.
import java.util.List;
// Locale: Used for case-insensitive month names like "Oct" or "October".
import java.util.Locale;
// Optional: Returned when searching for an existing person by name.
import java.util.Optional;

@Service
public class AttendanceImportService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceImportService.class);

    // Supported date formatters tried in order during import
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                           // 2026-10-18
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH), // 18 Oct 2026
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),  // 8 Oct 2026
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),                  // 18/10/2026
            DateTimeFormatter.ofPattern("d/M/yyyy"),                    // 8/10/2026
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),                  // 10/18/2026
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),                  // 18-10-2026
            DateTimeFormatter.ofPattern("yyyy/MM/dd")                   // 2026/10/18
    );

    // Supported time formatters tried in order
    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_TIME,                           // 14:30:00 or 14:30
            DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH),  // 02:30:00 PM
            DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH),   // 2:30:00 PM
            DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH),     // 02:30 PM
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),      // 2:30 PM
            DateTimeFormatter.ofPattern("HH:mm")                        // 14:30
    );

    private final AttendanceService attendanceService;
    private final PersonRepository personRepository;

    public AttendanceImportService(AttendanceService attendanceService, PersonRepository personRepository) {
        this.attendanceService = attendanceService;
        this.personRepository = personRepository;
    }

    /**
     * Reads and imports attendance rows from an uploaded CSV or text file.
     * Automatically ignores duplicate records for the same person on the same date.
     */
    public ImportResult importFromCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a CSV file to import.");
        }

        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.toLowerCase().endsWith(".csv")
                && !originalName.toLowerCase().endsWith(".txt")
                && !originalName.toLowerCase().endsWith(".tsv")) {
            throw new IllegalArgumentException("Only CSV, TSV, or TXT spreadsheet files are supported.");
        }

        int imported = 0;
        int duplicates = 0;
        int skipped = 0;
        List<String> warnings = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;

            // Column indices determined from header, defaulting to: Name=0, Date=1, Time=2
            int colName = -1;
            int colDate = -1;
            int colTime = -1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Strip UTF-8 Byte Order Mark (BOM) if present on the very first line
                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }

                line = line.trim();
                // Skip empty lines and comment lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                List<String> cells = parseCsvLine(line);
                if (cells.isEmpty()) continue;

                // Check if this line is a title or metadata line (e.g. "SmartVision — Attendance Report")
                if (cells.size() == 1 && (cells.get(0).toLowerCase().contains("smartvision")
                        || cells.get(0).toLowerCase().contains("report")
                        || cells.get(0).toLowerCase().contains("generated")
                        || cells.get(0).toLowerCase().contains("filter"))) {
                    continue;
                }

                // Check if this line is a summary row (e.g. ",Total Records:,14")
                if (line.toLowerCase().contains("total records") || line.toLowerCase().contains("total attendance")) {
                    continue;
                }

                // Check if this line is the column header row
                if (colName == -1 && isHeaderLine(cells)) {
                    for (int i = 0; i < cells.size(); i++) {
                        String col = cells.get(i).trim().toLowerCase();
                        if (col.contains("name") || col.equals("person")) {
                            colName = i;
                        } else if (col.contains("date")) {
                            colDate = i;
                        } else if (col.contains("time")) {
                            colTime = i;
                        }
                    }
                    continue;
                }

                // If no header was found yet, use default column mapping: Name=0, Date=1, Time=2
                if (colName == -1) {
                    if (cells.size() >= 3) {
                        colName = 0;
                        colDate = 1;
                        colTime = 2;
                    } else if (cells.size() == 2) {
                        colName = 0;
                        colDate = 1;
                    } else {
                        skipped++;
                        continue;
                    }
                }

                // Validate index availability for this row
                if (colName >= cells.size() || colDate >= cells.size()) {
                    skipped++;
                    continue;
                }

                String rawName = cells.get(colName).trim();
                String rawDate = cells.get(colDate).trim();
                String rawTime = (colTime >= 0 && colTime < cells.size()) ? cells.get(colTime).trim() : "";

                // Skip invalid or placeholder names
                if (rawName.isEmpty() || rawName.equalsIgnoreCase("person name") || rawName.equalsIgnoreCase("name")) {
                    continue;
                }

                LocalDate date = parseDate(rawDate);
                if (date == null) {
                    skipped++;
                    if (warnings.size() < 5) {
                        warnings.add("Line " + lineNumber + ": Could not parse date '" + rawDate + "' for " + rawName);
                    }
                    continue;
                }

                LocalTime time = parseTime(rawTime);

                // Look up registered person in SQLite so we link their person_id if available
                Optional<Person> matchedPerson = personRepository.findByNormalizedName(rawName);
                Long personId = matchedPerson.isPresent() ? matchedPerson.get().id() : null;

                // Insert into SQLite database (INSERT OR IGNORE handles unique date/name index)
                boolean inserted = attendanceService.record(personId, rawName, date, time);
                if (inserted) {
                    imported++;
                } else {
                    duplicates++;
                }
            }
        } catch (IOException ex) {
            log.error("Failed to read uploaded attendance CSV: {}", ex.getMessage(), ex);
            throw new RuntimeException("Could not read the uploaded CSV file.", ex);
        }

        return new ImportResult(imported, duplicates, skipped, warnings);
    }

    /**
     * Identifies whether a row is a header row containing column labels.
     */
    private boolean isHeaderLine(List<String> cells) {
        for (String cell : cells) {
            String lower = cell.toLowerCase().trim();
            if (lower.equals("name") || lower.equals("person") || lower.equals("person name")
                    || lower.equals("date") || lower.equals("attendance date") || lower.equals("time")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Splits a CSV line into individual cell tokens, respecting double-quote escapes.
     */
    private List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char delimiter = line.contains("\t") && !line.contains(",") ? '\t' : ',';

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    current.append('\"');
                    i++; // skip escaped quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == delimiter && !inQuotes) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString().trim());
        return cells;
    }

    /**
     * Tries each supported date format until one parses successfully.
     */
    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(raw, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }
        return null;
    }

    /**
     * Tries each supported time format, falling back to 09:00:00 if absent or unparseable.
     */
    private LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalTime.of(9, 0, 0); // Default to 9:00 AM if no time provided in CSV
        }
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return LocalTime.parse(raw, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }
        return LocalTime.of(9, 0, 0);
    }

    /**
     * Represents the outcome of an attendance CSV import operation.
     */
    public record ImportResult(int imported, int duplicates, int skipped, List<String> warnings) {
        public boolean hasSuccess() {
            return imported > 0;
        }

        public String summaryMessage() {
            StringBuilder sb = new StringBuilder();
            if (imported > 0) {
                sb.append("Successfully imported ").append(imported).append(" attendance record").append(imported == 1 ? "" : "s").append(". ");
            }
            if (duplicates > 0) {
                sb.append(duplicates).append(" record").append(duplicates == 1 ? " was" : "s were").append(" skipped because attendance was already marked for that day. ");
            }
            if (skipped > 0) {
                sb.append(skipped).append(" row").append(skipped == 1 ? " was" : "s were").append(" skipped due to invalid date or formatting. ");
            }
            if (imported == 0 && duplicates == 0 && skipped == 0) {
                sb.append("No valid attendance rows were found in the uploaded file.");
            }
            return sb.toString().trim();
        }
    }
}
