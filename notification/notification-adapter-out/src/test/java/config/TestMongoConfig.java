package config;

import org.example.notification.adapter.out.persistence.MongoNotificationRecipientRepository;
import org.example.notification.adapter.out.persistence.MongoNotificationRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@TestConfiguration
@EnableMongoRepositories(basePackageClasses = {MongoNotificationRepository.class, MongoNotificationRecipientRepository.class})
public class TestMongoConfig {

}