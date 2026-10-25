
package com.realchat.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> api(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(Map.of(
                        "status", e.getStatus().value(),
                        "message", e.getMessage()
                ));
    }

    // Handle upload-size errors through the parent class's extension point.
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of(
                        "status", 413,
                        "message", "File is too large"
                ));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Object> stale(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(409)
                .body(Map.of(
                        "status", 409,
                        "message", "This item just changed. Refresh and try again."
                ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> integrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(409)
                .body(Map.of(
                        "status", 409,
                        "message", "Conflict: the data already exists or is invalid"
                ));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> fields = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors()
                .forEach(f -> fields.putIfAbsent(
                        f.getField(), f.getDefaultMessage()
                ));

        return ResponseEntity.badRequest()
                .body(Map.of(
                        "status", 400,
                        "message", "Validation failed",
                        "fields", fields
                ));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> other(Exception e) {
        log.error("Unhandled error", e);

        return ResponseEntity.status(500)
                .body(Map.of(
                        "status", 500,
                        "message", "Internal server error"
                ));
    }
}
