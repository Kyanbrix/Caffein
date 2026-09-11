package com.github.kyanbrix.api.OpenAI.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileRenderService {


    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final int MAX_CHARS = 1_400_000;


    public static String readFile(String url, String filename) {
        try {
            byte[] bytes = downloadBytes(url);
            String ext = getExtension(filename).toLowerCase();

            String content = switch (ext) {
                case "pdf"  -> extractPdf(bytes);
                case "docx" -> extractDocx(bytes);
                default     -> extractPlainText(bytes);
            };

            if (content == null || content.isBlank()) {
                return "File was empty or could not be read.";
            }

            // Truncate if too large
            if (content.length() > MAX_CHARS) {
                content = content.substring(0, MAX_CHARS) + "\n\n[... file truncated at " + MAX_CHARS + " characters]";
            }

            return content;

        } catch (Exception e) {
            return "Failed to read file '" + filename + "': " + e.getMessage();
        }
    }


    private static String extractPlainText(byte[] bytes) {
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String extractPdf(byte[] bytes) throws Exception {
        // Requires org.apache.pdfbox:pdfbox in pom.xml
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            var stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private static String extractDocx(byte[] bytes) throws Exception {
        // Reads word/document.xml from the .docx zip structure
        StringBuilder text = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    // Strip XML tags to get plain text
                    text.append(xml.replaceAll("<[^>]+>", " ")
                            .replaceAll("\\s+", " ")
                            .trim());
                    break;
                }
            }
        }
        return text.toString();
    }

    private static byte[] downloadBytes(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Download failed (HTTP " + response.statusCode() + ")");
        }
        return response.body();
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? "txt" : filename.substring(dot + 1);
    }

}
