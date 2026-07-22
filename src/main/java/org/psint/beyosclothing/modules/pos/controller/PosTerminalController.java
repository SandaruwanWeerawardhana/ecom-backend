package org.psint.beyosclothing.modules.pos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.constants.ResponseCode;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.pos.dto.request.CreateTerminalRequest;
import org.psint.beyosclothing.modules.pos.dto.request.UpdateTerminalRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosTerminalDetailsResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosTerminalResponse;
import org.psint.beyosclothing.modules.pos.entity.PosTerminalEntity;
import org.psint.beyosclothing.modules.pos.service.PosTerminalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/pos/terminals")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "POS Terminals", description = "POS terminal management (admin)")
public class PosTerminalController {

    private final PosTerminalService posTerminalService;

    @Operation(summary = "Create POS terminal")
    @ApiResponse(responseCode = "201", description = "Terminal created")
    @PostMapping
//    @PreAuthorize("hasAuthority('MANAGE_POS')")
    public ResponseEntity<APIResponse<PosTerminalResponse>> createTerminal(@Valid @RequestBody CreateTerminalRequest request) {
        log.info("Creating POS terminal with code={}", request.getCode());

        if (posTerminalService.findByCode(request.getCode()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(APIResponse.<PosTerminalResponse>builder()
                            .responseCode(ResponseCode.BAD_REQUEST.getCode())
                            .success(false)
                            .message("Terminal code already exists")
                            .build());
        }

        PosTerminalEntity entity = PosTerminalEntity.builder()
                .name(request.getName())
                .code(request.getCode())
                .location(request.getLocation())
                .isActive(true)
                .build();

        PosTerminalEntity saved = posTerminalService.save(entity);
        PosTerminalResponse resp = mapToResponse(saved);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<PosTerminalResponse>builder()
                        .responseCode(ResponseCode.SUCCESS.getCode())
                        .success(true)
                        .message("Terminal created successfully")
                        .data(resp)
                        .build());
    }

    @Operation(summary = "List POS terminals (paginated)")
    @ApiResponse(responseCode = "200", description = "Terminals retrieved")
    @GetMapping
//    @PreAuthorize("hasAuthority('VIEW_POS')")
    public ResponseEntity<APIResponse<PageResponse<PosTerminalResponse>>> listTerminals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<PosTerminalEntity> pageData = posTerminalService.findByIsActiveTrue(pageable);

        List<PosTerminalResponse> resp = pageData.getContent().stream().map(this::mapToResponse).toList();

        PageResponse<PosTerminalResponse> pageResponse = PageResponse.<PosTerminalResponse>builder()
                .content(resp)
                .pageNumber(pageData.getNumber())
                .pageSize(pageData.getSize())
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .last(pageData.isLast())
                .first(pageData.isFirst())
                .empty(pageData.isEmpty())
                .build();

        return ResponseEntity.ok(APIResponse.<PageResponse<PosTerminalResponse>>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Terminals retrieved")
                .data(pageResponse)
                .build());
    }

    @Operation(summary = "Get POS terminal by UUID")
    @ApiResponse(responseCode = "200", description = "Terminal retrieved")
    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('VIEW_POS')")
    public ResponseEntity<APIResponse<PosTerminalResponse>> getTerminal(@PathVariable String uuid) {
        return posTerminalService.findByUuid(uuid)
                .map(entity -> ResponseEntity.ok(APIResponse.<PosTerminalResponse>builder()
                        .responseCode(ResponseCode.SUCCESS.getCode())
                        .success(true)
                        .message("Terminal retrieved")
                        .data(mapToResponse(entity))
                        .build()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(APIResponse.<PosTerminalResponse>builder()
                                .responseCode(ResponseCode.NOT_FOUND.getCode())
                                .success(false)
                                .message("Terminal not found")
                                .build()));
    }

    @Operation(summary = "Update POS terminal")
    @ApiResponse(responseCode = "200", description = "Terminal updated")
    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('MANAGE_POS')")
    public ResponseEntity<APIResponse<PosTerminalResponse>> updateTerminal(@PathVariable String uuid, @Valid @RequestBody UpdateTerminalRequest request) {
        return posTerminalService.updateTerminal(uuid, request.getName(), request.getCode(), request.getLocation(), request.getIsActive())
                .map(saved -> ResponseEntity.ok(APIResponse.<PosTerminalResponse>builder()
                        .responseCode(ResponseCode.UPDATED.getCode())
                        .success(true)
                        .message("Terminal updated")
                        .data(mapToResponse(saved))
                        .build()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(APIResponse.<PosTerminalResponse>builder()
                                .responseCode(ResponseCode.NOT_FOUND.getCode())
                                .success(false)
                                .message("Terminal not found")
                                .build()));
    }

    @Operation(summary = "Deactivate POS terminal (soft delete)")
    @ApiResponse(responseCode = "204", description = "Terminal deactivated")
    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('MANAGE_POS')")
    public ResponseEntity<Void> deactivateTerminal(@PathVariable String uuid) {
        Optional<PosTerminalEntity> opt = posTerminalService.findByUuid(uuid);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PosTerminalEntity entity = opt.get();
        entity.setIsActive(false);
        entity.setUpdatedAt(LocalDateTime.now());
        posTerminalService.save(entity);
        return ResponseEntity.noContent().build();
    }

    private PosTerminalResponse mapToResponse(PosTerminalEntity entity) {
        return PosTerminalResponse.builder()
                .uuid(entity.getUuid())
                .name(entity.getName())
                .location(entity.getLocation())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    @PostMapping("/Validate/{uuid}/{code}")
    @Operation(summary = "Get POS terminal by code",
            description = "Returns terminal details for given code. Used for terminal lookup during cart operations."
    )
    public ResponseEntity<APIResponse<PosTerminalDetailsResponse>> getTerminalByCode(@PathVariable String uuid, @PathVariable String code) {
        log.info("API: POS getTerminalByCode - code={}", code);
        return posTerminalService.validateByTerminal(uuid, code)
                .map(entity -> ResponseEntity.ok(APIResponse.<PosTerminalDetailsResponse>builder()
                        .responseCode(ResponseCode.SUCCESS.getCode())
                        .success(true)
                        .message("Terminal retrieved")
                        .data(mapToResponses(entity))
                        .build()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(APIResponse.<PosTerminalDetailsResponse>builder()
                                .responseCode(ResponseCode.NOT_FOUND.getCode())
                                .success(false)
                                .message("Terminal not found")
                                .build()));
    }

    private PosTerminalDetailsResponse mapToResponses(PosTerminalEntity entity) {
        return PosTerminalDetailsResponse.builder()
                .uuid(entity.getUuid())
                .name(entity.getName())
                .location(entity.getLocation())
                .build();

    }

    @Operation(summary = "List POS terminals (paginated)")
    @ApiResponse(responseCode = "200", description = "Terminals retrieved")
    @GetMapping("/active")
//    @PreAuthorize("hasAuthority('VIEW_POS')")
    public ResponseEntity<APIResponse<PageResponse<PosTerminalDetailsResponse>>> getAllisActive() {

        List<PosTerminalEntity> list = posTerminalService.getAllisActive();
        List<PosTerminalDetailsResponse> resp = list.stream().map(this::mapToResponses).toList();

        PageResponse<PosTerminalDetailsResponse> pageResponse = PageResponse.<PosTerminalDetailsResponse>builder()
                .content(resp)
                .pageNumber(0)
                .pageSize(resp.size())
                .totalElements(resp.size())
                .totalPages(resp.isEmpty() ? 0 : 1)
                .last(true)
                .first(true)
                .empty(resp.isEmpty())
                .build();

        return ResponseEntity.ok(APIResponse.<PageResponse<PosTerminalDetailsResponse>>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Terminals retrieved")
                .data(pageResponse)
                .build());

    }
}
