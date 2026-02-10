package com.tiketi.ticketservice.controller;

import com.tiketi.ticketservice.security.JwtTokenParser;
import com.tiketi.ticketservice.service.ArtistService;
import com.tiketi.ticketservice.service.SpotifyService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artists")
public class ArtistController {

    private final ArtistService artistService;
    private final SpotifyService spotifyService;
    private final JwtTokenParser jwtTokenParser;

    public ArtistController(ArtistService artistService, SpotifyService spotifyService, JwtTokenParser jwtTokenParser) {
        this.artistService = artistService;
        this.spotifyService = spotifyService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return artistService.listArtists(page, limit);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable UUID id) {
        return artistService.getArtistDetail(id);
    }

    @PostMapping("/enrich")
    public Map<String, Object> enrich(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        jwtTokenParser.requireAdmin(authorization);
        return spotifyService.enrichArtists();
    }
}
