package com.waddoc.notification;

import com.waddoc.domain.notification.entity.ProcessedEvent;
import com.waddoc.domain.notification.repository.ProcessedEventRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 의사 알림 SSE와 환자 SMS를 전담하는 notification-service의 시작점이다.
 */
@EnableScheduling
@EntityScan(basePackageClasses = ProcessedEvent.class)
@EnableJpaRepositories(basePackageClasses = ProcessedEventRepository.class)
@SpringBootApplication(
        scanBasePackages = "com.waddoc",
        exclude = UserDetailsServiceAutoConfiguration.class
)
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
