package com.mydrive.sync.http;

import java.io.FileNotFoundException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MultipartBodyPublisher {
    private static final String CRLF = "\r\n";
    private final String boundary = "MyDrive-" + UUID.randomUUID();
    private final List<HttpRequest.BodyPublisher> parts = new ArrayList<>();

    public MultipartBodyPublisher addTextPart(String fieldName, String value) {
        validateToken(fieldName, "field name");
        String header = "--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"" + CRLF
                + "Content-Type: text/plain; charset=UTF-8" + CRLF + CRLF;
        parts.add(HttpRequest.BodyPublishers.ofString(header + value + CRLF,
                StandardCharsets.UTF_8));
        return this;
    }

    public MultipartBodyPublisher addFilePart(
            String fieldName,
            String filename,
            Path file,
            String contentType) throws FileNotFoundException {
        validateToken(fieldName, "field name");
        validateToken(filename, "filename");
        if (contentType == null || contentType.isBlank()
                || contentType.contains("\r") || contentType.contains("\n")) {
            contentType = "application/octet-stream";
        }
        String header = "--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"" + fieldName
                + "\"; filename=\"" + filename + "\"" + CRLF
                + "Content-Type: " + contentType + CRLF + CRLF;
        parts.add(HttpRequest.BodyPublishers.ofString(header, StandardCharsets.UTF_8));
        parts.add(HttpRequest.BodyPublishers.ofFile(file));
        parts.add(HttpRequest.BodyPublishers.ofString(CRLF, StandardCharsets.UTF_8));
        return this;
    }

    public HttpRequest.BodyPublisher bodyPublisher() {
        List<HttpRequest.BodyPublisher> all = new ArrayList<>(parts);
        all.add(HttpRequest.BodyPublishers.ofString(
                "--" + boundary + "--" + CRLF, StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.concat(all.toArray(HttpRequest.BodyPublisher[]::new));
    }

    public String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    public String boundary() {
        return boundary;
    }

    private void validateToken(String value, String label) {
        if (value == null || value.isBlank() || value.contains("\r")
                || value.contains("\n") || value.contains("\"") || value.contains("\\")) {
            throw new IllegalArgumentException("Invalid multipart " + label);
        }
    }
}
