package com.tiketi.catalogservice.domain.admin.controller;

import com.tiketi.catalogservice.domain.admin.service.ImageUploadService;
import com.tiketi.catalogservice.shared.security.JwtTokenParser;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/image")
public class ImageController {

    private final ImageUploadService imageUploadService;
    private final JwtTokenParser jwtTokenParser;

    public ImageController(ImageUploadService imageUploadService, JwtTokenParser jwtTokenParser) {
        this.imageUploadService = imageUploadService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @PostMapping("/upload")
    public Map<String, String> upload(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam("image") MultipartFile image) {
        jwtTokenParser.requireAdmin(authorization);
        return imageUploadService.upload(image);
    }
}
