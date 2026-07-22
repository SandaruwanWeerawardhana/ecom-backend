package org.psint.beyosclothing.modules.customers.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.customers.dto.request.CreateReviewRequest;
import org.psint.beyosclothing.modules.customers.dto.response.ReviewImageResponse;
import org.psint.beyosclothing.modules.customers.dto.response.ReviewResponse;
import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.psint.beyosclothing.modules.customers.entity.CustomerReview;
import org.psint.beyosclothing.modules.customers.entity.ReviewImage;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.psint.beyosclothing.modules.customers.repository.CustomerReviewRepository;
import org.psint.beyosclothing.modules.customers.service.CustomerReviewService;
import org.psint.beyosclothing.modules.products.entity.Product;
import org.psint.beyosclothing.modules.products.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Customer Review Service Implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomerReviewServiceImpl implements CustomerReviewService {

    private final CustomerReviewRepository reviewRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    @Override
    public ReviewResponse createReview(CreateReviewRequest request, Long customerId) {
        log.info("Creating review for product: {} by customer: {}", request.getProductUuid(), customerId);

        // Validate customer exists
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        // Validate product exists
        Product product = productRepository.findByUuid(request.getProductUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with UUID: " + request.getProductUuid()));

        // Check if customer has already reviewed this product
        if (reviewRepository.existsByCustomerIdAndProductId(customerId, product.getId())) {
            throw new BadRequestException("You have already reviewed this product");
        }

        // Create review
        CustomerReview review = CustomerReview.builder()
                .customerId(customerId)
                .productId(product.getId())
                .rating(request.getRating())
                .comment(request.getComment())
                .isApproved(false) // Must be approved by admin
                .build();

        // Add images if provided
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            int order = 0;
            for (String imageUrl : request.getImageUrls()) {
                ReviewImage image = ReviewImage.builder()
                        .review(review)
                        .imageUrl(imageUrl)
                        .displayOrder(order++)
                        .build();
                review.getImages().add(image);
            }
        }

        // Save review
        CustomerReview savedReview = reviewRepository.save(review);
        log.info("Review created successfully with UUID: {}", savedReview.getUuid());

        return mapToResponse(savedReview, customer, product);
    }

    @Override
    public ReviewResponse createReviewByEmail(CreateReviewRequest request, String email) {
        log.info("Creating review for product: {} by customer email: {}", request.getProductUuid(), email);

        // Find customer by email
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with email: " + email));

        // Use the existing createReview method with customer ID
        return createReview(request, customer.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getAllReviews(Pageable pageable) {
        log.info("Fetching all reviews with pagination");
        Page<CustomerReview> reviewPage = reviewRepository.findAll(pageable);
        return mapToPageResponse(reviewPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getReviewsByApprovalStatus(Boolean isApproved, Pageable pageable) {
        log.info("Fetching reviews by approval status: {}", isApproved);
        Page<CustomerReview> reviewPage = reviewRepository.findByIsApproved(isApproved, pageable);
        return mapToPageResponse(reviewPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getProductReviews(String productUuid, Boolean approvedOnly, Pageable pageable) {
        log.info("Fetching reviews for product: {}, approvedOnly: {}", productUuid, approvedOnly);

        Product product = productRepository.findByUuid(productUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with UUID: " + productUuid));

        Page<CustomerReview> reviewPage;
        if (approvedOnly != null && approvedOnly) {
            reviewPage = reviewRepository.findByProductIdAndIsApproved(product.getId(), true, pageable);
        } else {
            reviewPage = reviewRepository.findByProductId(product.getId(), pageable);
        }

        return mapToPageResponse(reviewPage);
    }

    @Override
    public ReviewResponse approveReview(String reviewUuid) {
        log.info("Approving review: {}", reviewUuid);

        CustomerReview review = reviewRepository.findByUuid(reviewUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with UUID: " + reviewUuid));

        review.setIsApproved(true);
        CustomerReview updatedReview = reviewRepository.save(review);

        log.info("Review approved successfully: {}", reviewUuid);

        // Fetch customer and product for response
        Customer customer = customerRepository.findById(review.getCustomerId()).orElse(null);
        Product product = productRepository.findById(review.getProductId()).orElse(null);

        return mapToResponse(updatedReview, customer, product);
    }

    @Override
    public ReviewResponse rejectReview(String reviewUuid) {
        log.info("Rejecting review: {}", reviewUuid);

        CustomerReview review = reviewRepository.findByUuid(reviewUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with UUID: " + reviewUuid));

        review.setIsApproved(false);
        CustomerReview updatedReview = reviewRepository.save(review);

        log.info("Review rejected successfully: {}", reviewUuid);

        // Fetch customer and product for response
        Customer customer = customerRepository.findById(review.getCustomerId()).orElse(null);
        Product product = productRepository.findById(review.getProductId()).orElse(null);

        return mapToResponse(updatedReview, customer, product);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getCustomerReviews(Long customerId, Pageable pageable) {
        log.info("Fetching reviews for customer: {}", customerId);
        Page<CustomerReview> reviewPage = reviewRepository.findByCustomerId(customerId, pageable);
        return mapToPageResponse(reviewPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getCustomerReviewsByEmail(String email, Pageable pageable) {
        log.info("Fetching reviews for customer with email: {}", email);

        // Find customer by email
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with email: " + email));

        // Fetch reviews using customer ID
        Page<CustomerReview> reviewPage = reviewRepository.findByCustomerId(customer.getId(), pageable);
        return mapToPageResponse(reviewPage);
    }

    // Helper methods

    private PageResponse<ReviewResponse> mapToPageResponse(Page<CustomerReview> reviewPage) {
        List<ReviewResponse> responses = reviewPage.getContent().stream()
                .map(review -> {
                    Customer customer = customerRepository.findById(review.getCustomerId()).orElse(null);
                    Product product = productRepository.findById(review.getProductId()).orElse(null);
                    return mapToResponse(review, customer, product);
                })
                .collect(Collectors.toList());

        return PageResponse.<ReviewResponse>builder()
                .content(responses)
                .pageNumber(reviewPage.getNumber())
                .pageSize(reviewPage.getSize())
                .totalElements(reviewPage.getTotalElements())
                .totalPages(reviewPage.getTotalPages())
                .last(reviewPage.isLast())
                .build();
    }

    private ReviewResponse mapToResponse(CustomerReview review, Customer customer, Product product) {
        // Map customer info
        ReviewResponse.CustomerBasicInfo customerInfo = null;
        if (customer != null) {
            customerInfo = ReviewResponse.CustomerBasicInfo.builder()
                    .firstName(customer.getFirstName())
                    .lastName(customer.getLastName())
                    .profileImage(customer.getProfileImage())
                    .build();
        }

        // Map review images
        List<ReviewImageResponse> imageResponses = review.getImages().stream()
                .map(image -> ReviewImageResponse.builder()
                        .uuid(image.getUuid())
                        .imageUrl(image.getImageUrl())
                        .displayOrder(image.getDisplayOrder())
                        .build())
                .collect(Collectors.toList());

        return ReviewResponse.builder()
                .uuid(review.getUuid())
                .productUuid(product != null ? product.getUuid() : null)
                .productTitle(product != null ? product.getTitle() : null)
                .customer(customerInfo)
                .rating(review.getRating())
                .comment(review.getComment())
                .isApproved(review.getIsApproved())
                .images(imageResponses)
                .dateCreated(review.getDateCreated())
                .dateUpdated(review.getDateUpdated())
                .build();
    }
}
