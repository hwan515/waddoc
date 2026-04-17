package com.waddoc.domain.robot.config;

import com.waddoc.shared.event.EventTypes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * robot-gateway가 소비/발행하는 로봇 명령 및 텔레메트리 토픽을 구성한다.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public KafkaAdmin.NewTopics kafkaTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(EventTypes.ROBOT_COMMAND_WAYPOINT_REQUESTED_V1).partitions(3).replicas(1).build(),
                TopicBuilder.name(EventTypes.ROBOT_COMMAND_ESTOP_REQUESTED_V1).partitions(3).replicas(1).build(),
                TopicBuilder.name(EventTypes.ROBOT_COMMAND_DISPATCH_REQUESTED_V1).partitions(3).replicas(1).build(),
                TopicBuilder.name(EventTypes.ROBOT_TELEMETRY_V1).partitions(3).replicas(1).build()
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
        return new DefaultErrorHandler(new FixedBackOff(1_000L, 2L));
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
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}
