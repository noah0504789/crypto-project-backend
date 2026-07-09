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
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.observability.ContextProviderFactory;
import org.springframework.data.mongodb.observability.MongoObservationCommandListener;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@EnableMongoRepositories(
        basePackages = {"org.example"},
        mongoTemplateRef = "primaryMongoTemplate"
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
        context.setFieldNamingStrategy(new SnakeCaseFieldNamingStrategy());
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
                .readPreference(ReadPreference.primary())
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
    public MappingMongoConverter mappingMongoConverter(
            MongoDatabaseFactory mongoDatabaseFactory,
            MongoMappingContext context,
            MongoCustomConversions conversions
    ) {
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(mongoDatabaseFactory);

        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, context);
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();

        return converter;
    }

    @Primary
    @Bean("primaryMongoTemplate")
    public MongoTemplate primaryMongoTemplate(MongoDatabaseFactory mongoDatabaseFactory, MappingMongoConverter converter) {
        MongoTemplate template = new MongoTemplate(mongoDatabaseFactory, converter);
        template.setReadPreference(ReadPreference.primary());
        return template;
    }

    @Bean("secondaryMongoTemplate")
    public MongoTemplate secondaryMongoTemplate(MongoDatabaseFactory mongoDatabaseFactory, MappingMongoConverter converter) {
        MongoTemplate template = new MongoTemplate(mongoDatabaseFactory, converter);
        template.setReadPreference(ReadPreference.secondaryPreferred());
        return template;
    }

    @Bean("chatMongoTransactionManager")
    public MongoTransactionManager chatMongoTransactionManager(MongoDatabaseFactory mongoDatabaseFactory) {
        TransactionOptions transactionOptions = TransactionOptions.builder()
                .readPreference(ReadPreference.primary())
                .writeConcern(WriteConcern.ACKNOWLEDGED)
                .build();

        return new MongoTransactionManager(mongoDatabaseFactory, transactionOptions);
    }
}