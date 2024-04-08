package fr.axa.demo.kafka;

import javax.crypto.SecretKey;

public record SessionKey (String wrapKeyId, String sessionKeyId, byte[] wrapped, SecretKey cipherKey) {

}
