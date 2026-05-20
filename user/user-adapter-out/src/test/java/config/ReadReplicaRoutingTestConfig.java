package config;

import org.example.common.aop.ReadReplicaAspect;
import org.example.infra.annotation.ReadReplica;
import org.example.infra.mysql.DataSourceConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@TestConfiguration
@Import({DataSourceConfig.class, ReadReplicaAspect.class})
public class ReadReplicaRoutingTestConfig {

    @Bean
    public RoutingMarkerQueryService routingMarkerQueryService(JdbcTemplate jdbcTemplate) {
        return new RoutingMarkerQueryService(jdbcTemplate);
    }

    public static class RoutingMarkerQueryService {

        private final JdbcTemplate jdbcTemplate;

        public RoutingMarkerQueryService(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        public String findWithoutReadReplica() {
            return findMarker();
        }

        @ReadReplica
        public String findWithReadReplica() {
            return findMarker();
        }

        @Transactional(readOnly = true)
        public String findWithReadOnlyTransactionOnly() {
            return findMarker();
        }

        @ReadReplica
        @Transactional(readOnly = true)
        public String findWithReadReplicaAndReadOnlyTransaction() {
            return findMarker();
        }

        private String findMarker() {
            return jdbcTemplate.queryForObject(
                    "select marker from routing_marker where id = 1",
                    String.class
            );
        }
    }
}