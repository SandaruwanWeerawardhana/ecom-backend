package org.psint.beyosclothing.modules.delivery.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.util.UUID;
@Table(name = "courier_api_logs", indexes = {
        @Index(name = "idx_courier_api_log_uuid", columnList = "uuid", unique = true),
        @Index(name = "idx_courier_id", columnList = "courier_id"),
        @Index(name = "idx_http_status", columnList = "http_status")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourierApiLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id", nullable = true, foreignKey = @ForeignKey(name = "fk_courier_api_log_courier"))
    private Courier courier;

    @Column(name = "endpoint", length = 2048)
    private String endpoint;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "http_status")
    private Integer httpStatus;

    @PrePersist
    protected void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }
}

