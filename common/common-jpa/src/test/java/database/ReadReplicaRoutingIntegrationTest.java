package database;

import org.example.common.test.testcontainer.ReadWriteMysqlTestContainerInitializer;
import config.ReadReplicaRoutingTestConfig;
import org.example.common.test.config.TestBootApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ContextConfiguration(
        classes = {TestBootApplication.class, ReadReplicaRoutingTestConfig.class},
        initializers = ReadWriteMysqlTestContainerInitializer.class
)
class ReadReplicaRoutingIntegrationTest {

    @Autowired
    private ReadReplicaRoutingTestConfig.RoutingMarkerQueryService queryService;

    @BeforeEach
    void setUp() throws Exception {
        initializeMarkerTable(
                ReadWriteMysqlTestContainerInitializer.writeMysql().getJdbcUrl(),
                ReadWriteMysqlTestContainerInitializer.writeMysql().getUsername(),
                ReadWriteMysqlTestContainerInitializer.writeMysql().getPassword(),
                "WRITE_DB"
        );

        initializeMarkerTable(
                ReadWriteMysqlTestContainerInitializer.readMysql().getJdbcUrl(),
                ReadWriteMysqlTestContainerInitializer.readMysql().getUsername(),
                ReadWriteMysqlTestContainerInitializer.readMysql().getPassword(),
                "READ_DB"
        );
    }

    @Test
    @DisplayName("@ReadReplica가 없으면 WRITE DB로 라우팅된다")
    void withoutReadReplica_routesToWrite() {
        String result = queryService.findWithoutReadReplica();

        assertThat(result).isEqualTo("WRITE_DB");
    }

    @Test
    @DisplayName("@ReadReplica가 있으면 READ DB로 라우팅된다")
    void withReadReplica_routesToRead() {
        String result = queryService.findWithReadReplica();

        assertThat(result).isEqualTo("READ_DB");
    }

    @Test
    @DisplayName("@Transactional(readOnly = true)만 있으면 WRITE DB로 라우팅된다")
    void readOnlyTransactionOnly_routesToWrite() {
        String result = queryService.findWithReadOnlyTransactionOnly();

        assertThat(result).isEqualTo("WRITE_DB");
    }

    @Test
    @DisplayName("@ReadReplica와 @Transactional(readOnly = true)가 같이 있으면 READ DB로 라우팅된다")
    void readReplicaAndReadOnlyTransaction_routesToRead() {
        String result = queryService.findWithReadReplicaAndReadOnlyTransaction();

        assertThat(result).isEqualTo("READ_DB");
    }

    private static void initializeMarkerTable(String jdbcUrl, String username, String password, String markerValue) throws Exception {
        try (
                Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
                Statement statement = connection.createStatement()
        ) {
            statement.execute("drop table if exists routing_marker");
            statement.execute("""
                    create table routing_marker (
                        id bigint primary key,
                        marker varchar(100) not null
                    )
                    """);
            statement.execute("""
                    insert into routing_marker(id, marker)
                    values (1, '%s')
                    """.formatted(markerValue));
        }
    }
}