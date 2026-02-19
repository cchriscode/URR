package guru.urr.communityservice.shared.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DataSourceRoutingConfig {

    @Value("${spring.datasource.url}")
    private String primaryUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.replica-url:}")
    private String replicaUrl;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariDataSource primary = new HikariDataSource();
        primary.setJdbcUrl(primaryUrl);
        primary.setUsername(username);
        primary.setPassword(password);
        primary.setPoolName("primary");

        String effectiveReplicaUrl = (replicaUrl != null && !replicaUrl.isBlank())
                ? replicaUrl : primaryUrl;

        HikariDataSource replica = new HikariDataSource();
        replica.setJdbcUrl(effectiveReplicaUrl);
        replica.setUsername(username);
        replica.setPassword(password);
        replica.setReadOnly(true);
        replica.setPoolName("replica");

        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
        routing.setTargetDataSources(Map.of("primary", primary, "replica", replica));
        routing.setDefaultTargetDataSource(primary);
        return routing;
    }
}
