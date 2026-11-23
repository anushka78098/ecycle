package com.ecycle.backend.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Typed access to environment-driven settings. No secrets are hard-coded anywhere. */
@Component @Getter
public class AppProps {
    @Value("${app.jwt.secret:}") private String jwtSecret;
    @Value("${app.jwt.expiry-minutes:480}") private long jwtExpiryMinutes;
    @Value("${app.storage-dir:./data/uploads}") private String storageDir;
    @Value("${app.dev-mode:false}") private boolean devMode;
    @Value("${app.seed-demo:false}") private boolean seedDemo;
    @Value("${app.admin.email:admin@ecycle.local}") private String adminEmail;
    @Value("${app.admin.password:}") private String adminPassword;
    @Value("${app.google.client-id:}") private String googleClientId;
    @Value("${app.mail.enabled:false}") private boolean mailEnabled;
    @Value("${app.mail.from:no-reply@ecycle.local}") private String mailFrom;
    @Value("${app.payment.provider:mock}") private String paymentProvider;
    @Value("${app.payment.razorpay.key-id:}") private String razorpayKeyId;
    @Value("${app.payment.razorpay.key-secret:}") private String razorpayKeySecret;
    @Value("${app.rate-limit.per-minute:20}") private int rateLimitPerMinute;
    // ---- certificates, public links, pickups
    @Value("${app.certificate.signing-secret:}") private String certificateSigningSecret;
    @Value("${app.public-base-url:http://localhost:8080}") private String publicBaseUrl;
    @Value("${app.sanitization.require-checklist:false}") private boolean requireChecklist;
    // ---- AI eco-assistant (LangChain4j; Google Gemini by default via its OpenAI-compatible endpoint; OpenAI or a local Ollama / LM Studio server also work)
    @Value("${app.assistant.provider:auto}") private String assistantProvider;
    @Value("${app.assistant.api-key:}") private String assistantApiKey;
    @Value("${app.assistant.base-url:}") private String assistantBaseUrl;
    @Value("${app.assistant.model:}") private String assistantModel;
    @Value("${app.assistant.temperature:0.2}") private double assistantTemperature;
    @Value("${app.assistant.timeout-seconds:60}") private int assistantTimeoutSeconds;
    @Value("${app.assistant.memory-messages:20}") private int assistantMemoryMessages;
    @Value("${app.assistant.per-minute:15}") private int assistantPerMinute;
}
