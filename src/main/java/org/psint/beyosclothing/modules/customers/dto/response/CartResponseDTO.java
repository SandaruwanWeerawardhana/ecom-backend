package org.psint.beyosclothing.modules.customers.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.psint.beyosclothing.modules.cart.dto.response.CartItemResponseDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartResponseDTO {

    private Long id;
    private String uuid;
    private Long customerId;
    private String guestId;
    private List<CartItemResponseDTO> items;
    private Integer itemCount;
    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal total;
    private String promoCode;
    private BigDecimal promoDiscount;
    private Boolean hasPromo;
    private LocalDateTime expiresAt;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;
}
