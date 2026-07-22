package org.psint.beyosclothing.modules.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pos_product_cache", indexes = {
        @Index(name = "idx_pos_product_uuid", columnList = "uuid"),
        @Index(name = "idx_pos_product_product_id", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosProductCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "uuid", length = 36, nullable = false)
    private String uuid;

    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal showcasePrice;

    @Column(name = "sale_price", precision = 10, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "stock_available")
    private Integer stockAvailable;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "has_variants")
    private Boolean hasVariants = false;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_at")
    private LocalDateTime dateCreated;

    @Column(name = "updated_at")
    private LocalDateTime dateUpdated;
}
