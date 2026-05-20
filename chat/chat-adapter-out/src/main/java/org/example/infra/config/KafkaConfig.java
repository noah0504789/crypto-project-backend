//package org.example.infra.config;
//
//import org.springframework.cloud.stream.binder.BinderFactory;
//import org.springframework.cloud.stream.binder.kafka.KafkaMessageChannelBinder;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.kafka.annotation.EnableKafka;
//import org.springframework.kafka.core.ProducerFactory;
//import org.springframework.kafka.transaction.KafkaTransactionManager;
//import org.springframework.messaging.MessageChannel;
//import org.springframework.transaction.PlatformTransactionManager;
//
//@EnableKafka
//@Configuration
//public class KafkaConfig {
//
//    @Value("${spring.kafka.bootstrap-servers}")
//    private String bootstrapServers;
//
//    @Bean
//    public KafkaAdmin kafkaAdmin() {
//        Map<String, Object> configs = new HashMap<>();
//        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
//
//        return new KafkaAdmin(configs);
//    }
//
//    @Bean
//    public AdminClient adminClient(KafkaAdmin kafkaAdmin) {
//        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
//    }
//
//    @Bean("kafkaTransactionManager")
//    public PlatformTransactionManager kafkaTransactionManager(BinderFactory binders) {
//        KafkaMessageChannelBinder kafka = (KafkaMessageChannelBinder) binders.getBinder(null, MessageChannel.class);
//
//        ProducerFactory<byte[], byte[]> pf = kafka.getTransactionalProducerFactory();
//        KafkaTransactionManager<byte[], byte[]> kafkaTransactionManager = new KafkaTransactionManager<>(pf);
//
//        return kafkaTransactionManager;
//    }
//}
