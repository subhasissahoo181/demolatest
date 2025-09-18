package com.example.pdfcomparator.controller;

import com.example.pdfcomparator.service.PdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Controller
public class PdfController {

    @Autowired
    private PdfService pdfService;

    @GetMapping("/")
    public String index() {
        return "index"; // Loads index.html from templates
    }

    @PostMapping("/compare")
    public String comparePDFs(@RequestParam("file1") MultipartFile file1,
                              @RequestParam("file2") MultipartFile file2,
                              @RequestParam(value = "exclusionsJson", required = false) String exclusionsJson,
                              Model model) throws IOException {
        String result = pdfService.comparePDFs(file1, file2, exclusionsJson);
        model.addAttribute("result", result);

        // Extract report and CSV paths from result string
        if (result.contains("PDF report: ") && result.contains("CSV report: ")) {
            String reportPath = result.split("PDF report: ")[1].split(" \\|")[0].trim();
            String csvPath = result.split("CSV report: ")[1].trim();
            model.addAttribute("reportPath", reportPath);
            model.addAttribute("csvPath", csvPath);
        }

        return "index";
    }

    @GetMapping("/downloadReport")
    public ResponseEntity<Resource> downloadReport(@RequestParam("path") String path) throws IOException {
        String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
        File file = new File(decodedPath);

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName());

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    @GetMapping("/downloadCsv")
    public ResponseEntity<Resource> downloadCsv(@RequestParam("path") String path) throws IOException {
        String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
        File file = new File(decodedPath);

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName());

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(file.length())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(resource);
    }
}
