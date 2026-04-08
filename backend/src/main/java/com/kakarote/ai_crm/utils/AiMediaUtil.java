package com.kakarote.ai_crm.utils;

import cn.hutool.core.io.FileUtil;
import com.kakarote.ai_crm.service.FileStorageService;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.io.InputStream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AiMediaUtil {

    public static Media buildMedia(FileStorageService fileStorageService, String filePath, MimeType mimeType)
            throws IOException {
        try (InputStream inputStream = fileStorageService.getFileStream(filePath)) {
            byte[] data = inputStream.readAllBytes();
            Resource resource = new ByteArrayResource(data) {
                @Override
                public String getFilename() {
                    return FileUtil.getName(filePath);
                }
            };
            return new Media(mimeType, resource);
        }
    }
}
