package com.waddoc.domain.doctor.repository;

import com.waddoc.domain.doctor.entity.DoctorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, Long> {

    Optional<DoctorProfile> findByPublicId(String publicId);

    List<DoctorProfile> findByDepartment(String department);
}
