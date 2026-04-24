package com.assignment.multimediaqa.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndLoadsFile() throws Exception {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        String storedPath = service.store(file);

        assertThat(Files.exists(Path.of(storedPath))).isTrue();
        assertThat(service.loadAsResource(storedPath).exists()).isTrue();
    }
}
