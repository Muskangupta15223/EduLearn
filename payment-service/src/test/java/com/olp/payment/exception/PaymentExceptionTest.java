package com.olp.payment.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentExceptionTest {

    @Test
    void exposesProvidedStatus() {
        PaymentException ex = new PaymentException("missing payment", HttpStatus.NOT_FOUND);

        assertEquals("missing payment", ex.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}
