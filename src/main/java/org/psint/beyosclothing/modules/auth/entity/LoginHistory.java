package org.psint.beyosclothing.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Login History Entity
 * Table: login_history in beyos_auth_db
 */
@Entity
@Table(name = "login_history", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_login_id", columnList = "login_id"),
    @Index(name = "idx_login_time", columnList = "login_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "location_city", length = 100)
    private String locationCity;

    @Column(name = "location_country", length = 100)
    private String locationCountry;

    @Column(name = "login_time", nullable = false)
    private LocalDateTime loginTime;

    @Column(name = "is_login_success", nullable = false)
    private Boolean isLoginSuccess;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @PrePersist
    public void generateLoginId() {
        if (loginId == null) {
            loginId = UUID.randomUUID().toString();
        }
        if (loginTime == null) {
            loginTime = LocalDateTime.now();
        }
    }
}

