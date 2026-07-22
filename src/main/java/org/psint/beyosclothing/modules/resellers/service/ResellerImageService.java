package org.psint.beyosclothing.modules.resellers.service;

import org.psint.beyosclothing.modules.resellers.dto.response.ResellerImageUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ResellerImageService {

    ResellerImageUploadResponse uploadProfileImage(Long userId, MultipartFile file);
}

