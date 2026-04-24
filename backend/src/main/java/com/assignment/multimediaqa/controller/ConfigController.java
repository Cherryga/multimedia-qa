package com.assignment.multimediaqa.controller;

import com.assignment.multimediaqa.service.OpenAiClient;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = "${APP_CORS_ORIGINS:http://localhost:5173}")
public class ConfigController {

    private final OpenAiClient openAiClient;

    public ConfigController(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    @GetMapping
    public Map<String, Object> getConfig() {
        return Map.of(
                "openAiConfigured", openAiClient.isConfigured(),
                "serverPort", System.getProperty("server.port", "")
        );
    }
}

