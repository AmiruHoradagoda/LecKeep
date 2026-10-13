package com.devProject.leckeep_backend.adviser;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.devProject.leckeep_backend.exception.FolderNotFoundException;
import com.devProject.leckeep_backend.exception.GroupNotFoundException;
import com.devProject.leckeep_backend.exception.UserNotFoundException;
import com.devProject.leckeep_backend.utils.StandardResponseDto;

@RestControllerAdvice
public class AppWideExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<StandardResponseDto> handleUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new StandardResponseDto(413, "File size must not exceed 25 MB", null));
    }

    @ExceptionHandler(FolderNotFoundException.class)
    public ResponseEntity<StandardResponseDto> handleBadRequestException(FolderNotFoundException ex) {
        return new ResponseEntity<StandardResponseDto>(
                new StandardResponseDto(400,ex.getMessage(),ex),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(GroupNotFoundException.class)
    public ResponseEntity<StandardResponseDto> handleGroupNotFoundException(GroupNotFoundException ex) {
        return new ResponseEntity<StandardResponseDto>(
                new StandardResponseDto(404, ex.getMessage(), ex),
                HttpStatus.NOT_FOUND
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<StandardResponseDto> handleDuplicateEntryException(UserNotFoundException ex) {
        return new ResponseEntity<StandardResponseDto>(
                new StandardResponseDto(409,ex.getMessage(),ex),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<StandardResponseDto> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        return new ResponseEntity<StandardResponseDto>(
                new StandardResponseDto(500, "Database constraint violation", ex),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<StandardResponseDto> handleIllegalStateException(IllegalStateException ex) {
        return new ResponseEntity<StandardResponseDto>(
                new StandardResponseDto(500, ex.getMessage(), ex),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<StandardResponseDto> handleIllegalArgumentException(IllegalArgumentException ex) {
        return new ResponseEntity<StandardResponseDto>(
                new StandardResponseDto(400, ex.getMessage(), ex),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<StandardResponseDto> handleSecurityException(SecurityException ex) {
        return new ResponseEntity<StandardResponseDto>(
                new StandardResponseDto(403, ex.getMessage(), ex),
                HttpStatus.FORBIDDEN
        );
    }

}
