package config;

import com.mongodb.ReadPreference;
import java.time.Instant;
import java.time.LocalDateTime;
import org.example.chat.chatmessage.adapter.out.persistence.MongoChatMessageRepository;
import org.example.chat.chatroom.adapter.out.persistence.MongoChatRoomAdapter;
import org.example.chat.chatroom.adapter.out.persistence.MongoChatRoomMembershipRepository;
import org.example.chat.chatroom.adapter.out.persistence.MongoChatRoomRepository;
import org.example.common.time.Clock;
import org.example.common.time.ServiceTimeConverter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@TestConfiguration
@EnableMongoRepositories(
        basePackageClasses = {
            MongoChatRoomRepository.class,
            MongoChatRoomMembershipRepository.class,
            MongoChatMessageRepository.class
        },
        mongoTemplateRef = "primaryMongoTemplate")
public class TestMongoConfig {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 52);

    @Bean
    public Clock clock() {
        return new Clock() {
            @Override
            public long nowMs() {
                return now().toEpochMilli();
            }

            @Override
            public long monotonicTimeNanos() {
                return 0L;
            }

            @Override
            public Instant now() {
                return ServiceTimeConverter.toInstant(NOW);
            }

            @Override
            public LocalDateTime nowLocalDateTime() {
                return NOW;
            }
        };
    }

    @Primary
    @Bean("primaryMongoTemplate")
    public MongoTemplate primaryMongoTemplate(
            MongoDatabaseFactory mongoDatabaseFactory, MappingMongoConverter converter) {
        MongoTemplate template = new MongoTemplate(mongoDatabaseFactory, converter);
        template.setReadPreference(ReadPreference.primary());
        return template;
    }

    @Bean("secondaryMongoTemplate")
    public MongoTemplate secondaryMongoTemplate(
            MongoDatabaseFactory mongoDatabaseFactory, MappingMongoConverter converter) {
        MongoTemplate template = new MongoTemplate(mongoDatabaseFactory, converter);
        template.setReadPreference(ReadPreference.secondaryPreferred());
        return template;
    }

    @Bean
    public MongoChatRoomAdapter mongoChatRoomAdapter(
            MongoChatRoomRepository chatRoomRepository,
            MongoChatRoomMembershipRepository membershipRepository,
            MongoChatMessageRepository chatMessageRepository) {
        return new MongoChatRoomAdapter(
                chatRoomRepository, membershipRepository, chatMessageRepository);
    }
}
