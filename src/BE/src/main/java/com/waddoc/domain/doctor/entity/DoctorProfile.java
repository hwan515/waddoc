package com.waddoc.domain.doctor.entity;

import com.waddoc.domain.user.entity.User;
import com.waddoc.global.audit.BaseCreatedEntity;
import com.waddoc.global.util.PublicIdGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 의사 프로필. User와 1:1. 진료과 코드/한글명 보유.
 */
@Entity
@Table(name = "doctor_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DoctorProfile extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "doctor_profile_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 50)
    private String department;

    @Column(name = "department_name", nullable = false, length = 50)
    private String departmentName;

    @Builder
    public DoctorProfile(User user, String department, String departmentName) {
        this.publicId = PublicIdGenerator.generate("doc_");
        this.user = user;
        this.department = department;
        this.departmentName = departmentName;
    }
}
