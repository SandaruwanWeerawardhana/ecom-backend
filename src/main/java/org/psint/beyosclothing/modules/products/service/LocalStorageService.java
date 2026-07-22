package org.psint.beyosclothing.modules.products.service;

import org.psint.beyosclothing.modules.products.dto.response.ImageUploadResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for local filesystem image storage.
 * Files are written under the configured upload directory and served back
 * to clients through the {@code /uploads/**} static resource handler.
 */
public interface LocalStorageService {

    /**
     * Store a file under {@code folderPath} using a generated unique filename.
     *
     * @param file       the file to store
     * @param folderPath the folder (relative to the upload directory) to store the file in
     * @param prefix     filename prefix used to build the unique filename
     * @return upload details including the stored object key and its public URL
     */
    ImageUploadResponse uploadFile(MultipartFile file, String folderPath, String prefix);

    /**
     * Store a file using an explicit object key (folder path + filename).
     *
     * @param file      the file to store
     * @param objectKey full object key (folder/filename.ext) relative to the upload directory
     * @return upload details including the stored object key and its public URL
     */
    ImageUploadResponse uploadFileWithKey(MultipartFile file, String objectKey);

    /**
     * Delete a stored file. Accepts either a bare object key or a full public URL.
     *
     * @param fileKey object key or public URL of the file to delete
     */
    void deleteFile(String fileKey);

    /**
     * Build the public URL used by the frontend to fetch a stored file.
     *
     * @param fileKey object key (or an already-built public URL)
     * @return public URL served by the {@code /uploads/**} resource handler
     */
    String getPublicUrl(String fileKey);
}
