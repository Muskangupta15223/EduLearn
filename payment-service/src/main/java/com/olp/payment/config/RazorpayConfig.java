package com.olp.payment.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class RazorpayConfig {

    private static final Logger log = LoggerFactory.getLogger(RazorpayConfig.class);

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    @Bean
    public RazorpayClient razorpayClient() throws RazorpayException {
        boolean keyOk = keyId != null && !keyId.isBlank();
        boolean secretOk = keySecret != null && !keySecret.isBlank();
        if (keyOk && secretOk) {
            log.info("Razorpay configured with key: {}***", keyId.substring(0, Math.min(8, keyId.length())));
        } else {
            log.warn("Razorpay credentials are missing or using placeholder values; payment gateway features will stay unavailable until env vars are configured");
        }
        return new RazorpayClient(keyId, keySecret);
    }
}
