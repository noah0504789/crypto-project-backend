package config;

import org.example.chat.chatmessage.adapter.out.persistence.MongoChatMessageRepository;
import org.example.chat.chatroom.adapter.out.persistence.MongoChatRoomAdapter;
import org.example.chat.chatroom.adapter.out.persistence.MongoChatRoomMembershipRepository;
import org.example.chat.chatroom.adapter.out.persistence.MongoChatRoomRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@TestConfiguration
@EnableMongoRepositories(basePackageClasses = {MongoChatRoomRepository.class, MongoChatRoomMembershipRepository.class, MongoChatMessageRepository.class})
public class TestMongoConfig {

    @Bean
    public MongoChatRoomAdapter mongoChatRoomAdapter(
            MongoChatRoomRepository chatRoomRepository,
            MongoChatRoomMembershipRepository membershipRepository,
            MongoChatMessageRepository chatMessageRepository) {
        return new MongoChatRoomAdapter(chatRoomRepository, membershipRepository, chatMessageRepository);
    }
}