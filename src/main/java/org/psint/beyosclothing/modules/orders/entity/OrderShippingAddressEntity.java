package org.psint.beyosclothing.modules.orders.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Order Shipping Address Entity
 * Database: beyos_order
 * Table: order_shipping_address
 */
@Entity
@Table(name = "order_shipping_address", indexes = {
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_city", columnList = "city")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderShippingAddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone", nullable = false, length = 50)
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "address_line1", nullable = false, length = 500)
    private String addressLine1;

    @Column(name = "address_line2", length = 500)
    private String addressLine2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "city_id")
    private Long cityId;

    @Column(name = "district_id")
    private Long districtId;

    @Column(name = "province", length = 100)
    private String province;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", length = 100)
    private String country = "Sri Lanka";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
