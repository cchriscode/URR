package guru.urr.catalogservice.shared.util;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static UUID requireUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName + " format");
        }
    }
}
