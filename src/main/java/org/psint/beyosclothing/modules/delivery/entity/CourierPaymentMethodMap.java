package org.psint.beyosclothing.modules.delivery.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.util.UUID;

@Table(name = "courier_payment_method_map", indexes = {
        @Index(name = "idx_courier_payment_map_uuid", columnList = "uuid", unique = true),
        @Index(name = "idx_courier_payment_map_courier", columnList = "courier_id"),
        @Index(name = "idx_courier_payment_map_payment", columnList = "payment_method_id"),
        @Index(name = "idx_courier_payment_map_composite", columnList = "courier_id, payment_method_id", unique = true)
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourierPaymentMethodMap extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id", nullable = false, foreignKey = @ForeignKey(name = "fk_courier_payment_map_courier"))
    private Courier courier;

    @Column(name = "payment_method_id", nullable = true)
    private Long paymentMethodId;

    @Column(name = "is_fee_free", nullable = false )
    private Boolean isFeeFree = false;

    @PrePersist
    protected void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }
}
