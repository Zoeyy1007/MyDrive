/*
 * PHASE 3: Typed MinIO configuration.
 *
 * Import @ConfigurationProperties and create a record or class bound to:
 *   app.storage.minio
 *
 * Properties:
 *   String endpoint
 *   String accessKey
 *   String secretKey
 *   String bucket
 *
 * Never log secretKey. Use validation if you choose a class-based properties
 * object.
 */
package com.mydrive.drive.storage.minio;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.minio")
public record MinioProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket
) {
}
