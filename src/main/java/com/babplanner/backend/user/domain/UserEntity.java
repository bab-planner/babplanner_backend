package com.babplanner.backend.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 서비스 사용자.
 *
 * <p>비밀번호는 BCrypt 해시만 보관하며 원문은 어디에도 저장하지 않는다.
 * 이 엔티티를 API 응답으로 직접 내보내지 않고 UserResponse로 변환한다.
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "student_id", nullable = false, unique = true, length = 20)
    private String studentId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
        // JPA 전용
    }

    public UserEntity(
            String name,
            String phone,
            String studentId,
            Gender gender,
            Role role,
            String passwordHash,
            Instant now) {
        this.name = name;
        this.phone = phone;
        this.studentId = studentId;
        this.gender = gender;
        this.role = role;
        this.passwordHash = passwordHash;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 이름과 성별만 수정한다.
     *
     * <p>휴대폰 번호, 학번, 권한, 비밀번호는 이 경로로 바꿀 수 없다.
     * name이 null이면 기존 값을 유지하고, gender는 null 지정으로 해제할 수 있다.
     */
    public void updateProfile(String name, Gender gender, Instant now) {
        if (name != null) {
            this.name = name;
        }
        this.gender = gender;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getStudentId() {
        return studentId;
    }

    public Gender getGender() {
        return gender;
    }

    public Role getRole() {
        return role;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
