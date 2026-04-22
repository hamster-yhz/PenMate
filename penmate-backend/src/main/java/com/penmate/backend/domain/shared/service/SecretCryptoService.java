package com.penmate.backend.domain.shared.service;

public interface SecretCryptoService {

    String encrypt(String plainText);

    String decrypt(String cipherText);
}

