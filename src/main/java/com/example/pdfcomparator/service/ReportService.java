package com.example.pdfcomparator.service;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportService {

    public String generateReport(String expected, String actual, String comparisonOutputPath,
                                 List<String> lineDifferences, List<String> pixelSummary) throws IOException {

        String reportDir = System.getProperty("user.home") + "/OneDrive - LTIMindtree/Desktop/TestResult/PDFReports";
        File reportFolder = new File(reportDir);
        if (!reportFolder.exists() && !reportFolder.mkdirs()) {
            throw new IOException("Failed to create report directory: " + reportDir);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String reportPath = reportDir + "/comparison-report-" + timestamp + ".pdf";

        PdfWriter writer = new PdfWriter(new FileOutputStream(reportPath));
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);

        document.add(new Paragraph("PDF Comparison Report").setBold().setFontSize(16));
        document.add(new Paragraph("Timestamp: " + timestamp));
        document.add(new Paragraph("Expected Result: " + expected));
        document.add(new Paragraph("Actual Result: " + actual));
        document.add(new Paragraph("Comparison Output Path: " + comparisonOutputPath));

        // Line-by-line differences
        if (lineDifferences != null && !lineDifferences.isEmpty()) {
            document.add(new Paragraph("\nLine-by-Line Differences:").setBold().setFontSize(14));
            for (String diff : lineDifferences) {
                document.add(new Paragraph(diff));
            }
        } else {
            document.add(new Paragraph("\nNo line-by-line differences found."));
        }

        // Pixel-by-pixel summary
        if (pixelSummary != null && !pixelSummary.isEmpty()) {
            document.add(new Paragraph("\nPixel-by-Pixel Summary:").setBold().setFontSize(14));
            for (String line : pixelSummary) {
                document.add(new Paragraph(line));
            }
        }

        File comparisonFolder = new File(comparisonOutputPath);

        // Visual comparison images
        File[] diffImages = comparisonFolder.listFiles((dir, name) ->
                name.startsWith("diffImage-page") && name.endsWith(".png"));

        if (diffImages != null && diffImages.length > 0) {
            document.add(new Paragraph("\nVisual Comparison Images:").setBold().setFontSize(14));
            for (File imgFile : diffImages) {
                Image img = new Image(ImageDataFactory.create(imgFile.getAbsolutePath()));
                img.setAutoScale(true);
                document.add(img);
            }
        } else {
            document.add(new Paragraph("\nNo pixel-level visual differences found."));
        }

        // Annotated images with expected vs actual text
        File[] annotatedImages = comparisonFolder.listFiles((dir, name) ->
                name.startsWith("annotated-diffImage-page") && name.endsWith(".png"));

        if (annotatedImages != null && annotatedImages.length > 0) {
            document.add(new Paragraph("\nAnnotated Differences with Expected vs Actual:").setBold().setFontSize(14));
            for (File imgFile : annotatedImages) {
                Image img = new Image(ImageDataFactory.create(imgFile.getAbsolutePath()));
                img.setAutoScale(true);
                document.add(img);
            }
        }

        document.close();
        return reportPath;
    }
}
