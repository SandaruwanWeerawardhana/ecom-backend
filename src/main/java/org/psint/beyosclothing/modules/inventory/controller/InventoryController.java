package org.psint.beyosclothing.modules.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.inventory.dto.request.UpdateStockRequest;
import org.psint.beyosclothing.modules.inventory.dto.request.UpdateStockStatusRequest;
import org.psint.beyosclothing.modules.inventory.dto.response.ProductDamageStockPageResponse;
import org.psint.beyosclothing.modules.inventory.dto.response.ProductLowStockPageResponse;
import org.psint.beyosclothing.modules.inventory.dto.response.ProductStockPageResponse;
import org.psint.beyosclothing.modules.inventory.service.InventoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Inventory", description = "Product stock management")
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping("/stocks")
    @Operation(summary = "List product stocks", description = "Returns paginated product stock information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product stocks retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ProductStockPageResponse listProductStocks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean all
    ) {
        Pageable pageable = all ? Pageable.unpaged() : PageRequest.of(Math.max(0, page), Math.max(1, size));
        return inventoryService.listProductStocks(pageable);
    }


    @PutMapping("/stocks/{productStockUuid}")
    @Operation(
            summary = "Update stock quantity",
            description = "Update stock for a product or a specific variant. " +
                    "Use movementType IN/OUT/RETURN for delta changes, or ADJUSTMENT to set an absolute value."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "404", description = "Product / variant not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Void> updateStockQuantity(
            @PathVariable String productStockUuid,
            @Valid @RequestBody UpdateStockRequest request
    ) {

        inventoryService.updateStockQuantity(
                productStockUuid,
                request.getQuantity(),
                request.getMovementType(),
                request.getPerformedBy(),
                request.getNotes()
        );

        return ResponseEntity.ok().build();
    }

    @PutMapping("/stocks/status/{productStockUuid}")
    @Operation(
            summary = "Update stock active status",
            description = "Activate or deactivate a product stock record by UUID"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "404", description = "Product stock not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Void> updateStockStatus(
            @PathVariable String productStockUuid,
            @Valid @RequestBody UpdateStockStatusRequest request
    ) {
        inventoryService.updateStockStatus(productStockUuid, request.getStatus());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/low/stocks")
    @Operation(summary = "Low stocks", description = "Returns paginated Low stocks information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product stocks retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ProductLowStockPageResponse getLowProductStocks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean all
    ) {
        Pageable pageable = all ? Pageable.unpaged() : PageRequest.of(Math.max(0, page), Math.max(1, size));
        return inventoryService.getLowProductStocks(pageable);
    }

    @GetMapping("/damage/stocks")
    @Operation(summary = "Damage stocks", description = "Returns paginated Damage stocks information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product stocks retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ProductDamageStockPageResponse getDamageProductStocks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean all
    ) {
        Pageable pageable = all ? Pageable.unpaged() : PageRequest.of(Math.max(0, page), Math.max(1, size));
        return inventoryService.getDamageProductStocks(pageable);
    }
}
