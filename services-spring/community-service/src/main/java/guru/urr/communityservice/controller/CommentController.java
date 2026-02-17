package guru.urr.communityservice.controller;

import guru.urr.communityservice.dto.CommentCreateRequest;
import guru.urr.communityservice.service.CommentService;
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
@RequestMapping("/api/community/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;
    private final JwtTokenParser jwtTokenParser;

    public CommentController(CommentService commentService, JwtTokenParser jwtTokenParser) {
        this.commentService = commentService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable UUID postId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(commentService.listByPost(postId, page, limit));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable UUID postId,
            @Valid @RequestBody CommentCreateRequest request,
            HttpServletRequest httpRequest) {
        AuthUser user = jwtTokenParser.requireUser(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.create(postId, request, user));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            HttpServletRequest httpRequest) {
        AuthUser user = jwtTokenParser.requireUser(httpRequest);
        return ResponseEntity.ok(commentService.delete(commentId, user));
    }
}
