package org.psint.beyosclothing.modules.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pos_customers", indexes = {
        @Index(name = "idx_pos_customers_is_active", columnList = "is_active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosCustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid", length = 36, unique = true, nullable = false)
    private String uuid;

    @Column(name = "full_name", length = 200, nullable = false)
    private String fullName;

    @Column(name = "phone", length = 30, nullable = false)
    private String phone;

    @Column(name = "address_line", length = 255, nullable = false)
    private String address;

    @Column(name = "city", length = 100, nullable = false)
    private String city;

    @Column(name = "province", length = 100, nullable = false)
    private String province;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "postal_code", length = 20)
    private String zipCode;

    @Column(name = "date_created")
    private LocalDateTime dateCreated;

    @Column(name = "date_updated")
    private LocalDateTime dateUpdated;

    @Column(name = "is_active")
    private Boolean isActive;
}
