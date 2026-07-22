package org.psint.beyosclothing.modules.admin.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Event to query user by email from Auth DB
 * Published by: Admin Module
 * Response expected from: Auth Module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryUserByEmailEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String email;
    private String requestId; // For tracking the response
}

