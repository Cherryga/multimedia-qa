package com.assignment.multimediaqa.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiClientTest {

    @Test
    void resolveApiKeyUsesConfiguredValueWhenPresent() {
        assertThat(OpenAiClient.resolveApiKey(" test-key "))
                .isEqualTo("test-key");
    }

    @Test
    void resolveApiKeyReturnsEmptyWhenNothingConfigured() {
        assertThat(OpenAiClient.resolveApiKey("   "))
                .isEmpty();
    }

    @Test
    void fallbackSummaryTruncatesLongContent() {
        String input = "word ".repeat(100);

        String summary = OpenAiClient.fallbackSummary(input);

        assertThat(summary).endsWith("...");
        assertThat(summary.length()).isLessThanOrEqualTo(323);
    }

    @Test
    void fallbackSummaryHandlesEmptyContent() {
        assertThat(OpenAiClient.fallbackSummary("   "))
                .isEqualTo("No extractable text was found in the uploaded file.");
    }
}
