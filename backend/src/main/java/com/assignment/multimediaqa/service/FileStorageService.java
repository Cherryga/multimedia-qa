package com.assignment.multimediaqa.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path storageRoot;

    public FileStorageService(@Value("${app.storage.path}") String storagePath) {
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageRoot);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public String store(MultipartFile file) {
        String safeName = UUID.randomUUID() + "-" + file.getOriginalFilename().replaceAll("\\s+", "_");
        Path destination = storageRoot.resolve(safeName).normalize();
        try {
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return destination.toString();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public Resource loadAsResource(String storagePath) {
        return new FileSystemResource(Path.of(storagePath));
    }
}
