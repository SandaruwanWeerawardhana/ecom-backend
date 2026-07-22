package org.psint.beyosclothing.modules.pos.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.constants.ResponseCode;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.pos.dto.request.CreatePosCustomerRequest;
import org.psint.beyosclothing.modules.pos.dto.request.UpdatePosCustomerRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosCustomerDetailsResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosCustomerSearchResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosCustomerSimpleResponse;
import org.psint.beyosclothing.modules.pos.service.PosCustomerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pos/customers")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "POS Customers", description = "Customer lookup endpoints for POS")
public class PosCustomerController {

    private final PosCustomerService customerService;

    @GetMapping("/search")
    @Operation(
            summary = "Search customers for POS autocomplete dropdown",
            description = "Fast autocomplete endpoint for customer search. " +
                    "Searches by name, phone, or email with minimum 2 characters. " +
                    "Results ordered by recent activity. Target response time: < 100ms. " +
                    "Optional for walk-in customers."
    )
    public ResponseEntity<APIResponse<List<PosCustomerSearchResponse>>> searchCustomers(
            @RequestParam
            @NotBlank
            @Size(min = 2, message = "query must be at least 2 characters")
            String query,

            @RequestParam(defaultValue = "10")
            int limit
    ) {
        // Validate and clamp limit
        if (limit < 1) limit = 1;
        if (limit > 50) limit = 50; // Maximum safety limit

        log.debug("POS customer search: query='{}', limit={}", query, limit);

        List<PosCustomerSearchResponse> results = customerService.searchCustomers(query, limit);

        return ResponseEntity.ok(APIResponse.<List<PosCustomerSearchResponse>>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Customers retrieved")
                .data(results)
                .build());
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get customer details by id for POS")
    public ResponseEntity<APIResponse<PosCustomerDetailsResponse>> getCustomer(@PathVariable Long customerId) {
        PosCustomerDetailsResponse resp = customerService.getCustomerDetails(customerId);
        return ResponseEntity.ok(APIResponse.<PosCustomerDetailsResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Customer retrieved")
                .data(resp)
                .build());
    }

    @PostMapping
    @Operation(summary = "Create POS customer")
    public ResponseEntity<APIResponse<PosCustomerDetailsResponse>> createCustomer(
            @org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid CreatePosCustomerRequest request
    ) {
        log.debug("createCustomer called: {}", request);
        PosCustomerDetailsResponse resp = customerService.createPosCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(APIResponse.<PosCustomerDetailsResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Customer created")
                .data(resp)
                .build());
    }

    @PutMapping("/{customerId}")
    @Operation(summary = "Update POS customer by ID")
    public ResponseEntity<APIResponse<PosCustomerDetailsResponse>> updateCustomer(
            @PathVariable Long customerId,
            @RequestBody @jakarta.validation.Valid UpdatePosCustomerRequest request
    ) {
        log.debug("updateCustomer called: id={}, request={}", customerId, request);
        PosCustomerDetailsResponse resp = customerService.updatePosCustomer(customerId, request);
        return ResponseEntity.ok(APIResponse.<PosCustomerDetailsResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Customer updated")
                .data(resp)
                .build());
    }

    @GetMapping
    @Operation(summary = "Get all POS customers (simple)")
    public ResponseEntity<APIResponse<List<PosCustomerSimpleResponse>>> getAllSimpleCustomers() {
        List<PosCustomerSimpleResponse> list = customerService.getAllSimpleCustomers();
        return ResponseEntity.ok(APIResponse.<List<PosCustomerSimpleResponse>>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Simple customers retrieved")
                .data(list)
                .build());
    }

}
