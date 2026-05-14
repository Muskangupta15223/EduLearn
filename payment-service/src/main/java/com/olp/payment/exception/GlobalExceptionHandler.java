package com.olp.payment.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(PaymentException.class)
  public ResponseEntity<Map<String, String>> handlePaymentException(PaymentException ex) {
    log.warn("PaymentException [{}]: {}", ex.getStatus(), ex.getMessage());
    return ResponseEntity
      .status(ex.getStatus())
      .body(Map.of("error", ex.getMessage(), "message", ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException ex) {
    String msg = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .findFirst()
        .orElse("Invalid payment request payload");
    return ResponseEntity
      .badRequest()
      .body(Map.of("error", msg, "message", msg));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
    log.error("Unhandled exception in payment service", ex);
    String msg = ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred";
    return ResponseEntity
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(Map.of("error", msg, "message", msg));
  }
}
