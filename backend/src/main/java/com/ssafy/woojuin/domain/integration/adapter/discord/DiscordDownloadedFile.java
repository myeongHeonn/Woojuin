package com.ssafy.woojuin.domain.integration.adapter.discord;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

final class DiscordDownloadedFile implements MultipartFile {

    private final String filename;
    private final String contentType;
    private final byte[] bytes;

    DiscordDownloadedFile(String filename, String contentType, byte[] bytes) {
        this.filename = filename;
        this.contentType = contentType;
        this.bytes = bytes;
    }

    @Override public String getName() { return "file"; }
    @Override public String getOriginalFilename() { return filename; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return bytes.length == 0; }
    @Override public long getSize() { return bytes.length; }
    @Override public byte[] getBytes() { return bytes.clone(); }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
    @Override public void transferTo(java.io.File destination) throws java.io.IOException {
        java.nio.file.Files.write(destination.toPath(), bytes);
    }
}
