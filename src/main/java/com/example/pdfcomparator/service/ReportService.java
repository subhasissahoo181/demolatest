package com.example.pdfcomparator.service;



import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
public class ReportService {

    public String generateReport(String expected, String actual, String comparisonPath,
                                 List<String> textDifferences, List<String> pixelSummaries,
                                 List<String> imageDifferences) throws IOException {

        final String dir = System.getProperty("user.home") + "/OneDrive - LTIMindtree/Desktop/TestResult/PDFReports";
        File reportFolder = new File(dir);
        if (!reportFolder.exists() && !reportFolder.mkdirs()) {
            throw new IOException("Cannot create report directory: " + dir);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String reportPath = dir + "/ComparisonReport_" + timestamp + ".pdf";

        try (FileOutputStream fos = new FileOutputStream(reportPath);
             PdfWriter writer = new PdfWriter(fos);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc)) {

            doc.add(new Paragraph("PDF Comparison Report").setBold().setFontSize(16));
            doc.add(new Paragraph("Timestamp: " + timestamp));
            doc.add(new Paragraph("Expected result: " + expected));
            doc.add(new Paragraph("Actual result: " + actual));
//            doc.add(new Paragraph("Comparison path: " + comparisonPath));

            // Pass/Fail status
            boolean passed = actual.toLowerCase().contains("pass");
            Text statusText = new Text(passed ? "PASS" : "FAIL")
                    .setFontColor(passed ? ColorConstants.GREEN : ColorConstants.RED)
                    .setBold()
                    .setFontSize(14);
            doc.add(new Paragraph("Test case status: ").setBold().setFontSize(15).add(statusText).setBackgroundColor(ColorConstants.GREEN));

            // Text differences with highlighted mismatches
            doc.add(new Paragraph("\nText and Font Differences:").setBold().setFontSize(14));
            if (textDifferences != null && !textDifferences.isEmpty()) {
                for (String diff : textDifferences) {
                    if (diff.contains("Expected Text=\"") && diff.contains("Actual Text=\"")) {
                        try {
                            int es = diff.indexOf("Expected Text=\"") + 15;
                            int ee = diff.indexOf("\"", es);
                            int as = diff.indexOf("Actual Text=\"") + 13;
                            int ae = diff.indexOf("\"", as);

                            Paragraph p = new Paragraph();
                            p.add(new Text(diff.substring(0, es - 15)).setBackgroundColor(ColorConstants.RED));
                            p.add(new Text(diff.substring(es, ee)).setBackgroundColor(ColorConstants.YELLOW));
                            p.add(new Text(diff.substring(ee, as - 13)));
                            p.add(new Text(diff.substring(as, ae)).setBackgroundColor(ColorConstants.YELLOW));
                            p.add(new Text(diff.substring(ae)));
                            doc.add(p);
                        } catch (Exception ex) {
                            doc.add(new Paragraph(diff)); // fallback: no highlighting
                        }
                    } else {
                        doc.add(new Paragraph(diff));
                    }
                }
            } else {
                doc.add(new Paragraph("No text or font differences found."));
            }

            // Pixel differences summary
//            doc.add(new Paragraph("\nPixel-level Differences Summary:").setBold().setFontSize(14));
//            if (pixelSummaries != null && !pixelSummaries.isEmpty()) {
//                for (String line : pixelSummaries) {
//                    doc.add(new Paragraph(line));
//                }
//            } else {
//                doc.add(new Paragraph("No pixel-level differences detected."));
//            }

            // Image resolution differences (highlighted in yellow)
            doc.add(new Paragraph("\nImage Resolution Differences:").setBold().setFontSize(14));
            if (imageDifferences != null && !imageDifferences.isEmpty()) {
                for (String diff : imageDifferences) {
                    doc.add(new Paragraph(diff).setBackgroundColor(ColorConstants.YELLOW));
                }
            } else {
                doc.add(new Paragraph("No image resolution differences found."));
            }

            // Pixel diff images: embed and caption each one per page
//            File comparisonFolder = new File(comparisonPath);
//            File[] diffImages = comparisonFolder.listFiles((f, name) ->
//                    name.startsWith("diffImage-page") && name.endsWith(".png"));
//            if (diffImages != null && diffImages.length > 0) {
//                Arrays.sort(diffImages, Comparator.comparing(File::getName));
//                doc.add(new Paragraph("\nPixel Difference Images:").setBold().setFontSize(14));
//                for (File imgFile : diffImages) {
//                    String pageNum = imgFile.getName().replace("diffImage-page-", "").replace(".png", "");
//                    doc.add(new Paragraph("Page " + pageNum + " - Image mismatch detected, see below").setFontColor(ColorConstants.RED));
//                    Image img = new Image(ImageDataFactory.create(imgFile.getAbsolutePath()));
//                    img.setAutoScale(true);
//                    doc.add(img);
//                }
//                doc.add(new Paragraph("Please review the above images for detailed pixel-level differences."));
//            } else {
//                doc.add(new Paragraph("\nNo pixel difference images detected."));
//            }

            // Annotated text difference images (if any)
//            File[] annotatedImages = comparisonFolder.listFiles((f, name) ->
//                    name.startsWith("annotated-") && name.endsWith(".png"));
//            if (annotatedImages != null && annotatedImages.length > 0) {
//                doc.add(new Paragraph("\nAnnotated Images (Text Differences Highlighted):").setBold().setFontSize(14));
//                for (File imgFile : annotatedImages) {
//                    doc.add(new Paragraph(imgFile.getName()));
//                    Image img = new Image(ImageDataFactory.create(imgFile.getAbsolutePath()));
//                    img.setAutoScale(true);
//                    doc.add(img);
//                }
//            }

            doc.close();
        }

        return reportPath;
    }
}
