package guru.urr.communityservice.controller;

import guru.urr.communityservice.dto.PostCreateRequest;
import guru.urr.communityservice.dto.PostUpdateRequest;
import guru.urr.communityservice.service.PostService;
import guru.urr.communityservice.shared.security.AuthUser;
import guru.urr.communityservice.shared.security.JwtTokenParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/community/posts")
public class CommunityPostController {

    private final PostService postService;
    private final JwtTokenParser jwtTokenParser;

    public CommunityPostController(PostService postService, JwtTokenParser jwtTokenParser) {
        this.postService = postService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) UUID artistId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(postService.list(artistId, page, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(postService.detail(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody PostCreateRequest request,
            HttpServletRequest httpRequest) {
        AuthUser user = jwtTokenParser.requireUser(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.create(request, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID id,
            @Valid @RequestBody PostUpdateRequest request,
            HttpServletRequest httpRequest) {
        AuthUser user = jwtTokenParser.requireUser(httpRequest);
        return ResponseEntity.ok(postService.update(id, request, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        AuthUser user = jwtTokenParser.requireUser(httpRequest);
        return ResponseEntity.ok(postService.delete(id, user));
    }
}
