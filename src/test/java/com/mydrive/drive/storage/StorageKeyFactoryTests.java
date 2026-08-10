/*
 * PHASE 3 unit tests; no Spring context.
 *
 * Verify temporaryKey and versionKey contain the expected UUIDs/version and
 * never include an original filename.
 */
package com.mydrive.drive.storage;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StorageKeyFactoryTests {
    private final StorageKeyFactory keyFactory = new StorageKeyFactory();

    @Test
    void temporaryKeyUsesOwnerAndUploadIds() {
        UUID ownerId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();

        assertThat(keyFactory.temporaryKey(ownerId, uploadId))
                .isEqualTo("temp/" + ownerId + "/" + uploadId);
    }

    @Test
    void versionKeyUsesStableIdsAndNotOriginalFilename() {
        UUID ownerId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        String key = keyFactory.versionKey(ownerId, fileId, 3);

        assertThat(key).isEqualTo(
                "users/" + ownerId + "/files/" + fileId + "/versions/3"
        );
        assertThat(key).doesNotContain("report.pdf");
    }
}
