package com.example.logreg.config;

import com.example.logreg.dto.AvatarUploadDTO;
import com.example.logreg.dto.SuccessDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<SuccessDTO<AvatarUploadDTO>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.ok(new SuccessDTO<>(new AvatarUploadDTO(false, null, "文件过大")));
    }
}