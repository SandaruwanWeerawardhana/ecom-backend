package org.psint.beyosclothing.modules.products.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.constants.AppConstants;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.products.dto.request.AddAttributeValuesRequest;
import org.psint.beyosclothing.modules.products.dto.request.CreateAttributeWithValuesRequest;
import org.psint.beyosclothing.modules.products.dto.request.UpdateAttributeRequest;
import org.psint.beyosclothing.modules.products.dto.response.AttributeResponse;
import org.psint.beyosclothing.modules.products.service.ProductAttributeService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Product Attribute Controller
 * Manages product attributes and their values
 * All write operations require ADMIN user type with specific permissions
 */
@RestController
@RequestMapping(AppConstants.API_VERSION + "/products/attributes")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Product Attributes", description = "APIs for managing product attributes and their values (e.g., Size, Color)")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductAttributeController {

    private final ProductAttributeService attributeService;

    /**
     * Create a new product attribute with its values
     * Required: ADMIN user type with CREATE_PRODUCT permission
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('CREATE_PRODUCT')")
    @Operation(
            summary = "Create attribute with values",
            description = "Create a new product attribute along with its values in a single transaction. Requires ADMIN user type with CREATE_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<AttributeResponse>> createAttributeWithValues(
            @Valid @RequestBody CreateAttributeWithValuesRequest request) {
        log.info("POST /api/v1/products/attributes - Creating attribute: {}", request.getAttributeName());

        AttributeResponse response = attributeService.createAttributeWithValues(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<AttributeResponse>builder()
                        .success(true)
                        .message("Attribute created successfully with values")
                        .data(response)
                        .build());
    }

    /**
     * Add new values to an existing attribute
     * Required: ADMIN user type with EDIT_PRODUCT permission
     */
    @PostMapping("/{attributeUuid}/values")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('EDIT_PRODUCT')")
    @Operation(
            summary = "Add values to attribute",
            description = "Add new values to an existing product attribute. Requires ADMIN user type with EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<AttributeResponse>> addValuesToAttribute(
            @PathVariable String attributeUuid,
            @Valid @RequestBody AddAttributeValuesRequest request) {
        log.info("POST /api/v1/products/attributes/{}/values - Adding values", attributeUuid);

        AttributeResponse response = attributeService.addValuesToAttribute(attributeUuid, request);

        return ResponseEntity.ok(APIResponse.<AttributeResponse>builder()
                .success(true)
                .message("Values added to attribute successfully")
                .data(response)
                .build());
    }

    /**
     * Add new values to an existing attribute
     * Required: ADMIN user type with EDIT_PRODUCT permission
     */
    @PutMapping("/{attributeUuid}/values")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('EDIT_PRODUCT')")
    @Operation(
            summary = "Add values to attribute",
            description = "Add new values to an existing product attribute. Requires ADMIN user type with EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<AttributeResponse>> replaceValuesToAttribute(
            @PathVariable String attributeUuid,
            @Valid @RequestBody AddAttributeValuesRequest request) {
        log.info("PUT /api/v1/products/attributes/{}/values - Replacing values", attributeUuid);

        AttributeResponse response = attributeService.updateValuesToAttribute(attributeUuid, request);

        return ResponseEntity.ok(APIResponse.<AttributeResponse>builder()
                .success(true)
                .message("Attribute values replaced successfully")
                .data(response)
                .build());
    }


    /**
     * Update attribute details (name and/or active status)
     * Required: ADMIN user type with EDIT_PRODUCT permission
     */
    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('EDIT_PRODUCT')")
    @Operation(
            summary = "Update attribute",
            description = "Update attribute name and/or activation status. When changing activation status, all attribute values will also be updated. Requires ADMIN user type with EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<AttributeResponse>> updateAttribute(
            @PathVariable String uuid,
            @Valid @RequestBody UpdateAttributeRequest request) {
        log.info("PUT /api/v1/products/attributes/{} - Updating attribute", uuid);

        AttributeResponse response = attributeService.updateAttribute(uuid, request);

        return ResponseEntity.ok(APIResponse.<AttributeResponse>builder()
                .success(true)
                .message("Attribute updated successfully")
                .data(response)
                .build());
    }

    /**
     * Get attribute by UUID with its values
     */
    @GetMapping("/{uuid}")
    @Operation(
            summary = "Get attribute by UUID",
            description = "Retrieve a specific attribute with all its values. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<AttributeResponse>> getAttributeByUuid(@PathVariable String uuid) {
        log.info("GET /api/v1/products/attributes/{} - Fetching attribute", uuid);

        AttributeResponse response = attributeService.getAttributeByUuid(uuid);

        return ResponseEntity.ok(APIResponse.<AttributeResponse>builder()
                .success(true)
                .message("Attribute retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get all attributes with pagination
     */
    @GetMapping
    @Operation(
            summary = "Get all attributes (paginated)",
            description = "Retrieve all product attributes with pagination support. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<PageResponse<AttributeResponse>>> getAllAttributes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {
        log.info("GET /api/v1/products/attributes - Fetching all attributes (page: {}, size: {})", page, size);

        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<AttributeResponse> response = attributeService.getAllAttributes(pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<AttributeResponse>>builder()
                .success(true)
                .message("Attributes retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get all active attributes (for product creation forms)
     */
    @GetMapping("/active")
    @Operation(
            summary = "Get all active attributes",
            description = "Retrieve all active attributes with their active values. Useful for product creation forms. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<List<AttributeResponse>>> getAllActiveAttributes() {
        log.info("GET /api/v1/products/attributes/active - Fetching all active attributes");

        List<AttributeResponse> response = attributeService.getAllActiveAttributes();

        return ResponseEntity.ok(APIResponse.<List<AttributeResponse>>builder()
                .success(true)
                .message("Active attributes retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Delete attribute (soft delete)
     * Required: ADMIN user type with DELETE_PRODUCT permission
     */
    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('DELETE_PRODUCT')")
    @Operation(
            summary = "Delete attribute",
            description = "Soft delete a product attribute and all its values. Requires ADMIN user type with DELETE_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<Void>> deleteAttribute(@PathVariable String uuid) {
        log.info("DELETE /api/v1/products/attributes/{} - Deleting attribute", uuid);

        attributeService.deleteAttribute(uuid);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Attribute deleted successfully")
                .build());
    }

    /**
     * Delete a specific attribute value
     * Required: ADMIN user type with EDIT_PRODUCT permission
     */
    @DeleteMapping("/{attributeUuid}/values/{valueUuid}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('EDIT_PRODUCT')")
    @Operation(
            summary = "Delete attribute value",
            description = "Soft delete a specific value from an attribute. Requires ADMIN user type with EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<Void>> deleteAttributeValue(
            @PathVariable String attributeUuid,
            @PathVariable String valueUuid) {
        log.info("DELETE /api/v1/products/attributes/{}/values/{} - Deleting attribute value",
                attributeUuid, valueUuid);

        attributeService.deleteAttributeValue(attributeUuid, valueUuid);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Attribute value deleted successfully")
                .build());
    }

    /**
     * Activate attribute
     * Required: ADMIN user type with EDIT_PRODUCT permission
     */
    @PatchMapping("/{uuid}/activate")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('EDIT_PRODUCT')")
    @Operation(
            summary = "Activate attribute",
            description = "Activate a previously deleted attribute. Requires ADMIN user type with EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<AttributeResponse>> activateAttribute(@PathVariable String uuid) {
        log.info("PATCH /api/v1/products/attributes/{}/activate - Activating attribute", uuid);

        AttributeResponse response = attributeService.activateAttribute(uuid);

        return ResponseEntity.ok(APIResponse.<AttributeResponse>builder()
                .success(true)
                .message("Attribute activated successfully")
                .data(response)
                .build());
    }
}
