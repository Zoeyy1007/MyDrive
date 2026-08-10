
package com.mydrive.drive.file;

import java.io.InputStream;

public record FileDownload (
        InputStream inputStream,
        String filename,
        String contentType,
        long size
){}