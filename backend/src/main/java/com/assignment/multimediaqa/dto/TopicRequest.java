package com.assignment.multimediaqa.dto;

import jakarta.validation.constraints.NotBlank;

public record TopicRequest(@NotBlank String topic) {
}
