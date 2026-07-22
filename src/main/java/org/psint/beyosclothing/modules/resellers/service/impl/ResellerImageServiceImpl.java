package org.psint.beyosclothing.modules.resellers.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.modules.products.dto.response.ImageUploadResponse;
import org.psint.beyosclothing.modules.products.service.S3StorageService;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerImageUploadResponse;
import org.psint.beyosclothing.modules.resellers.entity.Reseller;
import org.psint.beyosclothing.modules.resellers.repository.ResellerRepository;
import org.psint.beyosclothing.modules.resellers.service.ResellerImageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Uploads reseller profile images to AWS S3 and stores the resulting URL on the reseller profile.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional("resellerTransactionManager")
public class ResellerImageServiceImpl implements ResellerImageService {

    private final ResellerRepository resellerRepository;
    private final S3StorageService s3StorageService;

    @Override
    public ResellerImageUploadResponse uploadProfileImage(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new BadRequestException("userId is required");
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Image file is required");
        }

        Reseller reseller = resellerRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Reseller not found for userId: " + userId));

        // Upload to S3 under a stable folder for this reseller
        String folderPath = String.format("resellers/profiles/%s", reseller.getUuid());
        ImageUploadResponse uploadResponse = s3StorageService.uploadFile(file, folderPath, "profile");

        // Save the URL on reseller profile
        reseller.setImageUrl(uploadResponse.getImageUrl());
        resellerRepository.save(reseller);

        log.info("Reseller profile image uploaded: resellerUuid={}, key={}", reseller.getUuid(), uploadResponse.getImageName());

        return ResellerImageUploadResponse.builder()
                .success(true)
                .message("Profile image uploaded successfully")
                .imageName(uploadResponse.getImageName())
                .imageUrl(uploadResponse.getImageUrl())
                .build();
    }
}

