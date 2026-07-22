package org.psint.beyosclothing.modules.pos.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.psint.beyosclothing.modules.orders.dto.response.OrderPlacementResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POS-specific order response that extends the base OrderPlacementResponse
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PosOrderResponse extends OrderPlacementResponse {

    private String receiptNumber;
    private String terminalCode;
    private String cashierName;

    private String cartUuid;
    private Long cartId;
    private Long cartTerminalId;
    private Long cartCashierId;
    private Long cartCustomerId;
    private String cartCustomerType;
    private Boolean cartIsActive;
    private Boolean cartIsDraft;
    private BigDecimal cartSubtotal;
    private BigDecimal cartTaxAmount;
    private BigDecimal cartTaxPercentage;
    private BigDecimal cartDiscountAmount;
    private BigDecimal cartTotal;
    private LocalDateTime cartCreatedAt;
    private LocalDateTime cartUpdatedAt;
}
