package com.assignment.multimediaqa.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String question) {
}
