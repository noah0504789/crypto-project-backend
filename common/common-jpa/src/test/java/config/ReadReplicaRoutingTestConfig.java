package config;

import com.zaxxer.hikari.HikariDataSource;
import org.example.common.jpa.aop.ReadReplicaAspect;
import org.example.common.jpa.annotation.ReadReplica;
import org.example.common.jpa.datasource.DataSourceType;
import org.example.common.jpa.datasource.ReplicationRoutingDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@TestConfiguration
@Import(ReadReplicaAspect.class)
public class ReadReplicaRoutingTestConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.write")
    public DataSourceProperties writeDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.read")
    public DataSourceProperties readDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.write.hikari")
    public DataSource writeDataSource(
            @Qualifier("writeDataSourceProperties") DataSourceProperties properties
    ) {
        return properties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.read.hikari")
    public DataSource readDataSource(
            @Qualifier("readDataSourceProperties") DataSourceProperties properties
    ) {
        return properties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    public DataSource routingDataSource(
            @Qualifier("writeDataSource") DataSource writeDataSource,
            @Qualifier("readDataSource") DataSource readDataSource
    ) {
        ReplicationRoutingDataSource routingDataSource = new ReplicationRoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.WRITE, writeDataSource);
        targetDataSources.put(DataSourceType.READ, readDataSource);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(writeDataSource);
        routingDataSource.afterPropertiesSet();

        return routingDataSource;
    }

    @Bean
    @Primary
    public DataSource dataSource(
            @Qualifier("routingDataSource") DataSource routingDataSource
    ) {
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    @Bean
    public RoutingMarkerRepository routingMarkerRepository(JdbcTemplate jdbcTemplate) {
        return new RoutingMarkerRepository(jdbcTemplate);
    }

    @Bean
    public ReadReplicaMarkerQueryService readReplicaMarkerQueryService(RoutingMarkerRepository repository) {
        return new ReadReplicaMarkerQueryService(repository);
    }

    @Bean
    public RoutingMarkerQueryService routingMarkerQueryService(RoutingMarkerRepository repository, ReadReplicaMarkerQueryService readReplicaMarkerQueryService) {
        return new RoutingMarkerQueryService(repository, readReplicaMarkerQueryService);
    }

    public static class RoutingMarkerQueryService {

        private final RoutingMarkerRepository repository;
        private final ReadReplicaMarkerQueryService readReplicaMarkerQueryService;

        public RoutingMarkerQueryService(
                RoutingMarkerRepository repository,
                ReadReplicaMarkerQueryService readReplicaMarkerQueryService
        ) {
            this.repository = repository;
            this.readReplicaMarkerQueryService = readReplicaMarkerQueryService;
        }

        public String findWithoutReadReplica() {
            return repository.findMarker();
        }

        @Transactional(readOnly = true)
        public String findWithReadOnlyTransactionOnly() {
            return repository.findMarker();
        }

        @Transactional
        public String findWithReadReplicaInsideWriteTransaction() {
            return readReplicaMarkerQueryService.findWithReadReplicaAndReadOnlyTransaction();
        }
    }

    public static class ReadReplicaMarkerQueryService {

        private final RoutingMarkerRepository repository;

        public ReadReplicaMarkerQueryService(RoutingMarkerRepository repository) {
            this.repository = repository;
        }

        @ReadReplica
        public String findWithReadReplica() {
            return repository.findMarker();
        }

        @ReadReplica
        @Transactional(readOnly = true)
        public String findWithReadReplicaAndReadOnlyTransaction() {
            return repository.findMarker();
        }
    }

    public static class RoutingMarkerRepository {

        private final JdbcTemplate jdbcTemplate;

        public RoutingMarkerRepository(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        public String findMarker() {
            return jdbcTemplate.queryForObject(
                    "select marker from routing_marker where id = 1",
                    String.class
            );
        }
    }
}