package com.example.pdfcomparator.util;

import com.example.pdfcomparator.service.PdfService.LineComparisonResult;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CsvReportExporter {

    /**
     * Export LineComparisonResult list to CSV with a clickable PDF report link for Excel.
     * Only failed entries (passFail = "FAIL") are exported.
     *
     * @param lineComparisonResults List of comparison results.
     * @param pdfReportPath Absolute path to the generated PDF report (for link).
     * @return Full CSV file path.
     * @throws IOException on file write failure.
     */
    public static String exportLineDifferencesToCsv(List<LineComparisonResult> lineComparisonResults, String pdfReportPath) throws IOException {
        String reportDir = System.getProperty("user.home") + "/OneDrive - LTIMindtree/Desktop/TestResult/CSVReports";
        java.io.File folder = new java.io.File(reportDir);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Failed to create CSV report directory: " + reportDir);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String csvPath = reportDir + "/line-differences-" + timestamp + ".csv";

        // Sanitize PDF report path for hyperlink formula
        String sanitizedReportPath = pdfReportPath.replace("\\", "/");

        try (FileWriter writer = new FileWriter(csvPath)) {
            // Write header line
            writer.append("Page Number,Expected Result,Actual Result,Text Compare,Image Compare,Expected Image,Actual Image,")
                  .append("Font Compare,Expected Font Style,Actual Font Style,Font Size Compare,Expected Font Size,Actual Font Size,")
                  .append("Line Compare,All Compare / Pixel Compare,Pass/Fail,Report Link,Mismatch Details\n");

            for (LineComparisonResult r : lineComparisonResults) {
                if (!"FAIL".equalsIgnoreCase(r.passFail)) continue;

                writer.append(safeQuote(String.valueOf(r.pageNumber))).append(",");
                writer.append(safeQuote(r.expectedText)).append(",");
                writer.append(safeQuote(r.actualText)).append(",");
                writer.append(safeQuote(r.textCompare)).append(",");
                writer.append(safeQuote(r.imageCompare)).append(",");
                writer.append(safeQuote(r.expectedImage)).append(",");
                writer.append(safeQuote(r.actualImage)).append(",");
                writer.append(safeQuote(r.fontCompare)).append(",");
                writer.append(safeQuote(r.expectedFontStyle)).append(",");
                writer.append(safeQuote(r.actualFontStyle)).append(",");
                writer.append(safeQuote(r.fontSizeCompare)).append(",");
                writer.append(safeQuote(r.expectedFontSize)).append(",");
                writer.append(safeQuote(r.actualFontSize)).append(",");
                writer.append(safeQuote(r.lineCompare)).append(",");
                writer.append(safeQuote(r.allCompare)).append(",");
                writer.append(safeQuote(r.passFail)).append(",");

                // Add Excel clickable hyperlink formula using full file path
                String hyperlinkFormula = String.format("=HYPERLINK(\"file:///%s\",\"View Report\")", sanitizedReportPath);
                writer.append("\"").append(hyperlinkFormula).append("\",");

                writer.append(safeQuote(r.mismatchDetails)).append("\n");
            }

            // Optional summary row with clickable PDF report link
            writer.append("\n\"Summary/Mismatches Shown In PDF Report:\",\"")
                  .append(String.format("=HYPERLINK(\"file:///%s\",\"View Report\")", sanitizedReportPath))
                  .append("\"\n");
        }

        return csvPath;
    }

    private static String safeQuote(String s) {
        if (s == null) return "\"\"";
        String trimmed = s.trim();
        String escaped = trimmed.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
