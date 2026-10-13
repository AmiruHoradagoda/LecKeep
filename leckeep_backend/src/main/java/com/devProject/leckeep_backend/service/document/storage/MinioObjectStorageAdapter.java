package com.devProject.leckeep_backend.service.document.storage;

import com.devProject.leckeep_backend.exception.ObjectStorageException;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "minio", matchIfMissing = true)
public class MinioObjectStorageAdapter implements ObjectStorageService{
    private static final int PREVIEW_URL_EXPIRY_MINUTES = 15;

    private final MinioClient minioClient;
    private final MinioClient minioPreviewClient;

    public MinioObjectStorageAdapter(MinioClient minioClient,
            @Qualifier("minioPreviewClient") MinioClient minioPreviewClient) {
        this.minioClient = minioClient;
        this.minioPreviewClient = minioPreviewClient;
    }

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Override
    public StoredObject upload(FileUploadCommand command) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(command.getObjectKey())
                            .stream(command.getInputStream(), command.getSizeBytes(), -1)
                            .contentType(command.getContentType())
                            .build()
            );

            return StoredObject.builder()
                    .bucketName(bucketName)
                    .objectKey(command.getObjectKey())
                    .originalName(command.getOriginalName())
                    .contentType(command.getContentType())
                    .sizeBytes(command.getSizeBytes())
                    .build();
        } catch (Exception ex) {
            throw new ObjectStorageException("Failed to upload object: " + command.getObjectKey(), ex);
        }
    }

    @Override
    public DownloadedObject download(String objectKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );

            return DownloadedObject.builder()
                    .objectKey(objectKey)
                    .contentType(stat.contentType())
                    .sizeBytes(stat.size())
                    .inputStream(minioClient.getObject(
                            GetObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(objectKey)
                                    .build()
                    ))
                    .build();
        } catch (Exception ex) {
            throw new ObjectStorageException("Failed to download object: " + objectKey, ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception ex) {
            throw new ObjectStorageException("Failed to delete object: " + objectKey, ex);
        }
    }

    @Override
    public String generatePreviewUrl(String objectKey) {
        try {
            return minioPreviewClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(PREVIEW_URL_EXPIRY_MINUTES, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception ex) {
            throw new ObjectStorageException("Failed to generate preview URL: " + objectKey, ex);
        }
    }
}
