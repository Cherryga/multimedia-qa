package com.assignment.multimediaqa.controller;

import com.assignment.multimediaqa.dto.AssetResponse;
import com.assignment.multimediaqa.dto.ChatRequest;
import com.assignment.multimediaqa.dto.ChatResponse;
import com.assignment.multimediaqa.dto.SegmentDto;
import com.assignment.multimediaqa.dto.TopicRequest;
import com.assignment.multimediaqa.entity.MediaAsset;
import com.assignment.multimediaqa.service.AssetMapper;
import com.assignment.multimediaqa.service.AssetService;
import com.assignment.multimediaqa.service.FileStorageService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
@CrossOrigin(origins = "${APP_CORS_ORIGINS:http://localhost:5173}")
public class AssetController {

    private final AssetService assetService;
    private final AssetMapper assetMapper;
    private final FileStorageService fileStorageService;

    public AssetController(AssetService assetService, AssetMapper assetMapper, FileStorageService fileStorageService) {
        this.assetService = assetService;
        this.assetMapper = assetMapper;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssetResponse upload(@RequestPart("file") MultipartFile file) {
        return assetMapper.toResponse(assetService.ingest(file));
    }

    @GetMapping
    public List<AssetResponse> list() {
        return assetService.findAll().stream().map(assetMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public AssetResponse get(@PathVariable Long id) {
        return assetMapper.toResponse(assetService.findById(id));
    }

    @PostMapping("/{id}/chat")
    public ChatResponse chat(@PathVariable Long id, @Valid @RequestBody ChatRequest request) {
        return assetService.answerQuestion(id, request.question());
    }

    @PostMapping("/{id}/topics")
    public List<SegmentDto> topics(@PathVariable Long id, @Valid @RequestBody TopicRequest request) {
        return assetService.findTopicSegments(id, request.topic());
    }

    @GetMapping("/{id}/media")
    public ResponseEntity<Resource> media(@PathVariable Long id) {
        MediaAsset asset = assetService.findById(id);
        Resource resource = fileStorageService.loadAsResource(asset.getStoragePath());
        MediaType mediaType = asset.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(asset.getContentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", "inline; filename=\"" + asset.getOriginalFilename() + "\"")
                .body(resource);
    }
}
