

package com.mydrive.drive.file;

import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties(FileVersionProperties.class)
public record FileVersionProperties(
        @jakarta.validation.constraints.Min(1) int maxRetained,
        @jakarta.validation.constraints.Min(1) int retentionDays) {
}