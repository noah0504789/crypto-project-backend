package org.example.chat.infra.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.connection.ConnectionPoolSettings;
import io.micrometer.observation.ObservationRegistry;
import org.example.common.mongo.converter.DateToLocalDateTimeConverter;
import org.example.common.mongo.converter.LocalDateTimeToDateConverter;
import org.example.common.mongo.SnakeCaseFieldNamingStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.observability.ContextProviderFactory;
import org.springframework.data.mongodb.observability.MongoObservationCommandListener;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@EnableMongoRepositories(
    basePackages = {"org.example.chatroom", "org.example.chatmessage", "org.example.outbox"},
    mongoTemplateRef = "mongoTemplate"
)
@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${mongo.db}")
    private String mongoDb;

    @Bean
    public MongoMappingContext mongoMappingContext() {
        MongoMappingContext context = new MongoMappingContext();
        context.setFieldNamingStrategy(new SnakeCaseFieldNamingStrategy()); // 네이밍 전략 변경
        context.setAutoIndexCreation(true);
        return context;
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(Arrays.asList(
                new LocalDateTimeToDateConverter(),
                new DateToLocalDateTimeConverter()
        ));
    }

    @Bean
    public MongoClient mongoClient(ObservationRegistry registry) {
        ConnectionPoolSettings poolSettings = ConnectionPoolSettings.builder()
                .minSize(20)
                .maxSize(200)
                .maxWaitTime(500, TimeUnit.MILLISECONDS)
                .maxConnectionIdleTime(30, TimeUnit.MINUTES)
                .maxConnectionLifeTime(4, TimeUnit.HOURS)
                .build();

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(mongoUri))
                .writeConcern(WriteConcern.ACKNOWLEDGED)
                .readPreference(ReadPreference.secondaryPreferred())
                .applyToConnectionPoolSettings(builder -> builder.applySettings(poolSettings))
                .contextProvider(ContextProviderFactory.create(registry))
                .addCommandListener(new MongoObservationCommandListener(registry))
                .build();

        return MongoClients.create(settings);
    }

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient mongoClient) {
        return new SimpleMongoClientDatabaseFactory(mongoClient, mongoDb);
    }

    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory chatMongoDbFactory, MongoMappingContext context, MongoCustomConversions conversions) {
        MappingMongoConverter converter = new MappingMongoConverter(chatMongoDbFactory, context);
        converter.setTypeMapper(new DefaultMongoTypeMapper(null)); // _class 필드 제거
        converter.setCustomConversions(conversions); // 커스텀 변환기 추가
        converter.afterPropertiesSet();

        return converter;
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory chatMongoDbFactory, MappingMongoConverter converter) {
        return new MongoTemplate(chatMongoDbFactory, converter);
    }

    @Bean("chatMongoTransactionManager")
    public MongoTransactionManager chatMongoTransactionManager(MongoDatabaseFactory chatMongoDbFactory) {
        TransactionOptions transactionOptions = TransactionOptions.builder()
                .readPreference(ReadPreference.primary())
                .build();

        return new MongoTransactionManager(chatMongoDbFactory, transactionOptions);
    }
}
