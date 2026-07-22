package org.psint.beyosclothing.modules.customers.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.constants.ResponseCode;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.customers.dto.CreateAddressRequest;
import org.psint.beyosclothing.modules.customers.dto.CustomerResponse;
import org.psint.beyosclothing.modules.customers.dto.request.CustomerUpdatePassword;
import org.psint.beyosclothing.modules.customers.dto.request.UpdateCustomerRequestDTO;
import org.psint.beyosclothing.modules.customers.dto.response.UpdateCustomerResponse;
import org.psint.beyosclothing.modules.customers.service.CustomerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Customer Controller
 * Manages customer profiles and addresses
 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER', 'ADMIN')")
    public ResponseEntity<APIResponse<CustomerResponse>> getCustomerProfile(
            @RequestParam String customerUuid) {
        log.info("GET /api/v1/customers/profile - customerUuid: {}", customerUuid);

        CustomerResponse customer = customerService.getCustomerByUuid(customerUuid);

        return ResponseEntity.ok(APIResponse.<CustomerResponse>builder()
                .success(true)
                .message("Customer profile retrieved successfully")
                .data(customer)
                .build());
    }

    @GetMapping
    public ResponseEntity<APIResponse<Page<CustomerResponse>>> getAllCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dateCreated") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(sortDirection.toUpperCase());
        } catch (IllegalArgumentException ex) {
            direction = Sort.Direction.DESC;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        log.info("GET /api/v1/customers - page: {} size: {} sortBy: {} sortDirection: {}", page, size, sortBy, sortDirection);

        Page<CustomerResponse> resultPage = customerService.getAllCustomers(pageable);

        return ResponseEntity.ok(APIResponse.<Page<CustomerResponse>>builder()
                .success(true)
                .message("Customers retrieved successfully")
                .data(resultPage)
                .build());
    }

    @GetMapping("/addresses")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER')")
    public ResponseEntity<APIResponse<List<CustomerResponse.AddressDTO>>> getCustomerAddresses(
            @RequestParam String customerUuid) {
        log.info("GET /api/v1/customers/addresses - customerUuid: {}", customerUuid);

        CustomerResponse customer = customerService.getCustomerByUuid(customerUuid);
        List<CustomerResponse.AddressDTO> addresses = customer.getAddresses();

        return ResponseEntity.ok(APIResponse.<List<CustomerResponse.AddressDTO>>builder()
                .success(true)
                .message("Customer addresses retrieved successfully")
                .data(addresses)
                .build());
    }

    @PostMapping("/addresses")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER')")
    public ResponseEntity<APIResponse<CustomerResponse>> addAddress(
            @RequestParam String customerUuid,
            @Valid @RequestBody CreateAddressRequest request) {
        log.info("POST /api/v1/customers/addresses - customerUuid: {}", customerUuid);

        CustomerResponse customer = customerService.addAddress(customerUuid, request);

        return ResponseEntity.ok(APIResponse.<CustomerResponse>builder()
                .success(true)
                .message("Address added successfully")
                .data(customer)
                .build());
    }

    @PutMapping("/addresses/{addressId}/set-default")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER')")
    public ResponseEntity<APIResponse<Void>> setDefaultAddress(
            @RequestParam String customerUuid,
            @PathVariable Long addressId) {
        log.info("PUT /api/v1/customers/addresses/{}/set-default - customerUuid: {}", addressId, customerUuid);

        customerService.setDefaultAddress(customerUuid, addressId);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Default address updated successfully")
                .build());
    }

    @DeleteMapping("/addresses/{addressId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER')")
    public ResponseEntity<APIResponse<Void>> deleteAddress(
            @RequestParam String customerUuid,
            @PathVariable Long addressId) {
        log.info("DELETE /api/v1/customers/addresses/{} - customerUuid: {}", addressId, customerUuid);

        customerService.deleteAddress(customerUuid, addressId);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Address deleted successfully")
                .build());
    }

    /**
     * Update customer details
     * Mirrors admin update pattern: PUT /api/v1/admin/{id}
     */
    @PutMapping("/{customerUuid}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER', 'ADMIN')")
    public ResponseEntity<APIResponse<UpdateCustomerResponse>> updateCustomer(
            @PathVariable String customerUuid,
            @Valid @RequestBody UpdateCustomerRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Customer update request for UUID: {} from: {}", customerUuid, userDetails.getUsername());

        try {
            UpdateCustomerResponse response = customerService.updateCustomer(customerUuid, request, userDetails.getUsername());

            return ResponseEntity
                    .ok(APIResponse.<UpdateCustomerResponse>builder()
                            .responseCode(ResponseCode.UPDATED.getCode())
                            .success(true)
                            .message("Customer updated successfully")
                            .data(response)
                            .build());

        } catch (ResourceNotFoundException e) {
            log.warn("Customer not found: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(APIResponse.<UpdateCustomerResponse>builder()
                            .responseCode(ResponseCode.NOT_FOUND.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Error updating customer: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<UpdateCustomerResponse>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to update customer")
                            .build());
        }
    }

    /**
     * Update customer password
     * Mirrors admin password update pattern: PUT /api/v1/admin/update-password/{id}
     */
    @PutMapping("/update-password/{customerUuid}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESELLER', 'ADMIN')")
    public ResponseEntity<APIResponse<UpdateCustomerResponse>> updateCustomerPassword(
            @PathVariable String customerUuid,
            @Valid @RequestBody CustomerUpdatePassword request) {

        log.info("Customer password update request for UUID: {}", customerUuid);

        try {
            UpdateCustomerResponse response = customerService.updateCustomerPassword(customerUuid, request);

            return ResponseEntity
                    .ok(APIResponse.<UpdateCustomerResponse>builder()
                            .responseCode(ResponseCode.UPDATED.getCode())
                            .success(true)
                            .message("Customer password updated successfully")
                            .data(response)
                            .build());

        } catch (BadRequestException e) {
            log.warn("Bad request while updating customer password: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(APIResponse.<UpdateCustomerResponse>builder()
                            .responseCode(ResponseCode.BAD_REQUEST.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (ResourceNotFoundException e) {
            log.warn("Customer not found: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(APIResponse.<UpdateCustomerResponse>builder()
                            .responseCode(ResponseCode.NOT_FOUND.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Error updating customer password: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<UpdateCustomerResponse>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to update customer password")
                            .build());
        }
    }
}
