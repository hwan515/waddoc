package com.waddoc.domain.robot.config;

import com.waddoc.shared.event.RobotTopics;
import java.net.InetAddress;
import javax.net.ssl.SSLContext;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

/**
 * robot-gateway가 MQTT broker에 단일 owner로 연결되도록 inbound/outbound 채널을 구성한다.
 */
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker-url:ws://localhost:9001}")
    private String brokerUrl;

    @Value("${mqtt.client-id:spring-waddoc}")
    private String baseClientId;

    private final String uniqueClientId;

    public MqttConfig() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = Long.toHexString(ProcessHandle.current().pid());
        }
        this.uniqueClientId = hostname;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(60);
        if (brokerUrl.startsWith("wss://")) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(null, null, null);
                options.setSocketFactory(sslContext.getSocketFactory());
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure TLS for MQTT WSS connection", e);
            }
        }
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter(MqttPahoClientFactory factory) {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                baseClientId + "-" + uniqueClientId + "-sub", factory,
                RobotTopics.ROBOT_ODOM,
                RobotTopics.ROBOT_MINIMAP,
                RobotTopics.ROBOT_STATE,
                RobotTopics.ROBOT_STATUS
        );
        adapter.setQos(1);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setOutputChannel(mqttInboundChannel());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutboundHandler(MqttPahoClientFactory factory) {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(baseClientId + "-" + uniqueClientId + "-pub", factory);
        handler.setAsync(true);
        handler.setDefaultQos(1);
        return handler;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }
}
