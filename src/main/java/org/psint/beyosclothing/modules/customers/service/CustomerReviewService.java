package org.psint.beyosclothing.modules.customers.service;

import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.customers.dto.request.CreateReviewRequest;
import org.psint.beyosclothing.modules.customers.dto.response.ReviewResponse;
import org.springframework.data.domain.Pageable;

/**
 * Customer Review Service Interface
 */
public interface CustomerReviewService {

    /**
     * Create a new product review
     * @param request Review details
     * @param customerId Customer ID from authenticated user
     * @return Created review response
     */
    ReviewResponse createReview(CreateReviewRequest request, Long customerId);

    /**
     * Create a new product review by customer email
     * @param request Review details
     * @param email Customer email from authenticated user
     * @return Created review response
     */
    ReviewResponse createReviewByEmail(CreateReviewRequest request, String email);

    /**
     * Get all reviews (for admin)
     * @param pageable Pagination details
     * @return Paginated reviews
     */
    PageResponse<ReviewResponse> getAllReviews(Pageable pageable);

    /**
     * Get reviews by approval status
     * @param isApproved Approval status
     * @param pageable Pagination details
     * @return Paginated reviews
     */
    PageResponse<ReviewResponse> getReviewsByApprovalStatus(Boolean isApproved, Pageable pageable);

    /**
     * Get reviews for a specific product
     * @param productUuid Product UUID
     * @param approvedOnly Whether to fetch only approved reviews
     * @param pageable Pagination details
     * @return Paginated reviews
     */
    PageResponse<ReviewResponse> getProductReviews(String productUuid, Boolean approvedOnly, Pageable pageable);

    /**
     * Approve a review (admin only)
     * @param reviewUuid Review UUID
     * @return Updated review response
     */
    ReviewResponse approveReview(String reviewUuid);

    /**
     * Reject/Unapprove a review (admin only)
     * @param reviewUuid Review UUID
     * @return Updated review response
     */
    ReviewResponse rejectReview(String reviewUuid);

    /**
     * Get customer's own reviews
     * @param customerId Customer ID
     * @param pageable Pagination details
     * @return Paginated reviews
     */
    PageResponse<ReviewResponse> getCustomerReviews(Long customerId, Pageable pageable);

    /**
     * Get customer's own reviews by email
     * @param email Customer email
     * @param pageable Pagination details
     * @return Paginated reviews
     */
    PageResponse<ReviewResponse> getCustomerReviewsByEmail(String email, Pageable pageable);
}