package config;

import com.mongodb.ReadPreference;
import org.example.chat.chatmessage.adapter.out.persistence.MongoChatMessageRepository;
import org.example.chat.chatroom.adapter.out.persistence.MongoChatRoomAdapter;
import org.example.chat.chatroom.adapter.out.persistence.MongoChatRoomMembershipRepository;
import org.example.chat.chatroom.adapter.out.persistence.MongoChatRoomRepository;
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
        mongoTemplateRef = "primaryMongoTemplate"
)
public class TestMongoConfig {

    @Primary
    @Bean("primaryMongoTemplate")
    public MongoTemplate primaryMongoTemplate(
            MongoDatabaseFactory mongoDatabaseFactory,
            MappingMongoConverter converter
    ) {
        MongoTemplate template = new MongoTemplate(mongoDatabaseFactory, converter);
        template.setReadPreference(ReadPreference.primary());
        return template;
    }

    @Bean("secondaryMongoTemplate")
    public MongoTemplate secondaryMongoTemplate(
            MongoDatabaseFactory mongoDatabaseFactory,
            MappingMongoConverter converter
    ) {
        MongoTemplate template = new MongoTemplate(mongoDatabaseFactory, converter);
        template.setReadPreference(ReadPreference.secondaryPreferred());
        return template;
    }

    @Bean
    public MongoChatRoomAdapter mongoChatRoomAdapter(
            MongoChatRoomRepository chatRoomRepository,
            MongoChatRoomMembershipRepository membershipRepository,
            MongoChatMessageRepository chatMessageRepository
    ) {
        return new MongoChatRoomAdapter(
                chatRoomRepository,
                membershipRepository,
                chatMessageRepository
        );
    }
}