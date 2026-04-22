package com.penmate.backend.infrastructure.security;

import com.penmate.backend.domain.shared.service.SecretCryptoService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesGcmSecretCryptoService implements SecretCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_SIZE = 12;
    private static final int TAG_BITS = 128;

    @Value("${penmate.security.model-key-encryption-key-base64:MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=}")
    private String encryptionKeyBase64;

    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKeySpec keySpec;

    @PostConstruct
    void init() {
        byte[] key = Base64.getDecoder().decode(encryptionKeyBase64);
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalStateException("Invalid AES key length for model key encryption");
        }
        keySpec = new SecretKeySpec(key, "AES");
    }

    @Override
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_SIZE];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(1 + NONCE_SIZE + encrypted.length);
            buffer.put((byte) 1);
            buffer.put(nonce);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("Encrypt model key failed", ex);
        }
    }

    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            byte[] data = Base64.getDecoder().decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte version = buffer.get();
            if (version != 1) {
                throw new IllegalStateException("Unsupported cipher version");
            }
            byte[] nonce = new byte[NONCE_SIZE];
            buffer.get(nonce);
            byte[] cipherBytes = new byte[buffer.remaining()];
            buffer.get(cipherBytes);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Decrypt model key failed", ex);
        }
    }
}

