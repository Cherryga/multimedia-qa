package com.assignment.multimediaqa.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesMissingAsset() {
        var response = handler.handleNotFound(new IllegalArgumentException("missing"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).containsEntry("message", "missing");
    }

    @Test
    void handlesValidationError() {
        var response = handler.handleValidation(mock(MethodArgumentNotValidException.class));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("message", "Validation failed");
    }
}
