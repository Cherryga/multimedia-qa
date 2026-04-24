package com.assignment.multimediaqa.dto;

import java.util.List;

public record ChatResponse(String answer, List<SegmentDto> relevantSegments) {
}
