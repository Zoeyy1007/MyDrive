
package com.mydrive.drive.storage.minio;

import com.mydrive.drive.storage.StorageException;
import com.mydrive.drive.storage.StorageService;
import com.mydrive.drive.storage.StoredObject;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SourceObject;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class MinioStorageService implements StorageService {
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public MinioStorageService(MinioClient minioClient, MinioProperties minioProperties) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    @Override
    public StoredObject save(
            String key,
            InputStream content,
            long size,
            String contentType) {

        String resolvedContentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.bucket())
                            .object(key)
                            .stream(content, size, -1L)
                            .contentType(resolvedContentType)
                            .build()
            );

            return new StoredObject(key, size, resolvedContentType);
        } catch (Exception exception) {
            throw new StorageException("Could not save object " + key, exception);
        }
    }

    /**
     * Returns a network-backed stream. The caller must close it.
     */
    @Override
    public InputStream load(String key) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProperties.bucket())
                            .object(key)
                            .build()
            );
        } catch (Exception exception) {
            throw new StorageException("Could not load object " + key, exception);
        }
    }

    @Override
    public void copy(String sourceKey, String destinationKey) {
        try {
            SourceObject source = SourceObject.builder()
                    .bucket(minioProperties.bucket())
                    .object(sourceKey)
                    .build();

            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(minioProperties.bucket())
                            .object(destinationKey)
                            .source(source)
                            .build()
            );
        } catch (Exception exception) {
            throw new StorageException(
                    "Could not copy object " + sourceKey + " to " + destinationKey,
                    exception
            );
        }
    }

    @Override
    public void delete(String key) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.bucket())
                            .object(key)
                            .build()
            );
        } catch (Exception exception) {
            throw new StorageException("Could not delete object " + key, exception);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioProperties.bucket())
                            .object(key)
                            .build()
            );
            return true;
        } catch (ErrorResponseException exception) {
            String code = exception.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                return false;
            }
            throw new StorageException("Could not inspect object " + key, exception);
        } catch (Exception exception) {
            throw new StorageException("Could not inspect object " + key, exception);
        }
    }

}
