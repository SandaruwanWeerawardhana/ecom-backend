package org.psint.beyosclothing.modules.customers.repository;

import org.psint.beyosclothing.modules.customers.entity.CustomerReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerReviewRepository extends JpaRepository<CustomerReview, Long> {

    Optional<CustomerReview> findByUuid(String uuid);

    Page<CustomerReview> findByIsApproved(Boolean isApproved, Pageable pageable);

    Page<CustomerReview> findByProductId(Long productId, Pageable pageable);

    Page<CustomerReview> findByProductIdAndIsApproved(Long productId, Boolean isApproved, Pageable pageable);

    Page<CustomerReview> findByCustomerId(Long customerId, Pageable pageable);

    Boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    @Query("SELECT COUNT(r) FROM CustomerReview r WHERE r.productId = :productId AND r.isApproved = true")
    Long countApprovedReviewsByProductId(@Param("productId") Long productId);
}

