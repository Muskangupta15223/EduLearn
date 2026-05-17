package com.olp.payment.exception;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handlePaymentExceptionUsesExceptionStatusAndMessage() {
        PaymentException ex = new PaymentException("Access denied", HttpStatus.FORBIDDEN);

        ResponseEntity<Map<String, String>> response = handler.handlePaymentException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Access denied", response.getBody().get("error"));
        assertEquals("Access denied", response.getBody().get("message"));
    }

    @Test
    void handleValidationExceptionReturnsFirstFieldMessage() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "amount", "must be greater than 0"));
        MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleMethod", Object.class),
                0
        );
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("amount: must be greater than 0", response.getBody().get("error"));
    }

    @Test
    void handleGenericExceptionFallsBackToInternalServerError() {
        ResponseEntity<Map<String, String>> response = handler.handleGenericException(new IllegalStateException("gateway offline"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("gateway offline", response.getBody().get("message"));
    }

    @SuppressWarnings("unused")
    private static void sampleMethod(Object ignored) {
    }
}
