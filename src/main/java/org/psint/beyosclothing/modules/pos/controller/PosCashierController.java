package org.psint.beyosclothing.modules.pos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.constants.ResponseCode;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.pos.dto.request.CreateCashierRequest;
import org.psint.beyosclothing.modules.pos.dto.request.UpdateCashierRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosCashierResponse;
import org.psint.beyosclothing.modules.pos.entity.PosCashierEntity;
import org.psint.beyosclothing.modules.pos.service.PosCashierService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/v1/pos/cashiers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "POS Cashiers", description = "POS cashier management (admin)")
public class PosCashierController {

    private final PosCashierService pastcashierService;

    @Operation(summary = "Create POS cashier")
    @ApiResponse(responseCode = "201", description = "Cashier created")
    @PostMapping
//    @PreAuthorize("hasAuthority('MANAGE_POS')")
    public ResponseEntity<APIResponse<PosCashierResponse>> createCashier(@Valid @RequestBody CreateCashierRequest request) {
        log.info("Creating cashier: {}", request.getName());
        PosCashierResponse resp = mapToResponse(
                pastcashierService.createCashier(request.getName(), request.getUserId(), request.getPinCode())
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<PosCashierResponse>builder()
                        .responseCode(ResponseCode.CREATED.getCode())
                        .success(true)
                        .message("Cashier created")
                        .data(resp)
                        .build());
    }

    @Operation(summary = "List POS cashiers (paginated)")
    @ApiResponse(responseCode = "200", description = "Cashiers retrieved")
    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_POS')")
    public ResponseEntity<APIResponse<PageResponse<PosCashierResponse>>> listCashiers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<PosCashierEntity> pageData = pastcashierService.findAll(pageable);

        List<PosCashierResponse> resp = pageData.getContent()
                .stream()
                .map(this::mapToResponse)
                .toList();

        PageResponse<PosCashierResponse> pageResponse = PageResponse.<PosCashierResponse>builder()
                .content(resp)
                .pageNumber(pageData.getNumber())
                .pageSize(pageData.getSize())
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .last(pageData.isLast())
                .first(pageData.isFirst())
                .empty(pageData.isEmpty())
                .build();

        return ResponseEntity.ok(APIResponse.<PageResponse<PosCashierResponse>>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Cashiers retrieved")
                .data(pageResponse)
                .build());
    }

    @Operation(summary = "Get POS cashier by UUID")
    @ApiResponse(responseCode = "200", description = "Cashier retrieved")
    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('VIEW_POS')")
    public ResponseEntity<APIResponse<PosCashierResponse>> getCashier(@PathVariable String uuid) {
        return pastcashierService.findByUuid(uuid)
                .map(entity -> ResponseEntity.ok(APIResponse.<PosCashierResponse>builder()
                        .responseCode(ResponseCode.SUCCESS.getCode())
                        .success(true)
                        .message("Cashier retrieved")
                        .data(mapToResponse(entity))
                        .build()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(APIResponse.<PosCashierResponse>builder()
                                .responseCode(ResponseCode.NOT_FOUND.getCode())
                                .success(false)
                                .message("Cashier not found")
                                .build()));
    }

    @Operation(summary = "Update POS cashier")
    @ApiResponse(responseCode = "200", description = "Cashier updated")
    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('MANAGE_POS')")
    public ResponseEntity<APIResponse<PosCashierResponse>> updateCashier(@PathVariable String uuid, @Valid @RequestBody UpdateCashierRequest request) {
        PosCashierResponse response = mapToResponse(
                pastcashierService.updateCashier(
                        uuid,
                        request.getName(),
                        request.getUserId(),
                        request.getPinCode(),
                        request.getIsActive()
                )
        );

        return ResponseEntity.ok(APIResponse.<PosCashierResponse>builder()
                .responseCode(ResponseCode.UPDATED.getCode())
                .success(true)
                .message("Cashier updated")
                .data(response)
                .build());
    }

    @Operation(summary = "Deactivate POS cashier (soft delete)")
    @ApiResponse(responseCode = "204", description = "Cashier deactivated")
    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('MANAGE_POS')")
    public ResponseEntity<APIResponse<Void>> deactivateCashier(@PathVariable String uuid) {
        pastcashierService.deactivateCashier(uuid);
        return ResponseEntity.ok(APIResponse.<Void>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Cashier deactivated")
                .build());
    }

    @Operation(summary = "Set or update cashier PIN")
    @ApiResponse(responseCode = "200", description = "PIN updated")
    @PutMapping("/{uuid}/pin")
    @PreAuthorize("hasAuthority('MANAGE_POS')")
    public ResponseEntity<APIResponse<Void>> updatePin(@PathVariable String uuid, @RequestBody @Valid UpdateCashierRequest request) {
        if (request.getPinCode() == null || request.getPinCode().isBlank()) {
            return ResponseEntity.badRequest().body(APIResponse.<Void>builder()
                    .responseCode(ResponseCode.BAD_REQUEST.getCode())
                    .success(false)
                    .message("pinCode is required")
                    .build());
        }

        pastcashierService.updateCashierPin(uuid, request.getPinCode());
        return ResponseEntity.ok(APIResponse.<Void>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("PIN updated")
                .build());
    }

    private PosCashierResponse mapToResponse(PosCashierEntity entity) {
        return PosCashierResponse.builder()
                .uuid(entity.getUuid())
                .userId(entity.getUserId())
                .name(entity.getName())
                .pinCode(entity.getPinCode())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
