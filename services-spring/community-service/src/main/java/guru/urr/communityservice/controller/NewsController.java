package guru.urr.communityservice.controller;

import guru.urr.communityservice.dto.NewsCreateRequest;
import guru.urr.communityservice.dto.NewsUpdateRequest;
import guru.urr.communityservice.shared.security.AuthUser;
import guru.urr.communityservice.shared.security.JwtTokenParser;
import guru.urr.communityservice.service.NewsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService newsService;
    private final JwtTokenParser jwtTokenParser;

    public NewsController(NewsService newsService, JwtTokenParser jwtTokenParser) {
        this.newsService = newsService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer limit
    ) {
        return newsService.list(page, limit);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable UUID id) {
        return newsService.detail(id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
        HttpServletRequest httpRequest,
        @Valid @RequestBody NewsCreateRequest request
    ) {
        AuthUser user = jwtTokenParser.requireAdmin(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(newsService.create(request, user));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
        @PathVariable UUID id,
        HttpServletRequest httpRequest,
        @Valid @RequestBody NewsUpdateRequest request
    ) {
        AuthUser user = jwtTokenParser.requireAdmin(httpRequest);
        return newsService.update(id, request, user);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(
        @PathVariable UUID id,
        HttpServletRequest httpRequest
    ) {
        AuthUser user = jwtTokenParser.requireAdmin(httpRequest);
        return newsService.delete(id, user);
    }
}
