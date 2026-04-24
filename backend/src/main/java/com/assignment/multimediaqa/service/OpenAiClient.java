package com.assignment.multimediaqa.service;

import com.assignment.multimediaqa.dto.SegmentDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;

@Service
public class OpenAiClient {

   private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String baseUrl;
    private final String chatModel;
    private final String transcriptionModel;

    public OpenAiClient(
            @Value("${app.openai.api-key}") String apiKey,
            @Value("${app.openai.base-url}") String baseUrl,
            @Value("${app.openai.chat-model}") String chatModel,
            @Value("${app.openai.transcription-model}") String transcriptionModel
    ) {
        this.apiKey = resolveApiKey(apiKey);
        this.baseUrl = baseUrl;
        this.chatModel = chatModel;
        this.transcriptionModel = transcriptionModel;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String summarize(String content) {
        if (!isConfigured()) {
            return fallbackSummary(content);
        }
        return chat("""
                You summarize uploaded content for a Q&A app.
                Return a concise paragraph plus 3 bullet-like highlights in plain text.
                """, content);
    }

    public String answerQuestion(String context, String question) {
        if (!isConfigured()) {
            return "OpenAI API key is not configured. Based on the uploaded text, the best matching context is:\n\n"
                    + context.lines().limit(8).reduce("", (left, right) -> left + right + "\n").trim();
        }
        return chat("""
                You answer user questions strictly from the provided context.
                If the answer is not in the context, say that clearly.
                Mention timestamps only when they are explicitly present in the context.
                """, "Question: " + question + "\n\nContext:\n" + context);
    }

    public TranscriptionResult transcribe(Path mediaPath) {
        if (!isConfigured()) {
            return new TranscriptionResult(
                    "OpenAI API key is not configured, so this is a placeholder transcript for the uploaded media.",
                    List.of(new SegmentDto(0, 15, "Placeholder transcript segment. Configure OPENAI_API_KEY for Whisper transcription."))
            );
        }

        String boundary = "----JavaBoundary" + System.currentTimeMillis();
        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(mediaPath);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        String fileName = mediaPath.getFileName().toString();
        String prefix = "--" + boundary + "\r\n";
        String form = prefix
                + "Content-Disposition: form-data; name=\"model\"\r\n\r\n" + transcriptionModel + "\r\n"
                + prefix
                + "Content-Disposition: form-data; name=\"response_format\"\r\n\r\nverbose_json\r\n"
                + prefix
                + "Content-Disposition: form-data; name=\"timestamp_granularities[]\"\r\n\r\nsegment\r\n"
                + prefix
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: " + MediaType.APPLICATION_OCTET_STREAM_VALUE + "\r\n\r\n";
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes();
        byte[] formBytes = form.getBytes();
        byte[] payload = new byte[formBytes.length + fileBytes.length + suffix.length];
        System.arraycopy(formBytes, 0, payload, 0, formBytes.length);
        System.arraycopy(fileBytes, 0, payload, formBytes.length, fileBytes.length);
        System.arraycopy(suffix, 0, payload, formBytes.length + fileBytes.length, suffix.length);

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/audio/transcriptions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Transcription failed: " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<SegmentDto> segments = new ArrayList<>();
            if (root.has("segments")) {
                for (JsonNode segment : root.get("segments")) {
                    segments.add(new SegmentDto(
                            segment.path("start").asDouble(),
                            segment.path("end").asDouble(),
                            segment.path("text").asText()
                    ));
                }
            }
            return new TranscriptionResult(root.path("text").asText(), segments);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI transcription interrupted", ex);
        }
    }

    private String chat(String instruction, String content) {
        try {
//            String body = objectMapper.writeValueAsString(objectMapper.createObjectNode()
//                    .put("model", chatModel)
//                    .putArray("messages")
//                    .add(objectMapper.createObjectNode().put("role", "system").put("content", instruction))
//                    .add(objectMapper.createObjectNode().put("role", "user").put("content", content)));


            var messages = objectMapper.createArrayNode();
            messages.add(objectMapper.createObjectNode()
                    .put("role", "system").put("content", instruction));
            messages.add(objectMapper.createObjectNode()
                    .put("role", "user").put("content", content));

            var requestBody = objectMapper.createObjectNode();
            requestBody.put("model", chatModel);
            requestBody.set("messages", messages);

            String body = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Chat completion failed: " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("choices").get(0).path("message").path("content").asText().trim();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI chat interrupted", ex);
        }
    }

    static String fallbackSummary(String content) {
        String normalized = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return "No extractable text was found in the uploaded file.";
        }
        return normalized.length() <= 320 ? normalized : normalized.substring(0, 320) + "...";
    }

    static String resolveApiKey(String configuredApiKey) {
        if (configuredApiKey != null && !configuredApiKey.isBlank()) {
            return configuredApiKey.trim();
        }
        String envApiKey = System.getenv("OPENAI_API_KEY");
        if (envApiKey != null && !envApiKey.isBlank()) {
            return envApiKey.trim();
        }
        String systemPropertyApiKey = System.getProperty("openai.api.key");
        if (systemPropertyApiKey != null && !systemPropertyApiKey.isBlank()) {
            return systemPropertyApiKey.trim();
        }
        return "";
    }

    public record TranscriptionResult(String fullText, List<SegmentDto> segments) {
    }
}
