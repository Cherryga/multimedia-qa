package com.assignment.multimediaqa.service;

import com.assignment.multimediaqa.dto.ChatResponse;
import com.assignment.multimediaqa.dto.SegmentDto;
import com.assignment.multimediaqa.entity.AssetType;
import com.assignment.multimediaqa.entity.MediaAsset;
import com.assignment.multimediaqa.entity.TranscriptSegment;
import com.assignment.multimediaqa.repository.MediaAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetServiceTest {

    private MediaAssetRepository repository;
    private FileStorageService storageService;
    private PdfExtractorService pdfExtractorService;
    private OpenAiClient openAiClient;
    private AssetService assetService;

    @BeforeEach
    void setUp() {
        repository = mock(MediaAssetRepository.class);
        storageService = mock(FileStorageService.class);
        pdfExtractorService = mock(PdfExtractorService.class);
        openAiClient = mock(OpenAiClient.class);
        assetService = new AssetService(repository, storageService, pdfExtractorService, openAiClient);
    }

    @Test
    void ingestPdfExtractsTextAndSummary() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "pdf".getBytes());
        when(repository.findByOriginalFilenameWithSegments("doc.pdf")).thenReturn(List.of());
        when(storageService.store(file)).thenReturn("storage/doc.pdf");
        when(pdfExtractorService.extractText("storage/doc.pdf")).thenReturn("important pdf content");
        when(openAiClient.summarize("important pdf content")).thenReturn("summary");
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaAsset asset = assetService.ingest(file);

        assertThat(asset.getAssetType()).isEqualTo(AssetType.PDF);
        assertThat(asset.getExtractedText()).isEqualTo("important pdf content");
        assertThat(asset.getSummary()).isEqualTo("summary");
    }

    @Test
    void ingestAudioStoresSegmentsFromTranscription() {
        MockMultipartFile file = new MockMultipartFile("file", "audio.mp3", "audio/mpeg", "audio".getBytes());
        when(repository.findByOriginalFilenameWithSegments("audio.mp3")).thenReturn(List.of());
        when(storageService.store(file)).thenReturn("storage/audio.mp3");
        when(openAiClient.transcribe(any())).thenReturn(new OpenAiClient.TranscriptionResult(
                "transcript",
                List.of(new SegmentDto(0, 4, "hello"), new SegmentDto(4, 8, "world"))
        ));
        when(openAiClient.summarize("transcript")).thenReturn("summary");
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaAsset asset = assetService.ingest(file);

        assertThat(asset.getAssetType()).isEqualTo(AssetType.AUDIO);
        assertThat(asset.getSegments()).hasSize(2);
        assertThat(asset.getSegments()).extracting(TranscriptSegment::getText).containsExactly("hello", "world");
    }

    @Test
    void answerQuestionUsesTopRankedSegments() {
        MediaAsset asset = transcriptAsset();
        when(repository.findByIdWithSegments(1L)).thenReturn(Optional.of(asset));
        when(openAiClient.answerQuestion(any(), any())).thenReturn("Use the API key setup section.");

        ChatResponse response = assetService.answerQuestion(1L, "How do I set up the API key?");

        assertThat(response.answer()).contains("API key");
        assertThat(response.relevantSegments()).hasSize(1);
        assertThat(response.relevantSegments().get(0).text()).contains("API key");
    }

    @Test
    void findTopicSegmentsReturnsBestMatches() {
        MediaAsset asset = transcriptAsset();
        when(repository.findByIdWithSegments(1L)).thenReturn(Optional.of(asset));

        List<SegmentDto> segments = assetService.findTopicSegments(1L, "deployment");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).contains("deployment");
    }

    @Test
    void tokenizeRemovesPunctuation() {
        assertThat(AssetService.tokenize("API key, setup!")).containsExactly("api", "key", "setup");
    }

    @Test
    void scoreRewardsTermMatches() {
        assertThat(AssetService.score("This talks about docker deployment", List.of("docker", "deployment"))).isEqualTo(10);
    }

    @Test
    void findAllSortsNewestFirst() {
        MediaAsset older = new MediaAsset();
        older.setId(1L);
        older.setCreatedAt(java.time.Instant.parse("2024-01-01T00:00:00Z"));

        MediaAsset newer = new MediaAsset();
        newer.setId(2L);
        newer.setCreatedAt(java.time.Instant.parse("2024-01-02T00:00:00Z"));

        when(repository.findAllWithSegments()).thenReturn(List.of(older, newer));

        assertThat(assetService.findAll()).extracting(MediaAsset::getId).containsExactly(2L, 1L);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(repository.findByIdWithSegments(22L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.findById(22L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("22");
    }

    @Test
    void answerQuestionFallsBackToDocumentTextWhenNoSegmentsMatch() {
        MediaAsset asset = new MediaAsset();
        asset.setId(7L);
        asset.setAssetType(AssetType.PDF);
        asset.setExtractedText("Reference architecture uses mysql and spring boot.");
        when(repository.findByIdWithSegments(7L)).thenReturn(Optional.of(asset));
        when(openAiClient.answerQuestion("Reference architecture uses mysql and spring boot.", "What database is used?"))
                .thenReturn("MySQL is used.");

        ChatResponse response = assetService.answerQuestion(7L, "What database is used?");

        assertThat(response.answer()).isEqualTo("MySQL is used.");
        assertThat(response.relevantSegments()).isEmpty();
    }

    @Test
    void findTopicSegmentsBuildsDocumentChunksForPdfAssets() {
        MediaAsset asset = new MediaAsset();
        asset.setId(8L);
        asset.setAssetType(AssetType.PDF);
        asset.setExtractedText("Spring Boot handles the backend. React handles the frontend.");
        when(repository.findByIdWithSegments(8L)).thenReturn(Optional.of(asset));

        List<SegmentDto> segments = assetService.findTopicSegments(8L, "frontend");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).contains("frontend");
    }

    @Test
    void tokenizeFiltersStopWordsAndSingleLetters() {
        assertThat(AssetService.tokenize("How do I set up the API key in a app?"))
                .containsExactly("set", "api", "key", "app");
    }

    @Test
    void scoreReturnsZeroWhenTermsDoNotMatch() {
        assertThat(AssetService.score("completely unrelated text", List.of("docker"))).isZero();
    }

    @Test
    void ingestVideoDetectsVideoType() {
        MockMultipartFile file = new MockMultipartFile("file", "demo.mp4", "video/mp4", "video".getBytes());
        when(repository.findByOriginalFilenameWithSegments("demo.mp4")).thenReturn(List.of());
        when(storageService.store(file)).thenReturn("storage/demo.mp4");
        when(openAiClient.transcribe(any())).thenReturn(new OpenAiClient.TranscriptionResult("video transcript", List.of()));
        when(openAiClient.summarize("video transcript")).thenReturn("summary");
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaAsset asset = assetService.ingest(file);

        assertThat(asset.getAssetType()).isEqualTo(AssetType.VIDEO);
    }

    @Test
    void ingestReturnsExistingAssetWhenFilenameAlreadyExists() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "pdf".getBytes());
        MediaAsset existingAsset = new MediaAsset();
        existingAsset.setId(42L);
        existingAsset.setOriginalFilename("doc.pdf");
        existingAsset.setAssetType(AssetType.PDF);
        existingAsset.setExtractedText("saved text");
        existingAsset.setSummary("saved summary");
        when(repository.findByOriginalFilenameWithSegments("doc.pdf")).thenReturn(List.of(existingAsset));

        MediaAsset asset = assetService.ingest(file);

        assertThat(asset.getId()).isEqualTo(42L);
        verify(storageService, never()).store(any());
        verify(repository, never()).save(any());
        verify(pdfExtractorService, never()).extractText(any());
        verify(openAiClient, never()).summarize(any());
        verify(openAiClient, never()).transcribe(any());
    }

    private MediaAsset transcriptAsset() {
        MediaAsset asset = new MediaAsset();
        asset.setId(1L);
        asset.setAssetType(AssetType.VIDEO);
        asset.setExtractedText("General transcript");

        TranscriptSegment first = new TranscriptSegment();
        first.setAsset(asset);
        first.setStartSeconds(12);
        first.setEndSeconds(24);
        first.setText("The API key setup happens in the configuration section.");

        TranscriptSegment second = new TranscriptSegment();
        second.setAsset(asset);
        second.setStartSeconds(50);
        second.setEndSeconds(70);
        second.setText("We cover docker deployment and compose next.");

        asset.setSegments(List.of(first, second));
        return asset;
    }
}
