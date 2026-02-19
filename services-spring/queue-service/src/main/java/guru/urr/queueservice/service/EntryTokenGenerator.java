package guru.urr.queueservice.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EntryTokenGenerator {

    private final SecretKey entryTokenKey;
    private final int entryTokenTtlSeconds;

    public EntryTokenGenerator(
        @Value("${queue.entry-token.secret}") String entryTokenSecret,
        @Value("${queue.entry-token.ttl-seconds:600}") int entryTokenTtlSeconds
    ) {
        this.entryTokenKey = Keys.hmacShaKeyFor(entryTokenSecret.getBytes(StandardCharsets.UTF_8));
        this.entryTokenTtlSeconds = entryTokenTtlSeconds;
    }

    public String generate(String eventId, String userId) {
        long nowMs = System.currentTimeMillis();
        Date issuedAt = new Date(nowMs);
        Date expiration = new Date(nowMs + (entryTokenTtlSeconds * 1000L));

        return Jwts.builder()
            .subject(eventId)
            .claim("uid", userId)
            .issuedAt(issuedAt)
            .expiration(expiration)
            .signWith(entryTokenKey)
            .compact();
    }
}
