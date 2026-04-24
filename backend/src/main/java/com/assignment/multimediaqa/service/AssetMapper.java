package com.assignment.multimediaqa.service;

import com.assignment.multimediaqa.dto.AssetResponse;
import com.assignment.multimediaqa.dto.SegmentDto;
import com.assignment.multimediaqa.entity.MediaAsset;
import com.assignment.multimediaqa.entity.TranscriptSegment;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssetMapper {

    public AssetResponse toResponse(MediaAsset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getOriginalFilename(),
                asset.getAssetType(),
                asset.getSummary(),
                asset.getExtractedText(),
                "/api/assets/" + asset.getId() + "/media",
                asset.getCreatedAt(),
                toSegments(asset.getSegments())
        );
    }

    public List<SegmentDto> toSegments(List<TranscriptSegment> segments) {
        return segments.stream()
                .map(segment -> new SegmentDto(segment.getStartSeconds(), segment.getEndSeconds(), segment.getText()))
                .toList();
    }
}
