package com.example.pdfcomparator.service;

import com.example.pdfcomparator.util.CsvReportExporter;
//import com.example.pdfcomparator.util.ExclusionHelper;
import de.redsix.pdfcompare.CompareResult;
import de.redsix.pdfcompare.PdfComparator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfService {

    @Autowired
    private ReportService reportService;

    public String comparePDFs(MultipartFile file1, MultipartFile file2, String exclusionsJson) throws IOException {
        File tempFile1 = File.createTempFile("pdf1-", ".pdf");
        File tempFile2 = File.createTempFile("pdf2-", ".pdf");

        file1.transferTo(tempFile1);
        file2.transferTo(tempFile2);

        String outputDirPath = System.getProperty("user.home") + "/OneDrive - LTIMindtree/Desktop/TestResult";
        File outputDir = new File(outputDirPath);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outputDirPath);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String outputPath = outputDirPath + "/pdf-comparison-result-" + timestamp;

        PdfComparator<?> comparator = new PdfComparator<>(tempFile1, tempFile2);
//        ExclusionHelper.applyExclusions(comparator, exclusionsJson);

        CompareResult result = comparator.compare();
        result.writeTo(outputPath);

        boolean isEqual = result.isEqual();
        String expected = "PDFs should be identical";
        String actual = isEqual ? "✅ PDFs are identical." : "❌ PDFs differ";

        String text1 = extractTextFromPDF(tempFile1);
        String text2 = extractTextFromPDF(tempFile2);
        List<String> lineDifferences = compareLines(text1, text2);
        List<String> pixelSummary = extractPixelDifferences(outputPath);
        generateAnnotatedImages(lineDifferences, outputPath);

        String reportPath = reportService.generateReport(expected, actual, outputPath, lineDifferences, pixelSummary);
        String csvPath = CsvReportExporter.exportLineDifferencesToCsv(lineDifferences);

        return actual + ". PDF report: " + reportPath + " | CSV report: " + csvPath;
    }

    private String extractTextFromPDF(File pdfFile) throws IOException {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private List<String> compareLines(String expectedText, String actualText) {
        List<String> differences = new ArrayList<>();
        String[] expectedLines = expectedText.split("\r?\n");
        String[] actualLines = actualText.split("\r?\n");

        int maxLines = Math.max(expectedLines.length, actualLines.length);

        for (int i = 0; i < maxLines; i++) {
            String expected = i < expectedLines.length ? expectedLines[i].trim() : "[Missing Line]";
            String actual = i < actualLines.length ? actualLines[i].trim() : "[Extra Line]";
            if (!expected.equals(actual)) {
                differences.add("Line " + (i + 1) + ": Expected: \"" + expected + "\" vs Actual: \"" + actual + "\"");
            }
        }
        return differences;
    }

    private List<String> extractPixelDifferences(String comparisonOutputPath) {
        List<String> pixelSummary = new ArrayList<>();
        File comparisonFolder = new File(comparisonOutputPath);

        File[] diffImages = comparisonFolder.listFiles((dir, name) ->
                name.startsWith("diffImage-page") && name.endsWith(".png"));

        if (diffImages == null || diffImages.length == 0) {
            pixelSummary.add("No visual differences detected in any page.");
        } else {
            pixelSummary.add("Visual differences detected. See annotated images below.");
            for (File img : diffImages) {
                String fileName = img.getName();
                String pageInfo = fileName.replace("diffImage-page-", "").replace(".png", "");
                pixelSummary.add("Page " + pageInfo + ": Visual differences detected.");
            }
        }

        return pixelSummary;
    }

    private void generateAnnotatedImages(List<String> lineDifferences, String comparisonOutputPath) throws IOException {
        File comparisonFolder = new File(comparisonOutputPath);

        File[] diffImages = comparisonFolder.listFiles((dir, name) ->
                name.startsWith("diffImage-page") && name.endsWith(".png"));

        if (diffImages == null || diffImages.length == 0) return;

        for (File imageFile : diffImages) {
            BufferedImage image = ImageIO.read(imageFile);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 14));

            int y = 30;
            for (String diff : lineDifferences) {
                g.drawString(diff, 20, y);
                y += 20;
                if (y > image.getHeight() - 30) break;
            }

            g.dispose();

            File annotated = new File(comparisonFolder, "annotated-" + imageFile.getName());
            ImageIO.write(image, "png", annotated);
        }
    }
}
