package guru.urr.catalogservice.domain.admin.controller;

import guru.urr.catalogservice.domain.admin.service.ImageUploadService;
import guru.urr.common.security.JwtTokenParser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
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
        HttpServletRequest request,
        @RequestParam("image") MultipartFile image) {
        jwtTokenParser.requireAdmin(request);
        return imageUploadService.upload(image);
    }
}
