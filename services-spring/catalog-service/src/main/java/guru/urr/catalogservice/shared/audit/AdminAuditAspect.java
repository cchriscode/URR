package guru.urr.catalogservice.shared.audit;

import guru.urr.catalogservice.shared.security.AuthUser;
import guru.urr.catalogservice.shared.security.JwtTokenParser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AdminAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditAspect.class);

    private final JdbcTemplate jdbcTemplate;
    private final JwtTokenParser jwtTokenParser;

    public AdminAuditAspect(JdbcTemplate jdbcTemplate, JwtTokenParser jwtTokenParser) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtTokenParser = jwtTokenParser;
    }

    @Around("@annotation(auditLog)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        HttpServletRequest httpRequest = extractHttpServletRequest(joinPoint);
        UUID adminUserId = null;
        UUID resourceId = extractResourceId(joinPoint);

        try {
            if (httpRequest != null) {
                AuthUser admin = jwtTokenParser.requireUser(httpRequest);
                adminUserId = UUID.fromString(admin.userId());
            }
        } catch (Exception e) {
            // If we can't parse the headers, still proceed with the request
        }

        Object result;
        int responseStatus = 200;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            responseStatus = 500;
            logAudit(adminUserId, auditLog.action(), auditLog.resourceType(), resourceId, responseStatus);
            throw e;
        }

        logAudit(adminUserId, auditLog.action(), auditLog.resourceType(), resourceId, responseStatus);
        return result;
    }

    private void logAudit(UUID adminUserId, String action, String resourceType, UUID resourceId, int responseStatus) {
        try {
            jdbcTemplate.update("""
                INSERT INTO admin_audit_logs (admin_user_id, action, resource_type, resource_id, response_status)
                VALUES (?, ?, ?, ?, ?)
                """, adminUserId, action, resourceType, resourceId, responseStatus);
        } catch (Exception e) {
            log.warn("Failed to write audit log: action={}, resource={}: {}", action, resourceType, e.getMessage());
        }
    }

    private HttpServletRequest extractHttpServletRequest(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest req) {
                return req;
            }
        }
        return null;
    }

    private UUID extractResourceId(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid;
            }
        }
        return null;
    }
}
