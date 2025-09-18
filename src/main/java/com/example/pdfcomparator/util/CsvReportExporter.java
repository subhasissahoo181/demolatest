package com.example.pdfcomparator.util;



import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CsvReportExporter {

    public static String exportLineDifferencesToCsv(List<String> lineDifferences) throws IOException {
        String reportDir = System.getProperty("user.home") + "/OneDrive - LTIMindtree/Desktop/TestResult/CSVReports";
        java.io.File folder = new java.io.File(reportDir);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Failed to create CSV report directory: " + reportDir);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String csvPath = reportDir + "/line-differences-" + timestamp + ".csv";

        try (FileWriter writer = new FileWriter(csvPath)) {
            writer.append("Line Number,Expected Text,Actual Text\n");

            for (String diff : lineDifferences) {
                // Example: Line 5: Expected: "Hello" vs Actual: "Hi"
                String cleaned = diff.replace("Line ", "")
                                     .replace(": Expected: \"", ",\"")
                                     .replace("\" vs Actual: \"", "\",\"")
                                     .replace("\"", "");
                writer.append(cleaned).append("\n");
            }
        }

        return csvPath;
    }
}
