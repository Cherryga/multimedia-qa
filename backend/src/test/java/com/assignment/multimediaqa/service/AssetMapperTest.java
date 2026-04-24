package com.assignment.multimediaqa.service;

import com.assignment.multimediaqa.dto.AssetResponse;
import com.assignment.multimediaqa.entity.AssetType;
import com.assignment.multimediaqa.entity.MediaAsset;
import com.assignment.multimediaqa.entity.TranscriptSegment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssetMapperTest {

    @Test
    void toResponseBuildsMediaUrl() {
        MediaAsset asset = new MediaAsset();
        asset.setId(9L);
        asset.setAssetType(AssetType.PDF);
        asset.setOriginalFilename("file.pdf");
        asset.setSummary("summary");
        asset.setExtractedText("text");
        asset.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));

        AssetResponse response = new AssetMapper().toResponse(asset);

        assertThat(response.mediaUrl()).isEqualTo("/api/assets/9/media");
        assertThat(response.originalFilename()).isEqualTo("file.pdf");
    }

    @Test
    void toSegmentsMapsTranscriptEntries() {
        TranscriptSegment segment = new TranscriptSegment();
        segment.setStartSeconds(3);
        segment.setEndSeconds(8);
        segment.setText("hello");

        assertThat(new AssetMapper().toSegments(List.of(segment)))
                .extracting("text")
                .containsExactly("hello");
    }
}
