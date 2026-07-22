package org.psint.beyosclothing.modules.pos.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO to update a POS terminal
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTerminalRequest {

    private String name;

    @Size(max = 255, message = "Code must be at most 255 characters")
    private String code;

    @Size(max = 255, message = "Location must be at most 255 characters")
    private String location;

    private Boolean isActive;
}
