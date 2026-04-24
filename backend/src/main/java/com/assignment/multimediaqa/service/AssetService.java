package com.assignment.multimediaqa.service;

import com.assignment.multimediaqa.dto.ChatResponse;
import com.assignment.multimediaqa.dto.SegmentDto;
import com.assignment.multimediaqa.entity.AssetType;
import com.assignment.multimediaqa.entity.MediaAsset;
import com.assignment.multimediaqa.entity.TranscriptSegment;
import com.assignment.multimediaqa.repository.MediaAssetRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AssetService {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "do", "for", "from", "how",
            "i", "in", "is", "it", "of", "on", "or", "that", "the", "this", "to", "up", "we", "what", "when"
    );

    private final MediaAssetRepository repository;
    private final FileStorageService storageService;
    private final PdfExtractorService pdfExtractorService;
    private final OpenAiClient openAiClient;

    public AssetService(
            MediaAssetRepository repository,
            FileStorageService storageService,
            PdfExtractorService pdfExtractorService,
            OpenAiClient openAiClient
    ) {
        this.repository = repository;
        this.storageService = storageService;
        this.pdfExtractorService = pdfExtractorService;
        this.openAiClient = openAiClient;
    }

    @Transactional
    public MediaAsset ingest(MultipartFile file) {
        MediaAsset existingAsset = findExistingAsset(file.getOriginalFilename());
        if (existingAsset != null) {
            return existingAsset;
        }

        String path = storageService.store(file);
        AssetType assetType = detectType(file.getContentType(), file.getOriginalFilename());
        MediaAsset asset = new MediaAsset();
        asset.setOriginalFilename(file.getOriginalFilename());
        asset.setContentType(file.getContentType());
        asset.setStoragePath(path);
        asset.setAssetType(assetType);

        if (assetType == AssetType.PDF) {
            String text = pdfExtractorService.extractText(path);
            asset.setExtractedText(text);
            asset.setSummary(openAiClient.summarize(text));
        } else {
            OpenAiClient.TranscriptionResult transcription = openAiClient.transcribe(Path.of(path));
            asset.setExtractedText(transcription.fullText());
            asset.setSummary(openAiClient.summarize(transcription.fullText()));
            asset.setSegments(transcription.segments().stream().map(segment -> {
                TranscriptSegment entity = new TranscriptSegment();
                entity.setAsset(asset);
                entity.setStartSeconds(segment.startSeconds());
                entity.setEndSeconds(segment.endSeconds());
                entity.setText(segment.text());
                return entity;
            }).toList());
        }

        return repository.save(asset);
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> findAll() {
        return repository.findAllWithSegments().stream()
                .sorted(Comparator.comparing(MediaAsset::getCreatedAt).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public MediaAsset findById(Long id) {
        return repository.findByIdWithSegments(id)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found for id " + id));
    }

    @Transactional(readOnly = true)
    public ChatResponse answerQuestion(Long assetId, String question) {
        MediaAsset asset = findById(assetId);
        List<SegmentDto> relevantSegments = findRelevantSegments(asset, question, 3);
        String context = buildContext(asset, relevantSegments);
        String answer = openAiClient.answerQuestion(context, question);
        return new ChatResponse(answer, relevantSegments);
    }

    @Transactional(readOnly = true)
    public List<SegmentDto> findTopicSegments(Long assetId, String topic) {
        MediaAsset asset = findById(assetId);
        return findRelevantSegments(asset, topic, 5);
    }

    private AssetType detectType(String contentType, String filename) {
        String lowerType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lowerType.contains(MediaType.APPLICATION_PDF_VALUE) || lowerName.endsWith(".pdf")) {
            return AssetType.PDF;
        }
        if (lowerType.startsWith("video/") || Set.of(".mp4", ".mov", ".mkv", ".webm").stream().anyMatch(lowerName::endsWith)) {
            return AssetType.VIDEO;
        }
        return AssetType.AUDIO;
    }

    private MediaAsset findExistingAsset(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        return repository.findByOriginalFilenameWithSegments(filename).stream()
                .findFirst()
                .orElse(null);
    }

    private List<SegmentDto> findRelevantSegments(MediaAsset asset, String topic, int limit) {
        if (asset.getSegments().isEmpty()) {
            String[] chunks = asset.getExtractedText().split("(?<=\\G.{400})");
            List<SegmentDto> documentChunks = new ArrayList<>();
            for (String chunk : chunks) {
                if (!chunk.isBlank()) {
                    documentChunks.add(new SegmentDto(0, 0, chunk.trim()));
                }
            }
            return rankSegments(documentChunks, topic, limit);
        }
        return rankSegments(asset.getSegments().stream()
                .map(segment -> new SegmentDto(segment.getStartSeconds(), segment.getEndSeconds(), segment.getText()))
                .toList(), topic, limit);
    }

    private List<SegmentDto> rankSegments(List<SegmentDto> segments, String query, int limit) {
        List<String> terms = tokenize(query);
        return segments.stream()
                .map(segment -> new RankedSegment(segment, score(segment.text(), terms)))
                .sorted(Comparator.comparingInt(RankedSegment::score).reversed())
                .filter(ranked -> ranked.score() > 0)
                .limit(limit)
                .map(RankedSegment::segment)
                .toList();
    }

    private String buildContext(MediaAsset asset, List<SegmentDto> relevantSegments) {
        if (!relevantSegments.isEmpty()) {
            return relevantSegments.stream()
                    .map(segment -> formatSegment(segment, asset.getAssetType()))
                    .reduce((left, right) -> left + "\n\n" + right)
                    .orElse(asset.getExtractedText());
        }
        return asset.getExtractedText();
    }

    private String formatSegment(SegmentDto segment, AssetType assetType) {
        if (assetType == AssetType.PDF) {
            return segment.text();
        }
        return "[from " + segment.startSeconds() + "s to " + segment.endSeconds() + "s] " + segment.text();
    }

    static List<String> tokenize(String input) {
        return List.of(input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").split("\\s+")).stream()
                .filter(token -> !token.isBlank())
                .filter(token -> token.length() > 1)
                .filter(token -> !STOP_WORDS.contains(token))
                .toList();
    }

    static int score(String text, List<String> queryTerms) {
        String lowerText = text == null ? "" : text.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : queryTerms) {
            if (lowerText.contains(term)) {
                score += 5;
            }
        }
        return score;
    }

    private record RankedSegment(SegmentDto segment, int score) {
    }
}
