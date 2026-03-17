package com.waddoc.domain.patient.repository;

import com.waddoc.domain.patient.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByPublicId(String publicId);

    Optional<Patient> findByPhone(String phone);

    List<Patient> findAllByNameAndBirthDate6(String name, String birthDate6);

    @Query(
            value = """
                    select p
                    from Patient p
                    where (:name is null or lower(p.name) like lower(concat('%', :name, '%')))
                      and (:phone is null or p.phone like concat('%', :phone, '%'))
                    order by p.createdAt desc
                    """,
            countQuery = """
                    select count(p)
                    from Patient p
                    where (:name is null or lower(p.name) like lower(concat('%', :name, '%')))
                      and (:phone is null or p.phone like concat('%', :phone, '%'))
                    """
    )
    Page<Patient> searchAdminPatients(
            @Param("name") String name,
            @Param("phone") String phone,
            Pageable pageable
    );
}
