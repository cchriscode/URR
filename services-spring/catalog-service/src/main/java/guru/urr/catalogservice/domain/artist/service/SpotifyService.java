package guru.urr.catalogservice.domain.artist.service;

import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.Paging;

import java.io.IOException;

@Service
public class SpotifyService {

    private static final Logger log = LoggerFactory.getLogger(SpotifyService.class);
    private static final long SPOTIFY_API_THROTTLE_MS = 100;

    private final JdbcTemplate jdbcTemplate;
    private final String clientId;
    private final String clientSecret;

    public SpotifyService(
        JdbcTemplate jdbcTemplate,
        @Value("${SPOTIFY_CLIENT_ID:}") String clientId,
        @Value("${SPOTIFY_CLIENT_SECRET:}") String clientSecret
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public Map<String, Object> enrichArtists() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            log.warn("Spotify credentials not configured. Skipping artist enrichment.");
            return Map.of("message", "Spotify credentials not configured", "enriched", 0);
        }

        try {
            SpotifyApi spotifyApi = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .build();

            ClientCredentials credentials = spotifyApi.clientCredentials().build().execute();
            spotifyApi.setAccessToken(credentials.getAccessToken());

            List<Map<String, Object>> artists = jdbcTemplate.queryForList(
                "SELECT id, name FROM artists WHERE image_url IS NULL OR image_url = ''");

            int enriched = 0;
            for (Map<String, Object> artist : artists) {
                String name = String.valueOf(artist.get("name"));
                try {
                    Paging<Artist> results = spotifyApi.searchArtists(name)
                        .limit(1)
                        .build()
                        .execute();

                    if (results.getItems().length > 0) {
                        Artist spotifyArtist = results.getItems()[0];
                        String imageUrl = null;
                        Image[] images = spotifyArtist.getImages();
                        if (images != null && images.length > 0) {
                            imageUrl = images[0].getUrl();
                        }

                        String genres = spotifyArtist.getGenres() != null
                            ? String.join(", ", spotifyArtist.getGenres())
                            : null;

                        if (imageUrl != null || genres != null) {
                            jdbcTemplate.update("""
                                UPDATE artists
                                SET image_url = COALESCE(?, image_url),
                                    description = COALESCE(?, description),
                                    updated_at = NOW()
                                WHERE id = ?
                                """, imageUrl, genres, artist.get("id"));
                            enriched++;
                            log.info("Enriched artist '{}' with Spotify data", name);
                        }
                    }

                    Thread.sleep(SPOTIFY_API_THROTTLE_MS);
                } catch (SpotifyWebApiException | ParseException e) {
                    log.warn("Spotify search failed for '{}': {}", name, e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            return Map.of("message", "Artist enrichment complete", "enriched", enriched, "total", artists.size());

        } catch (IOException | SpotifyWebApiException | ParseException e) {
            log.error("Failed to authenticate with Spotify: {}", e.getMessage());
            return Map.of("message", "Spotify authentication failed: " + e.getMessage(), "enriched", 0);
        }
    }
}
