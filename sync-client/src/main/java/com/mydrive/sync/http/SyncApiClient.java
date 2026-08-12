package com.mydrive.sync.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydrive.sync.config.SyncClientConfig;
import com.mydrive.sync.http.dto.RemoteChangeBatch;
import com.mydrive.sync.http.dto.RemoteFileMetadata;
import com.mydrive.sync.http.dto.RemoteFolder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class SyncApiClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);
    private final SyncClientConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SyncApiClient(
            SyncClientConfig config,
            HttpClient httpClient,
            ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public RemoteChangeBatch getChanges(long after, int limit)
            throws IOException, InterruptedException {
        URI uri = endpoint("/api/sync/changes?after=" + after + "&limit=" + limit);
        HttpResponse<String> response = sendString(request(uri).GET().build());
        requireSuccess(response.statusCode(), response.body());
        return objectMapper.readValue(response.body(), RemoteChangeBatch.class);
    }

    public void reportSyncProgress(long lastProcessedSequence)
            throws IOException, InterruptedException {
        String json = objectMapper.createObjectNode()
                .put("lastProcessedSequence", lastProcessedSequence)
                .toString();
        HttpResponse<String> response = sendString(
                request(endpoint("/api/devices/current/sync-progress"))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(json))
                        .build());
        requireStatus(response.statusCode(), 204, response.body());
    }

    public List<RemoteFolder> listFolders() throws IOException, InterruptedException {
        HttpResponse<String> response = sendString(request(endpoint("/api/folders")).GET().build());
        requireSuccess(response.statusCode(), response.body());
        return objectMapper.readValue(response.body(), new TypeReference<>() {});
    }

    public UUID uploadNewFile(
            String relativePath,
            Path localFile,
            UUID parentFolderId) throws IOException, InterruptedException {
        String leafName = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        MultipartBodyPublisher multipart = new MultipartBodyPublisher().addFilePart(
                "file", leafName, localFile, detectedContentType(localFile));
        String query = parentFolderId == null ? "" : "?parentFolderId=" + parentFolderId;
        HttpRequest request = request(endpoint("/api/files/upload" + query))
                .header("Content-Type", multipart.contentType())
                .POST(multipart.bodyPublisher())
                .build();
        HttpResponse<String> response = sendString(request);
        requireStatus(response.statusCode(), 201, response.body());
        return UUID.fromString(objectMapper.readTree(response.body()).get("id").asText());
    }

    public int uploadNewVersion(UUID fileId, Path localFile)
            throws IOException, InterruptedException {
        MultipartBodyPublisher multipart = new MultipartBodyPublisher().addFilePart(
                "file", localFile.getFileName().toString(), localFile, detectedContentType(localFile));
        HttpRequest request = request(endpoint("/api/files/" + fileId + "/versions"))
                .header("Content-Type", multipart.contentType())
                .POST(multipart.bodyPublisher())
                .build();
        HttpResponse<String> response = sendString(request);
        requireStatus(response.statusCode(), 201, response.body());
        return objectMapper.readTree(response.body()).get("versionNumber").asInt();
    }

    public RemoteFileMetadata getFile(UUID fileId) throws IOException, InterruptedException {
        HttpResponse<String> response = sendString(
                request(endpoint("/api/files/" + fileId)).GET().build());
        requireSuccess(response.statusCode(), response.body());
        return objectMapper.readValue(response.body(), RemoteFileMetadata.class);
    }

    public InputStream downloadFile(UUID fileId) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = httpClient.send(
                request(endpoint("/api/files/" + fileId + "/download")).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (InputStream body = response.body()) {
                requireSuccess(response.statusCode(), new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return response.body();
    }

    public void deleteFile(UUID fileId) throws IOException, InterruptedException {
        HttpResponse<String> response = sendString(
                request(endpoint("/api/files/" + fileId)).DELETE().build());
        requireStatus(response.statusCode(), 204, response.body());
    }

    public UUID createFolder(String name, UUID parentId) throws IOException, InterruptedException {
        JsonNode json = objectMapper.createObjectNode()
                .put("name", name)
                .put("parentId", parentId == null ? null : parentId.toString());
        HttpResponse<String> response = sendString(request(endpoint("/api/folders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(json)))
                .build());
        requireStatus(response.statusCode(), 201, response.body());
        return UUID.fromString(objectMapper.readTree(response.body()).get("id").asText());
    }

    private HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + config.deviceToken());
    }

    private HttpResponse<String> sendString(HttpRequest request)
            throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI endpoint(String pathAndQuery) {
        String base = config.serverBaseUrl().toString().replaceAll("/+$", "");
        return URI.create(base + pathAndQuery);
    }

    private String detectedContentType(Path file) throws IOException {
        String type = Files.probeContentType(file);
        return type == null ? "application/octet-stream" : type;
    }

    private void requireSuccess(int status, String body) throws SyncApiException {
        if (status < 200 || status >= 300) throw error(status, body);
    }

    private void requireStatus(int status, int expected, String body) throws SyncApiException {
        if (status != expected) throw error(status, body);
    }

    private SyncApiException error(int status, String body) {
        String meaning = switch (status) {
            case 401 -> "Device token is invalid or revoked";
            case 404 -> "Remote resource was not found";
            default -> "Server returned HTTP " + status;
        };
        return new SyncApiException(status, meaning,
                status >= 500 || status == 408 || status == 429);
    }

    public static class SyncApiException extends IOException {
        private final int statusCode;
        private final boolean retryable;

        public SyncApiException(int statusCode, String message, boolean retryable) {
            super(message);
            this.statusCode = statusCode;
            this.retryable = retryable;
        }

        public int statusCode() { return statusCode; }
        public boolean retryable() { return retryable; }
    }
}
