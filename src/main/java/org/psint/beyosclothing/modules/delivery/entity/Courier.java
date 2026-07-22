package org.psint.beyosclothing.modules.delivery.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Table(name = "couriers", indexes = {
        @Index(name = "idx_code", columnList = "code", unique = true),
        @Index(name = "idx_uuid", columnList = "uuid", unique = true),
        @Index(name = "idx_name", columnList = "name")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Courier extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "api_base_url")
    private String apiBaseUrl;

    @Column(name = "api_key")
    private String apiKey;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "email")
    private String email;

    @OneToMany(mappedBy = "courier", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<CourierRate> courierRates = new HashSet<>();

    @PrePersist
    protected void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }
}
