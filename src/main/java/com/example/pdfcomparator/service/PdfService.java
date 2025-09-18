package com.example.pdfcomparator.service;

import com.example.pdfcomparator.util.CsvReportExporter;
import com.example.pdfcomparator.util.ExclusionHelper;
import de.redsix.pdfcompare.CompareResult;
import de.redsix.pdfcompare.PdfComparator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
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
import java.util.List;
import java.util.*;

@Service
public class PdfService {

    @Autowired
    private ReportService reportService;

    public static class ExtractedLine {
        public int pageNumber;
        public String lineText;
        public String fontStyle;
        public String fontSize;
    }

    public static class LineComparisonResult {
        public int pageNumber;
        public String expectedText, actualText;
        public String expectedFontStyle, actualFontStyle;
        public String expectedFontSize, actualFontSize;
        public String expectedImage, actualImage;
        public String textCompare, imageCompare, fontCompare, fontSizeCompare, lineCompare, allCompare, passFail;
        public String mismatchDetails;
    }

    public String comparePDFs(MultipartFile file1, MultipartFile file2, String exclusionsJson) throws IOException {
        File tempFile1 = File.createTempFile("pdf1-", ".pdf");
        File tempFile2 = File.createTempFile("pdf2-", ".pdf");

        file1.transferTo(tempFile1);
        file2.transferTo(tempFile2);

        String outputDirPath = System.getProperty("user.home") + "/OneDrive - LTIMindtree/Desktop/TestResult/AllinOneReport";
        File outputDir = new File(outputDirPath);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outputDirPath);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String outputPath = outputDirPath + "/pdf-comparison-result-" + timestamp;

        PdfComparator<?> comparator = new PdfComparator<>(tempFile1, tempFile2);
        ExclusionHelper.applyExclusions(comparator, exclusionsJson);

        CompareResult result = comparator.compare();
        result.writeTo(outputPath);

        List<ExtractedLine> expectedLines = extractAllLineDetails(tempFile1);
        List<ExtractedLine> actualLines = extractAllLineDetails(tempFile2);

        Map<Integer, String> expectedImages = extractImageResolutionsPerPage(tempFile1);
        Map<Integer, String> actualImages = extractImageResolutionsPerPage(tempFile2);

        List<LineComparisonResult> results = compareAllLines(expectedLines, actualLines, expectedImages, actualImages, outputPath);

        List<String> pixelSummary = extractPixelDifferences(outputPath);

        List<String> failDetails = new ArrayList<>();
        for (LineComparisonResult lineResult : results) {
            if ("FAIL".equalsIgnoreCase(lineResult.passFail)) {
                failDetails.add(lineResult.mismatchDetails);
            }
        }
        generateAnnotatedImages(failDetails, outputPath);

        boolean testCasePass = result.isEqual() && results.stream().allMatch(r -> "PASS".equalsIgnoreCase(r.passFail));
        String expectedMsg = "PDFs should be identical";
        String actualMsg = testCasePass ? "✅ TestCase Passed: PDFs match" : "❌ TestCase Failed: Differences detected";

        String reportPath = reportService.generateReport(
                expectedMsg,
                actualMsg,
                outputPath,
                failDetails,
                pixelSummary,
                getImageDiffStrings(expectedImages, actualImages)
        );

        String csvPath = CsvReportExporter.exportLineDifferencesToCsv(results, reportPath);

        return actualMsg + ". PDF report: " + reportPath + " | CSV report: " + csvPath;
    }

    private List<ExtractedLine> extractAllLineDetails(File pdfFile) throws IOException {
        List<ExtractedLine> lines = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                int currentPage = 0;
                @Override
                protected void startPage(PDPage page) throws IOException {
                    super.startPage(page);
                    currentPage++;
                }
                @Override
                protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                    if (!text.trim().isEmpty() && !textPositions.isEmpty()) {
                        ExtractedLine line = new ExtractedLine();
                        line.pageNumber = currentPage;
                        line.lineText = text.trim();
                        line.fontStyle = textPositions.get(0).getFont().getName();
                        line.fontSize = String.format("%.1fpt", textPositions.get(0).getFontSizeInPt());
                        lines.add(line);
                    }
                }
            };
            stripper.getText(document);
        }
        return lines;
    }

    private Map<Integer, String> extractImageResolutionsPerPage(File pdfFile) throws IOException {
        Map<Integer, String> pageImageRes = new HashMap<>();
        try (PDDocument document = PDDocument.load(pdfFile)) {
            int pageNum = 0;
            for (PDPage page : document.getPages()) {
                pageNum++;
                String resolution = "-";
                for (COSName name : page.getResources().getXObjectNames()) {
                    if (page.getResources().isImageXObject(name)) {
                        try {
                            PDImageXObject image = (PDImageXObject) page.getResources().getXObject(name);
                            resolution = image.getWidth() + "x" + image.getHeight();
                            break;
                        } catch (Exception ignored) {}
                    }
                }
                pageImageRes.put(pageNum, resolution);
            }
        }
        return pageImageRes;
    }

    /** Returns true if a pixel-diff image screenshot exists for this page **/
    private boolean hasPixelDiffForPage(File comparisonFolder, int pageNumber) {
        String diffName = "diffImage-page-" + pageNumber + ".png";
        return new File(comparisonFolder, diffName).exists();
    }

    private List<LineComparisonResult> compareAllLines(
        List<ExtractedLine> expected,
        List<ExtractedLine> actual,
        Map<Integer, String> expectedImages,
        Map<Integer, String> actualImages,
        String comparisonOutputPath
    ) {
        List<LineComparisonResult> diffs = new ArrayList<>();
        int maxLines = Math.max(expected.size(), actual.size());
        File comparisonFolder = new File(comparisonOutputPath);

        for (int i = 0; i < maxLines; i++) {
            ExtractedLine e = i < expected.size() ? expected.get(i) : null;
            ExtractedLine a = i < actual.size() ? actual.get(i) : null;
            LineComparisonResult comp = new LineComparisonResult();

            comp.pageNumber = (e != null) ? e.pageNumber : (a != null ? a.pageNumber : i + 1);
            comp.expectedText = (e != null) ? e.lineText : "[Missing Line]";
            comp.actualText = (a != null) ? a.lineText : "[Extra Line]";
            comp.expectedFontStyle = (e != null) ? e.fontStyle : "[None]";
            comp.actualFontStyle = (a != null) ? a.fontStyle : "[None]";
            comp.expectedFontSize = (e != null) ? e.fontSize : "[None]";
            comp.actualFontSize = (a != null) ? a.fontSize : "[None]";
            comp.expectedImage = expectedImages.getOrDefault(comp.pageNumber, "-");
            comp.actualImage = actualImages.getOrDefault(comp.pageNumber, "-");

            comp.textCompare = safeEquals(comp.expectedText, comp.actualText) ? "PASS" : "FAIL";
            comp.fontCompare = safeEquals(comp.expectedFontStyle, comp.actualFontStyle) ? "PASS" : "FAIL";
            comp.fontSizeCompare = safeEquals(comp.expectedFontSize, comp.actualFontSize) ? "PASS" : "FAIL";

            // --- Image comparison logic now considers pixel diff as well as resolution ---
            boolean diffRes = !safeEquals(comp.expectedImage, comp.actualImage);
            boolean hasPixelDiff = hasPixelDiffForPage(comparisonFolder, comp.pageNumber);
            comp.imageCompare = (diffRes || hasPixelDiff) ? "FAIL" : "PASS";

            comp.lineCompare = (comp.textCompare.equals("PASS") && comp.fontCompare.equals("PASS") && comp.fontSizeCompare.equals("PASS")) ? "PASS" : "FAIL";
            comp.allCompare = (comp.lineCompare.equals("PASS") && comp.imageCompare.equals("PASS")) ? "PASS" : "FAIL";
            comp.passFail = comp.allCompare.equals("PASS") ? "PASS" : "FAIL";

            StringBuilder details = new StringBuilder("Line " + (i + 1) + ": ");
            if (!comp.textCompare.equals("PASS")) details.append("Text mismatch; ");
            if (!comp.fontCompare.equals("PASS")) details.append("Font style mismatch; ");
            if (!comp.fontSizeCompare.equals("PASS")) details.append("Font size mismatch; ");
            if (!comp.imageCompare.equals("PASS")) {
                if (diffRes) details.append("Image resolution mismatch; ");
                if (hasPixelDiff) details.append("Pixel-level image mismatch—see PDF report screenshot; ");
            }

            details.append(String.format("Expected Text=\"%s\", Actual Text=\"%s\", Expected Font Style=\"%s\", Actual Font Style=\"%s\", Expected Font Size=\"%s\", Actual Font Size=\"%s\", Expected Image=\"%s\", Actual Image=\"%s\"",
                    comp.expectedText, comp.actualText,
                    comp.expectedFontStyle, comp.actualFontStyle,
                    comp.expectedFontSize, comp.actualFontSize,
                    comp.expectedImage, comp.actualImage));
            comp.mismatchDetails = details.toString();
            diffs.add(comp);
        }
        return diffs;
    }

    private boolean safeEquals(String a, String b) {
        return Objects.equals(a, b);
    }

    private List<String> extractPixelDifferences(String comparisonOutputPath) {
        List<String> pixelSummary = new ArrayList<>();
        File folder = new File(comparisonOutputPath);

        File[] diffImages = folder.listFiles((dir, name) -> name.startsWith("diffImage-page") && name.endsWith(".png"));
        if (diffImages == null || diffImages.length == 0) {
            pixelSummary.add("No visual differences detected in any page.");
        } else {
            pixelSummary.add("Visual differences detected. See annotated images below.");
            for (File img : diffImages) {
                String pageNum = img.getName().replace("diffImage-page-", "").replace(".png", "");
                pixelSummary.add("Page " + pageNum + ": Visual differences detected.");
            }
        }
        return pixelSummary;
    }

    private void generateAnnotatedImages(List<String> lineDifferences, String outputPath) throws IOException {
        File folder = new File(outputPath);

        File[] diffImages = folder.listFiles((dir, name) -> name.startsWith("diffImage-page") && name.endsWith(".png"));
        if (diffImages == null || diffImages.length == 0) return;

        for (File imgFile : diffImages) {
            BufferedImage image = ImageIO.read(imgFile);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.RED);
            graphics.setFont(new Font("Arial", Font.BOLD, 14));

            int y = 30;
            for (String diff : lineDifferences) {
                graphics.drawString(diff, 20, y);
                y += 20;
                if (y > image.getHeight() - 30) break;
            }
            graphics.dispose();

            File annotatedFile = new File(folder, "annotated-" + imgFile.getName());
            ImageIO.write(image, "png", annotatedFile);
        }
    }

    private List<String> getImageDiffStrings(Map<Integer, String> expected, Map<Integer, String> actual) {
        List<String> diffs = new ArrayList<>();
        Set<Integer> allPages = new HashSet<>();
        allPages.addAll(expected.keySet());
        allPages.addAll(actual.keySet());

        for (Integer page : allPages) {
            String expectedRes = expected.getOrDefault(page, "-");
            String actualRes = actual.getOrDefault(page, "-");

            if (!Objects.equals(expectedRes, actualRes)) {
                diffs.add("Page " + page + ": Image Resolution difference - Expected: " + expectedRes + ", Actual: " + actualRes);
            }
        }
        return diffs;
    }
}
