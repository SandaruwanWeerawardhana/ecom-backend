package org.psint.beyosclothing.modules.pos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO to create a POS terminal
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTerminalRequest {

    @NotBlank(message = "Terminal name is required")
    private String name;

    @NotBlank(message = "Terminal code is required")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Terminal code must be alphanumeric (dash/underscore allowed)")
    private String code;

    @Size(max = 255, message = "Location must be at most 255 characters")
    private String location;
}
