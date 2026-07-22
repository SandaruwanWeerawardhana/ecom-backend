package org.psint.beyosclothing.modules.auth.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.auth.document.UserDocument;
import org.psint.beyosclothing.modules.auth.entity.User;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Elasticsearch Sync Service
 * Synchronizes user data to Elasticsearch for search
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchSyncService {

    private final ElasticsearchClient elasticsearchClient;
    private static final String USER_INDEX = "users";

    public void indexUser(User user) {
        try {
            // Get role name from userRoleId
            String roleName = user.getUserType();
            if (user.getUserRoleId() != null) {
                // You may want to inject UserRoleRepository to fetch the actual role name
                // For now, use userType as fallback
                roleName = user.getUserType();
            }

            UserDocument document = UserDocument.builder()
                    .id(user.getId().toString())
                    .username(user.getUsername())
                    .role(roleName)
                    .isActive(user.getIsActive())
                    .createdAt(user.getDateCreated().toString())
                    .build();

            IndexRequest<UserDocument> request = IndexRequest.of(i -> i
                    .index(USER_INDEX)
                    .id(document.getId())
                    .document(document)
            );

            elasticsearchClient.index(request);
            log.info("User indexed in Elasticsearch: {}", user.getId());
        } catch (IOException e) {
            log.error("Failed to index user in Elasticsearch", e);
            // Don't throw exception - indexing failure shouldn't break main flow
        }
    }

    public List<UserDocument> searchUsers(String query) {
        try {
            SearchResponse<UserDocument> response = elasticsearchClient.search(s -> s
                    .index(USER_INDEX)
                    .query(q -> q
                            .multiMatch(m -> m
                                    .query(query)
                                    .fields("username", "role")
                            )
                    ),
                    UserDocument.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to search users in Elasticsearch", e);
            return List.of();
        }
    }

    public void deleteUser(String userId) {
        try {
            elasticsearchClient.delete(d -> d
                    .index(USER_INDEX)
                    .id(userId)
            );
            log.info("User deleted from Elasticsearch: {}", userId);
        } catch (IOException e) {
            log.error("Failed to delete user from Elasticsearch", e);
        }
    }
}
