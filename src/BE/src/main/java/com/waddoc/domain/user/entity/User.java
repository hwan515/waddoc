package com.waddoc.domain.user.entity;

import com.waddoc.global.audit.BaseTimeEntity;
import com.waddoc.global.type.ApprovalStatus;
import com.waddoc.global.util.PublicIdGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import com.waddoc.global.util.KstTime;

/**
 * 시스템 사용자(의사, 관리자, 보호자). 환자는 별도 Patient 테이블.
 */
@Entity
@Table(name = "\"user\"")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus;

    @Column(name = "approval_requested_at", nullable = false)
    private LocalDateTime approvalRequestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedByUser;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Builder
    public User(String username, String passwordHash, String name, Role role) {
        LocalDateTime now = KstTime.now();
        this.publicId = PublicIdGenerator.generate("usr_");
        this.username = username;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.approvalRequestedAt = now;
        if (role == Role.ADMIN) {
            this.active = true;
            this.approvalStatus = ApprovalStatus.APPROVED;
            this.approvedAt = now;
        } else {
            this.active = false;
            this.approvalStatus = ApprovalStatus.PENDING;
        }
    }

    public boolean isPendingApproval() {
        return this.approvalStatus == ApprovalStatus.PENDING;
    }

    public boolean isApproved() {
        return this.approvalStatus == ApprovalStatus.APPROVED;
    }

    public void approve(User approver) {
        approve(approver, KstTime.now());
    }

    public void approve(User approver, LocalDateTime now) {
        this.active = true;
        this.approvalStatus = ApprovalStatus.APPROVED;
        this.approvedByUser = approver;
        this.approvedAt = now;
    }

    public void reject(User approver) {
        reject(approver, KstTime.now());
    }

    public void reject(User approver, LocalDateTime now) {
        this.active = false;
        this.approvalStatus = ApprovalStatus.REJECTED;
        this.approvedByUser = approver;
        this.approvedAt = now;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
