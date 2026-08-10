
package com.mydrive.drive.storage.minio;

import com.mydrive.drive.storage.StorageException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {
    @Bean
    MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    ApplicationRunner ensureMinioBucket(
            MinioClient minioClient,
            MinioProperties properties) {

        return args -> {
            String bucket = properties.bucket();

            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder()
                                .bucket(bucket)
                                .build()
                );

                if (!exists) {
                    minioClient.makeBucket(
                            MakeBucketArgs.builder()
                                    .bucket(bucket)
                                    .build()
                    );
                }
            } catch (ErrorResponseException exception) {
                String code = exception.errorResponse().code();

                // Another application instance may have created the bucket
                // after bucketExists() returned false.
                if (!"BucketAlreadyExists".equals(code)
                        && !"BucketAlreadyOwnedByYou".equals(code)) {
                    throw new StorageException(
                            "Could not initialize storage bucket " + bucket,
                            exception
                    );
                }
            } catch (Exception exception) {
                throw new StorageException(
                        "Could not initialize storage bucket " + bucket,
                        exception
                );
            }
        };
    }
}
