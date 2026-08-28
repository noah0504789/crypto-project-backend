package org.example.chat.infra.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(
        basePackages = {"org.example"},
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager")
public class DatasourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.write")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.write.hikari")
    public HikariDataSource writeDataSource() {
        return dataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * 물리 커넥션 획득을 첫 statement 시점까지 미룬다.
     *
     * <p>{@code ChatMessageCommandService.save} 는 {@code @Transactional} 안에서
     * {@code chatRoomPersistencePort.findById}(Mongo)를 먼저 호출한 뒤 outbox 를 INSERT 한다.
     * lazy proxy 가 없으면 트랜잭션 시작과 동시에 커넥션을 잡아 <b>Mongo 왕복 내내 커넥션을
     * 붙들고 있다.</b> 실제 SQL 은 수 ms 인데 점유는 그 몇 배가 된다.
     *
     * <p>2026-08-27 부하 측정(VU 60, 60 msg/s)에서 이 구조가 병목으로 드러났다 —
     * {@code hikaricp_connections_usage_seconds_max} 5.139초, {@code acquire_seconds_max} 5.343초,
     * {@code connections_timeout_total} 360건. 그 360건이 저장 실패로 이어져 브로드캐스트
     * 유실 10.06%(21,720/216,000)를 만들었고, 같은 실행에서 STOMP outbound 거절은 0건이었다.
     * 즉 병목은 팬아웃이 아니라 커넥션 점유시간이었다.
     *
     * <p>market 은 Read Replica 라우팅을 배선하면서 같은 프록시를 이미 쓰고 있다
     * (→ {@code docs/modules/MARKET.md §10}). 그쪽은 라우팅 결정 시점을 statement 로 미루는 것이
     * 목적이었고, 점유시간 단축은 같은 프록시가 주는 다른 효과다.
     */
    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("writeDataSource") DataSource writeDataSource) {
        return new LazyConnectionDataSourceProxy(writeDataSource);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("dataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("org.example")
                .persistenceUnit("common")
                .build();
    }

    @Bean("transactionManager")
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
