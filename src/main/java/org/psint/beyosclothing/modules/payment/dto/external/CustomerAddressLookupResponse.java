package org.psint.beyosclothing.modules.payment.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Response DTO for customer delivery address lookup
 * Received from Customer module via RabbitMQ
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAddressLookupResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Boolean found;
    private Long addressId; // Address ID from Customer module
    private Long customerId;
    private String customerUuid;
    private String fullName;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String province;
    private String postalCode;
    private String phoneNumber;
    private String country;
    private String email;
    private String customerType; // "CUSTOMER" or "RESELLER"
}
