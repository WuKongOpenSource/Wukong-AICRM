package com.kakarote.ai_crm.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * AI audio transcription service.
 */
public interface AiAudioTranscriptionService {

    /**
     * Transcribe an audio file with the current effective AI provider.
     *
     * @param audioFile audio file
     * @return transcript text
     */
    String transcribe(MultipartFile audioFile);
}
