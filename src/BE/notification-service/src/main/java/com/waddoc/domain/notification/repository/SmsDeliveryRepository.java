package com.waddoc.domain.notification.repository;

import com.waddoc.domain.notification.entity.SmsDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SmsDeliveryRepository extends JpaRepository<SmsDelivery, Long> {
}
