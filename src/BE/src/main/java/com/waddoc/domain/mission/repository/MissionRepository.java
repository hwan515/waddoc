package com.waddoc.domain.mission.repository;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.mission.entity.Mission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MissionRepository extends JpaRepository<Mission, Long> {

    Optional<Mission> findByPublicId(String publicId);

    Optional<Mission> findByCareCase(CareCase careCase);

    List<Mission> findAllByCareCaseIn(List<CareCase> careCases);
}
