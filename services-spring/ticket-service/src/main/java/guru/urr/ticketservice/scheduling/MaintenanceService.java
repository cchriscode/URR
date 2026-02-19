package guru.urr.ticketservice.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public MaintenanceService(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${event.status.interval-ms:60000}")
    @Transactional
    public void updateEventStatuses() {
        jdbcTemplate.update("""
            UPDATE events
            SET status = 'on_sale', updated_at = NOW()
            WHERE status = 'upcoming' AND sale_start_date <= NOW() AND sale_end_date > NOW()
            """);

        jdbcTemplate.update("""
            UPDATE events
            SET status = 'ended', updated_at = NOW()
            WHERE status IN ('upcoming', 'on_sale')
              AND sale_end_date <= NOW()
              AND status != 'cancelled'
            """);

        jdbcTemplate.update("""
            UPDATE events
            SET status = 'ended', updated_at = NOW()
            WHERE status NOT IN ('ended', 'cancelled')
              AND event_date < NOW()
            """);
    }

    @Transactional
    public void forceStatusReschedule() {
        updateEventStatuses();
    }
}
