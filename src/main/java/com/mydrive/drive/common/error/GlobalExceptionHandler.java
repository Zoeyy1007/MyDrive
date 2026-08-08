
package com.mydrive.drive.common.error;

import com.mydrive.drive.account.AppUserNotFoundException;
import com.mydrive.drive.account.EmailAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler{

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleDuplicateEmail(
            EmailAlreadyExistsException exception, HttpServletRequest request){
        ApiError apiError = new ApiError(
            Instant.now(),
            HttpStatus.CONFLICT.value(),
            "Conflict",
            exception.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(apiError);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request){
        String fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ApiError apiError = new ApiError(
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            fieldErrors,
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiError);
    }

    @ExceptionHandler(AppUserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(
            AppUserNotFoundException exception, HttpServletRequest request){
        ApiError apiError = new ApiError(
            Instant.now(),
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            exception.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiError);
    }
}
