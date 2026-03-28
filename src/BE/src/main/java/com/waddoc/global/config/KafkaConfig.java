package com.waddoc.global.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
public class KafkaConfig {

    private static final long RETRY_INTERVAL_MS = 1_000L;
    private static final long RETRY_ATTEMPTS = 2L;

    @Bean
    public KafkaAdmin.NewTopics kafkaTopics() {
        // 개발 환경에서도 필요한 토픽을 애플리케이션 기동 시점에 자동으로 보장한다.
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(KafkaTopics.DISPATCH_REQUESTS_TOPIC).partitions(3).replicas(1).build(),
                TopicBuilder.name(KafkaTopics.DISPATCH_RETRY_TOPIC).partitions(3).replicas(1).build(),
                TopicBuilder.name(KafkaTopics.SMS_REQUESTS_TOPIC).partitions(3).replicas(1).build(),
                TopicBuilder.name(KafkaTopics.SMS_REQUESTS_DLT_TOPIC).partitions(1).replicas(1).build(),
                TopicBuilder.name(KafkaTopics.DOCTOR_NOTIFICATIONS_TOPIC).partitions(3).replicas(1).build(),
                TopicBuilder.name(KafkaTopics.MISSION_TELEMETRY_TOPIC).partitions(3).replicas(1).build()
        );
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaConsumerFactory<>(kafkaProperties.buildConsumerProperties());
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(RETRY_INTERVAL_MS, RETRY_ATTEMPTS));
    }

    @Bean
    public DefaultErrorHandler smsKafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // SMS는 외부 부작용이라 재시도 후에도 실패하면 DLT에 남겨 별도로 추적한다.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(KafkaTopics.SMS_REQUESTS_DLT_TOPIC, 0)
        );
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(RETRY_INTERVAL_MS, RETRY_ATTEMPTS)
        );
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler,
            @Value("${spring.kafka.listener.auto-startup:true}") boolean autoStartup
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        // 레코드 단위 ack로 처리해 실패한 메시지만 다시 소비할 수 있게 한다.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setAutoStartup(autoStartup);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> smsKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler smsKafkaErrorHandler,
            @Value("${spring.kafka.listener.auto-startup:true}") boolean autoStartup
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(smsKafkaErrorHandler);
        // SMS도 같은 단위로 ack하되, 실패 시에는 DLT 경로를 탄다.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}
