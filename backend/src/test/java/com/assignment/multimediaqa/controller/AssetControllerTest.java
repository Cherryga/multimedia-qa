package com.assignment.multimediaqa.controller;

import com.assignment.multimediaqa.dto.ChatResponse;
import com.assignment.multimediaqa.dto.SegmentDto;
import com.assignment.multimediaqa.entity.AssetType;
import com.assignment.multimediaqa.entity.MediaAsset;
import com.assignment.multimediaqa.service.AssetMapper;
import com.assignment.multimediaqa.service.AssetService;
import com.assignment.multimediaqa.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssetControllerTest {

    private AssetService assetService;
    private FileStorageService storageService;
    private AssetController controller;

    @BeforeEach
    void setUp() {
        assetService = mock(AssetService.class);
        storageService = mock(FileStorageService.class);
        controller = new AssetController(assetService, new AssetMapper(), storageService);
    }

    @Test
    void uploadDelegatesToService() {
        MockMultipartFile file = new MockMultipartFile("file", "file.pdf", "application/pdf", "x".getBytes());
        MediaAsset asset = asset(1L);
        when(assetService.ingest(file)).thenReturn(asset);

        assertThat(controller.upload(file).id()).isEqualTo(1L);
    }

    @Test
    void listReturnsMappedResponses() {
        when(assetService.findAll()).thenReturn(List.of(asset(1L), asset(2L)));

        assertThat(controller.list()).hasSize(2);
    }

    @Test
    void chatReturnsAnswer() {
        when(assetService.answerQuestion(3L, "question")).thenReturn(new ChatResponse("answer", List.of()));

        assertThat(controller.chat(3L, new com.assignment.multimediaqa.dto.ChatRequest("question")).answer()).isEqualTo("answer");
    }

    @Test
    void topicsReturnsSegments() {
        when(assetService.findTopicSegments(4L, "topic")).thenReturn(List.of(new SegmentDto(0, 1, "topic")));

        assertThat(controller.topics(4L, new com.assignment.multimediaqa.dto.TopicRequest("topic"))).hasSize(1);
    }

    @Test
    void mediaLoadsStoredResource() {
        MediaAsset asset = asset(5L);
        asset.setContentType("audio/mpeg");
        asset.setStoragePath("storage/path.mp3");
        when(assetService.findById(5L)).thenReturn(asset);
        when(storageService.loadAsResource("storage/path.mp3")).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));

        var response = controller.media(5L);

        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("audio/mpeg");
    }

    private MediaAsset asset(Long id) {
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setOriginalFilename("file-" + id + ".pdf");
        asset.setAssetType(AssetType.PDF);
        asset.setExtractedText("text");
        asset.setSummary("summary");
        return asset;
    }
}
