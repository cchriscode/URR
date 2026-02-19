package guru.urr.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InternalTokenValidator {

    private final String internalToken;

    public InternalTokenValidator(String internalToken) {
        this.internalToken = internalToken;
    }

    public void requireValidToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal token required");
        }
        String token = authorization.substring(7);
        if (!timingSafeEquals(internalToken, token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
        }
    }

    private static boolean timingSafeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
