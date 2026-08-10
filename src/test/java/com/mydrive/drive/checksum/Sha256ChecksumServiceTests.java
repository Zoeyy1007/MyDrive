
package com.mydrive.drive.checksum;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256ChecksumServiceTests {
    private final ChecksumService checksumService = new Sha256ChecksumService();

    @Test
    void calculatesKnownChecksums() {
        assertThat(checksumService.sha256(new ByteArrayInputStream(new byte[0])))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(checksumService.sha256(new ByteArrayInputStream(
                "hello".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void equalContentProducesEqualChecksums() {
        byte[] content = "same content".getBytes(StandardCharsets.UTF_8);

        String first = checksumService.sha256(new ByteArrayInputStream(content));
        String second = checksumService.sha256(new ByteArrayInputStream(content));

        assertThat(first).isEqualTo(second);
    }
}
