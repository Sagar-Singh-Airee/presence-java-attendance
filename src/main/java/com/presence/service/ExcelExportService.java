/**
 * Purpose: Generates beautifully formatted Microsoft Excel spreadsheets (.xls) using XML SpreadsheetML.
 * Why it is used: It gives users a gorgeous, styled attendance sheet with brand colors, alternating rows,
 * centered badges, and proper column widths when opened in Microsoft Excel, Apple Numbers, or LibreOffice,
 * without requiring heavy external dependencies like Apache POI.
 */
package com.presence.service;

// AttendanceRecord: Holds person name, date, and time for each attendance row.
import com.presence.model.AttendanceRecord;
// Service: Registers this class as a Spring business service component.
import org.springframework.stereotype.Service;

// ByteArrayOutputStream: Captures the XML spreadsheet bytes in memory for downloading.
import java.io.ByteArrayOutputStream;
// OutputStreamWriter: Writes characters to the byte stream with standard UTF-8 encoding.
import java.io.OutputStreamWriter;
// PrintWriter: Provides line-by-line printing methods for building the XML document.
import java.io.PrintWriter;
// StandardCharsets: Guarantees consistent UTF-8 encoding across Windows and Mac.
import java.nio.charset.StandardCharsets;
// LocalDateTime: Captures the exact creation timestamp for the spreadsheet header.
import java.time.LocalDateTime;
// DateTimeFormatter: Converts dates and times into human-friendly strings (e.g. "18 Oct 2026").
import java.time.format.DateTimeFormatter;
// List: Represents the collection of attendance records being exported.
import java.util.List;

@Service
public class ExcelExportService {

    // Human-friendly date formatting: "18 Oct 2026"
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    // Human-friendly 12-hour time formatting: "08:30 AM"
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm:ss a");
    // Report creation timestamp format: "dd MMM yyyy 'at' hh:mm a"
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy 'at' hh:mm a");

    /**
     * Generates a fully-styled, gorgeous Excel spreadsheet (.xls) for all attendance records.
     */
    public byte[] export(List<AttendanceRecord> records) {
        return generateWorkbook(records, "SmartVision — Attendance Register", null);
    }

    /**
     * Generates an Excel spreadsheet for filtered records, displaying the active search criteria in the header.
     */
    public byte[] exportFiltered(List<AttendanceRecord> records, String nameFilter, String dateFilter) {
        StringBuilder filterDesc = new StringBuilder("Active Filters: ");
        boolean hasFilter = false;
        if (nameFilter != null && !nameFilter.isBlank()) {
            filterDesc.append("Name contains \"").append(nameFilter.trim()).append("\"");
            hasFilter = true;
        }
        if (dateFilter != null && !dateFilter.isBlank()) {
            if (hasFilter) filterDesc.append(" • ");
            filterDesc.append("Date is ").append(dateFilter.trim());
            hasFilter = true;
        }
        if (!hasFilter) {
            filterDesc.append("All Records");
        }

        return generateWorkbook(records, "SmartVision — Filtered Attendance Register", filterDesc.toString());
    }

    /**
     * Builds the complete XML SpreadsheetML document with full visual styling.
     */
    private byte[] generateWorkbook(List<AttendanceRecord> records, String title, String subtitleFilter) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8), true);

        // XML declaration and Excel Processing Instruction
        out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        out.println("<?mso-application progid=\"Excel.Sheet\"?>");
        out.println("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"");
        out.println(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"");
        out.println(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"");
        out.println(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"");
        out.println(" xmlns:html=\"http://www.w3.org/TR/REC-html40\">");

        // Document Properties
        out.println(" <DocumentProperties xmlns=\"urn:schemas-microsoft-com:office:office\">");
        out.println("  <Title>" + xmlEscape(title) + "</Title>");
        out.println("  <Author>SmartVision Local Attendance</Author>");
        out.println("  <Created>" + LocalDateTime.now() + "</Created>");
        out.println("  <Company>SmartVision</Company>");
        out.println(" </DocumentProperties>");

        // Styles Definition (Fonts, Backgrounds, Borders, Alignments)
        writeStyles(out);

        // Worksheet Definition
        out.println(" <Worksheet ss:Name=\"Attendance\">");
        out.println("  <Table ss:DefaultRowHeight=\"22\">");

        // Explicit Column Widths (in Excel points)
        out.println("   <Column ss:Index=\"1\" ss:Width=\"45\"/>");   // S.No.
        out.println("   <Column ss:Index=\"2\" ss:Width=\"180\"/>");  // Person Name
        out.println("   <Column ss:Index=\"3\" ss:Width=\"110\"/>");  // Date
        out.println("   <Column ss:Index=\"4\" ss:Width=\"90\"/>");   // Day
        out.println("   <Column ss:Index=\"5\" ss:Width=\"110\"/>");  // Time
        out.println("   <Column ss:Index=\"6\" ss:Width=\"90\"/>");   // Status

        // Row 1: Brand Title Banner (Merged across 6 columns, height 36)
        out.println("   <Row ss:Height=\"36\">");
        out.println("    <Cell ss:MergeAcross=\"5\" ss:StyleID=\"BannerTitle\">");
        out.println("     <Data ss:Type=\"String\">" + xmlEscape(title.toUpperCase()) + "</Data>");
        out.println("    </Cell>");
        out.println("   </Row>");

        // Row 2: Subtitle with Generation Timestamp & Filter Details (Merged across 6 columns, height 22)
        String timestamp = "Generated on " + LocalDateTime.now().format(TIMESTAMP_FORMATTER)
                + (subtitleFilter != null ? "  |  " + subtitleFilter : "")
                + "  |  Total Records: " + records.size();
        out.println("   <Row ss:Height=\"22\">");
        out.println("    <Cell ss:MergeAcross=\"5\" ss:StyleID=\"BannerSubtitle\">");
        out.println("     <Data ss:Type=\"String\">" + xmlEscape(timestamp) + "</Data>");
        out.println("    </Cell>");
        out.println("   </Row>");

        // Row 3: Blank separator row
        out.println("   <Row ss:Height=\"8\"><Cell ss:StyleID=\"EmptyRow\"/></Row>");

        // Row 4: Column Header Row (Height 26)
        out.println("   <Row ss:Height=\"26\">");
        out.println("    <Cell ss:StyleID=\"ColHeader\"><Data ss:Type=\"String\">S.No.</Data></Cell>");
        out.println("    <Cell ss:StyleID=\"ColHeader\"><Data ss:Type=\"String\">Person Name</Data></Cell>");
        out.println("    <Cell ss:StyleID=\"ColHeader\"><Data ss:Type=\"String\">Attendance Date</Data></Cell>");
        out.println("    <Cell ss:StyleID=\"ColHeader\"><Data ss:Type=\"String\">Day</Data></Cell>");
        out.println("    <Cell ss:StyleID=\"ColHeader\"><Data ss:Type=\"String\">Recorded Time</Data></Cell>");
        out.println("    <Cell ss:StyleID=\"ColHeader\"><Data ss:Type=\"String\">Status</Data></Cell>");
        out.println("   </Row>");

        // Data Rows (Zebra striped: RowEven / RowOdd)
        int serial = 1;
        for (AttendanceRecord record : records) {
            boolean isOdd = (serial % 2 == 1);
            String textStyle = isOdd ? "DataTextOdd" : "DataTextEven";
            String centerStyle = isOdd ? "DataCenterOdd" : "DataCenterEven";
            String statusStyle = isOdd ? "StatusBadgeOdd" : "StatusBadgeEven";

            String name = record.personName() != null ? record.personName() : "Unknown";
            String date = record.attendanceDate().format(DATE_FORMATTER);
            String day = record.attendanceDate().getDayOfWeek().name();
            day = day.charAt(0) + day.substring(1).toLowerCase();
            String time = record.attendanceTime().format(TIME_FORMATTER);

            out.println("   <Row ss:Height=\"22\">");
            out.println("    <Cell ss:StyleID=\"" + centerStyle + "\"><Data ss:Type=\"Number\">" + serial + "</Data></Cell>");
            out.println("    <Cell ss:StyleID=\"" + textStyle + "\"><Data ss:Type=\"String\">" + xmlEscape(name) + "</Data></Cell>");
            out.println("    <Cell ss:StyleID=\"" + centerStyle + "\"><Data ss:Type=\"String\">" + xmlEscape(date) + "</Data></Cell>");
            out.println("    <Cell ss:StyleID=\"" + centerStyle + "\"><Data ss:Type=\"String\">" + xmlEscape(day) + "</Data></Cell>");
            out.println("    <Cell ss:StyleID=\"" + centerStyle + "\"><Data ss:Type=\"String\">" + xmlEscape(time) + "</Data></Cell>");
            out.println("    <Cell ss:StyleID=\"" + statusStyle + "\"><Data ss:Type=\"String\">Present</Data></Cell>");
            out.println("   </Row>");
            serial++;
        }

        // Summary Row at bottom
        out.println("   <Row ss:Height=\"10\"><Cell ss:StyleID=\"EmptyRow\"/></Row>");
        out.println("   <Row ss:Height=\"24\">");
        out.println("    <Cell ss:StyleID=\"SummaryLabel\" ss:MergeAcross=\"4\"><Data ss:Type=\"String\">Total Attendance Logged:</Data></Cell>");
        out.println("    <Cell ss:StyleID=\"SummaryValue\"><Data ss:Type=\"Number\">" + records.size() + "</Data></Cell>");
        out.println("   </Row>");

        out.println("  </Table>");

        // Worksheet Options: Freeze panes below row 4 so header stays visible on scroll
        out.println("  <WorksheetOptions xmlns=\"urn:schemas-microsoft-com:office:excel\">");
        out.println("   <Selected/>");
        out.println("   <FreezePanes/>");
        out.println("   <FrozenNoSplit/>");
        out.println("   <SplitHorizontal>4</SplitHorizontal>");
        out.println("   <TopRowBottomPane>4</TopRowBottomPane>");
        out.println("   <ActivePane>2</ActivePane>");
        out.println("   <Panes>");
        out.println("    <Pane><Number>3</Number></Pane>");
        out.println("    <Pane><Number>2</Number><ActiveRow>4</ActiveRow></Pane>");
        out.println("   </Panes>");
        out.println("   <ProtectObjects>False</ProtectObjects>");
        out.println("   <ProtectScenarios>False</ProtectScenarios>");
        out.println("  </WorksheetOptions>");

        out.println(" </Worksheet>");
        out.println("</Workbook>");

        out.flush();
        return buffer.toByteArray();
    }

    /**
     * Defines all spreadsheet styles using the SmartVision forest green and paper theme.
     */
    private void writeStyles(PrintWriter out) {
        out.println(" <Styles>");

        // Default Normal Style
        out.println("  <Style ss:ID=\"Default\" ss:Name=\"Normal\">");
        out.println("   <Alignment ss:Vertical=\"Center\"/>");
        out.println("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Color=\"#18211C\"/>");
        out.println("  </Style>");

        // Banner Title: Deep Forest Green (#155240) with White Bold Text
        out.println("  <Style ss:ID=\"BannerTitle\">");
        out.println("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>");
        out.println("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"14\" ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/>");
        out.println("   <Interior ss:Color=\"#155240\" ss:Pattern=\"Solid\"/>");
        out.println("  </Style>");

        // Banner Subtitle: Forest Mid (#1A6650) with Soft Green Text
        out.println("  <Style ss:ID=\"BannerSubtitle\">");
        out.println("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>");
        out.println("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"9\" ss:Color=\"#E3F0EA\" ss:Italic=\"1\"/>");
        out.println("   <Interior ss:Color=\"#1A6650\" ss:Pattern=\"Solid\"/>");
        out.println("  </Style>");

        // Column Headers: Forest Green Accent (#1D7A58) with White Text & Borders
        out.println("  <Style ss:ID=\"ColHeader\">");
        out.println("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>");
        out.println("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/>");
        out.println("   <Interior ss:Color=\"#1D7A58\" ss:Pattern=\"Solid\"/>");
        out.println("   <Borders>");
        out.println("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"2\" ss:Color=\"#0D3528\"/>");
        out.println("    <Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#155240\"/>");
        out.println("    <Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#155240\"/>");
        out.println("    <Border ss:Position=\"Top\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#155240\"/>");
        out.println("   </Borders>");
        out.println("  </Style>");

        // Data Rows: Even (White background)
        out.println("  <Style ss:ID=\"DataTextEven\">");
        out.println("   <Alignment ss:Horizontal=\"Left\" ss:Vertical=\"Center\"/>");
        out.println("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Color=\"#18211C\"/>");
        out.println("   <Interior ss:Color=\"#FFFFFF\" ss:Pattern=\"Solid\"/>");
        out.println("   <Borders>");
        out.println("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E0DCD4\"/>");
        out.println("    <Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E0DCD4\"/>");
        out.println("    <Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E0DCD4\"/>");
        out.println("   </Borders>");
        out.println("  </Style>");

        out.println("  <Style ss:ID=\"DataCenterEven\">");
        out.println("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>");
        out.println("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Color=\"#18211C\"/>");
        out.println("   <Interior ss:Color=\"#FFFFFF\" ss:Pattern=\"Solid\"/>");
        out.println("   <Borders>");
        out.println("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E0DCD4\"/>");
        out.println("    <Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E0DCD4\"/>");
        out.println("    <Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E0DCD4\"/>");
        out.println("   </Borders>");
        out.println("  </Style>");

        // Data Rows: Odd (Soft warm paper background #F9FAF8 for subtle zebra effect)
        out.println("  <Style ss:ID=\"DataTextOdd\">");
        out.println("   <Alignment ss:Horizontal=\"Left\" ss:Vertical=\"Center\"/>");
        out.println("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Color=\"#18211C\"/>");
        out.println("   <Interior ss:Color=\"#F7F5F0\" ss:Pattern=\"Solid\"/>");
        out.println("   <Borders>");
        out.println("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E0DCD4\"/>");
        out.println("    <Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E0DCD4\"/>");
        out.println("    <Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E0DCD4\"/>");
        out.println("   </Borders>");
        out.println("  </Style>");

        out.println("  <Style ss:ID=\"DataCenterOdd\">");
        out.println("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>");
        out.println("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Color=\"#18211C\"/>");
        out.println("   <Interior ss:Color=\"#F7F5F0\" ss:Pattern=\"Solid\"/>");
        out.println("   <Borders>");
        out.println("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E0DCD4\"/>");
        out.println("    <Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E0DCD4\"/>");
        out.println("    <Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E0DCD4\"/>");
        out.println("   </Borders>");
        out.println("  </Style>");

        // Status Badge: Soft Green pill background (#E3F0EA) with dark green text (#155240)
        out.println("  <Style ss:ID=\"StatusBadgeEven\">");
        out.println("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>");
        out.println("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"9\" ss:Bold=\"1\" ss:Color=\"#155240\"/>");
        out.println("   <Interior ss:Color=\"#E3F0EA\" ss:Pattern=\"Solid\"/>");
        out.println("   <Borders>");
        out.println("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#B8D9C8\"/>");
        out.println("    <Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#B8D9C8\"/>");
        out.println("    <Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#B8D9C8\"/>");
        out.println("   </Borders>");
        out.println("  </Style>");

        out.println("  <Style ss:ID=\"StatusBadgeOdd\">");
        out.println("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>");
        out.println("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"9\" ss:Bold=\"1\" ss:Color=\"#155240\"/>");
        out.println("   <Interior ss:Color=\"#D8EAE0\" ss:Pattern=\"Solid\"/>");
        out.println("   <Borders>");
        out.println("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#B8D9C8\"/>");
        out.println("    <Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#B8D9C8\"/>");
        out.println("    <Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#B8D9C8\"/>");
        out.println("   </Borders>");
        out.println("  </Style>");

        // Summary Row Styles
        out.println("  <Style ss:ID=\"SummaryLabel\">");
        out.println("   <Alignment ss:Horizontal=\"Right\" ss:Vertical=\"Center\"/>");
        out.println("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Bold=\"1\" ss:Color=\"#155240\"/>");
        out.println("   <Interior ss:Color=\"#EDEAE2\" ss:Pattern=\"Solid\"/>");
        out.println("   <Borders>");
        out.println("    <Border ss:Position=\"Top\" ss:LineStyle=\"Continuous\" ss:Weight=\"2\" ss:Color=\"#155240\"/>");
        out.println("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Double\" ss:Weight=\"3\" ss:Color=\"#155240\"/>");
        out.println("   </Borders>");
        out.println("  </Style>");

        out.println("  <Style ss:ID=\"SummaryValue\">");
        out.println("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>");
        out.println("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"11\" ss:Bold=\"1\" ss:Color=\"#155240\"/>");
        out.println("   <Interior ss:Color=\"#EDEAE2\" ss:Pattern=\"Solid\"/>");
        out.println("   <Borders>");
        out.println("    <Border ss:Position=\"Top\" ss:LineStyle=\"Continuous\" ss:Weight=\"2\" ss:Color=\"#155240\"/>");
        out.println("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Double\" ss:Weight=\"3\" ss:Color=\"#155240\"/>");
        out.println("   </Borders>");
        out.println("  </Style>");

        out.println("  <Style ss:ID=\"EmptyRow\"/>");

        out.println(" </Styles>");
    }

    /**
     * Safely escapes special XML characters to prevent syntax corruption in Excel.
     */
    private static String xmlEscape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
