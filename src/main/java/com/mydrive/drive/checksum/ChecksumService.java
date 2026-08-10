
package com.mydrive.drive.checksum;
import java.io.InputStream;

public interface ChecksumService {
    String sha256(InputStream content);
}
