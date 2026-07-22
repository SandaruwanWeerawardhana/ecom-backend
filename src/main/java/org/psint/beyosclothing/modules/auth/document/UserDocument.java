package org.psint.beyosclothing.modules.auth.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Elasticsearch User Document
 * Stores ONLY public searchable user data
 * NEVER store passwords or sensitive data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {

    private String id;
    private String username;
    private String role;
    private Boolean isActive;
    private String createdAt;
}

