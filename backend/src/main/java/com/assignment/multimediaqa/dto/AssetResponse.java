package com.assignment.multimediaqa.dto;

import com.assignment.multimediaqa.entity.AssetType;

import java.time.Instant;
import java.util.List;

public record AssetResponse(
        Long id,
        String originalFilename,
        AssetType assetType,
        String summary,
        String extractedText,
        String mediaUrl,
        Instant createdAt,
        List<SegmentDto> segments
) {
}
