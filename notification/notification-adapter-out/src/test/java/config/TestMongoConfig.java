package config;

import com.mongodb.ReadPreference;
import org.example.notification.adapter.out.persistence.MongoNotificationRecipientRepository;
import org.example.notification.adapter.out.persistence.MongoNotificationRecipientRepositoryImpl;
import org.example.notification.adapter.out.persistence.MongoNotificationRepository;
import org.example.notification.infra.properties.NotificationPersistenceProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@TestConfiguration
@EnableMongoRepositories(
        basePackageClasses = {MongoNotificationRepository.class},
        mongoTemplateRef = "primaryMongoTemplate"
)
public class TestMongoConfig {

    @Primary
    @Bean("primaryMongoTemplate")
    public MongoTemplate primaryMongoTemplate(MongoDatabaseFactory mongoDatabaseFactory) {
        MongoTemplate template = new MongoTemplate(mongoDatabaseFactory);
        template.setReadPreference(ReadPreference.primary());
        return template;
    }

    @Bean("secondaryMongoTemplate")
    public MongoTemplate secondaryMongoTemplate(MongoDatabaseFactory mongoDatabaseFactory) {
        MongoTemplate template = new MongoTemplate(mongoDatabaseFactory);
        template.setReadPreference(ReadPreference.secondaryPreferred());
        return template;
    }

    @Bean
    public MongoNotificationRecipientRepository mongoNotificationRecipientRepository(
            @Qualifier("primaryMongoTemplate") MongoTemplate primaryMongoTemplate,
            @Qualifier("secondaryMongoTemplate") MongoTemplate secondaryMongoTemplate
    ) {
        return new MongoNotificationRecipientRepositoryImpl(
                primaryMongoTemplate, secondaryMongoTemplate, new NotificationPersistenceProperties(1000));
    }
}