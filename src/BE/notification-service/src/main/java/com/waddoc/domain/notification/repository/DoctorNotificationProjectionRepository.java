package com.waddoc.domain.notification.repository;

import com.waddoc.domain.notification.entity.DoctorNotificationProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DoctorNotificationProjectionRepository extends JpaRepository<DoctorNotificationProjection, Long> {
}
