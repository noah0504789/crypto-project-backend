package org.example.common.inbox.application.service;

import org.example.common.inbox.adapter.out.InboxRepository;
import org.example.common.inbox.domain.Inbox;
import org.example.common.inbox.exception.DuplicateInboxException;
import org.example.common.outbox.adapter.out.JpaOutbox;
import org.example.common.outbox.adapter.out.JpaOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext
@SpringBootTest(classes = InboxServiceIntegrationTest.Config.class)
class InboxServiceIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("event");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Inbox.class, JpaOutbox.class})
    @EnableJpaRepositories(basePackageClasses = {InboxRepository.class, JpaOutboxRepository.class})
    @Import(InboxService.class)
    static class Config {
    }

    @Autowired
    private InboxService inboxService;
    @Autowired
    private InboxRepository inboxRepository;
    @Autowired
    private JpaOutboxRepository outboxRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        jdbcTemplate.update("DELETE FROM outbox");
        jdbcTemplate.update("DELETE FROM inbox");
    }

    @Test
    @DisplayName("신규 Inbox는 저장과 조회 후 기존 엔티티로 판정한다")
    void persistenceLifecycle_updatesNewState() {
        // given
        Inbox inbox = Inbox.of("consumer", "event-1");
        assertThat(inbox.isNew()).isTrue();

        // when
        transaction.executeWithoutResult(status -> inboxRepository.saveAndFlush(inbox));

        // then
        assertThat(inbox.isNew()).isFalse();
        Inbox loaded = inboxRepository.findById(inbox.getId()).orElseThrow();
        assertThat(loaded.isNew()).isFalse();
        assertThat(Inbox.of("consumer", "event-1").isNew()).isTrue();
    }

    @Test
    @DisplayName("커밋된 이벤트의 재전달은 새 Outbox 생성을 차단한다")
    void redelivery_blocksSecondOutbox() {
        // given
        process("consumer", "event-1");

        // when / then
        assertThatThrownBy(() -> process("consumer", "event-1"))
                .isInstanceOf(DuplicateInboxException.class);
        assertCounts(1, 1);
    }

    @Test
    @DisplayName("서로 다른 소비자는 같은 이벤트를 각각 처리한다")
    void differentConsumers_processIndependently() {
        // when
        process("first", "event-1");
        process("second", "event-1");

        // then
        assertCounts(2, 2);
    }

    @Test
    @DisplayName("후속 처리 실패는 Inbox와 Outbox를 롤백하고 재처리를 허용한다")
    void failure_rollsBackInboxAndOutbox() {
        // when
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            inboxService.save("consumer", "event-1");
            saveOutbox();
            throw new IllegalStateException("downstream failure");
        })).isInstanceOf(IllegalStateException.class);

        // then
        assertCounts(0, 0);
        process("consumer", "event-1");
        assertCounts(1, 1);
    }

    @Test
    @DisplayName("Outbox 저장 실패는 Inbox 선점도 롤백한다")
    void outboxFailure_rollsBackClaim() {
        // when / then
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            inboxService.save("consumer", "event-1");
            outboxRepository.saveAndFlush(JpaOutbox.builder()
                    .id(UUID.randomUUID().toString())
                    .build());
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertCounts(0, 0);

        process("consumer", "event-1");
        assertCounts(1, 1);
    }

    @Test
    @DisplayName("Inbox 길이 제약 위반은 중복 성공으로 처리하지 않는다")
    void invalidInbox_propagatesFailure() {
        // when / then
        assertThatThrownBy(() -> process("consumer", "x".repeat(65)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(DuplicateInboxException.class);
        assertCounts(0, 0);
    }

    @Test
    @DisplayName("동시 재전달은 하나의 트랜잭션만 후속 Outbox를 커밋한다")
    void concurrentRedelivery_commitsOnce() throws Exception {
        // given
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("start timeout");
            }
            try {
                process("consumer", "event-1");
                return true;
            } catch (DuplicateInboxException e) {
                return false;
            }
        };
        try {
            // when
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            // then
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertCounts(1, 1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private void process(String consumer, String eventId) {
        transaction.executeWithoutResult(status -> {
            inboxService.save(consumer, eventId);
            saveOutbox();
        });
    }

    private void saveOutbox() {
        JpaOutbox outbox = JpaOutbox.builder()
                .id(UUID.randomUUID().toString())
                .payload("{}")
                .build();
        outboxRepository.saveAndFlush(outbox);
    }

    private void assertCounts(int inboxCount, int outboxCount) {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inbox", Integer.class)).isEqualTo(inboxCount);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class)).isEqualTo(outboxCount);
    }
}
