package org.psint.beyosclothing.modules.customers.service;

import org.psint.beyosclothing.modules.customers.dto.CreateAddressRequest;
import org.psint.beyosclothing.modules.customers.dto.CustomerResponse;
import org.psint.beyosclothing.modules.customers.dto.request.CustomerUpdatePassword;
import org.psint.beyosclothing.modules.customers.dto.request.UpdateCustomerRequestDTO;
import org.psint.beyosclothing.modules.customers.dto.response.UpdateCustomerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Customer Service Interface
 */
public interface CustomerService {

    CustomerResponse getCustomerByUuid(String customerUuid);

    /**
     * Get customer ID by email (from authentication)
     * This method handles cross-database lookup internally
     */
    Long getCustomerIdByEmail(String email);

    CustomerResponse addAddress(String customerUuid, CreateAddressRequest request);

    void setDefaultAddress(String customerUuid, Long addressId);

    void deleteAddress(String customerUuid, Long addressId);

    Page<CustomerResponse> getAllCustomers(Pageable pageable);

    UpdateCustomerResponse updateCustomer(String customerUuid, UpdateCustomerRequestDTO request, String updatedBy);

    UpdateCustomerResponse updateCustomerPassword(String customerUuid, CustomerUpdatePassword request);
}
