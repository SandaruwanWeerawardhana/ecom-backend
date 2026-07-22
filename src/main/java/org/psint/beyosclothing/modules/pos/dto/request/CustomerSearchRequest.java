package org.psint.beyosclothing.modules.pos.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSearchRequest implements Serializable {

    private String query;
    private Integer limit;
}
