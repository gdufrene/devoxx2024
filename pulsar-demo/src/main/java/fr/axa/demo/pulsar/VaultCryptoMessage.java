package fr.axa.demo.pulsar;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.apache.pulsar.client.api.CryptoKeyReader;
import org.apache.pulsar.client.api.MessageCrypto;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.PulsarClientException.CryptoException;
import org.apache.pulsar.common.api.proto.EncryptionKeys;
import org.apache.pulsar.common.api.proto.KeyValue;
import org.apache.pulsar.common.api.proto.MessageMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VaultCryptoMessage implements MessageCrypto<MessageMetadata, MessageMetadata> {

	private final Logger log = LoggerFactory.getLogger(VaultCryptoMessage.class);

	private KeyManager keyManager;
	private SecureRandom rand;

	public VaultCryptoMessage() throws NoSuchAlgorithmException {
		this.rand = SecureRandom.getInstanceStrong();
		this.keyManager = KeyManager.getInstance();
	}

	@Override
	public void addPublicKeyCipher(Set<String> keyNames, CryptoKeyReader keyReader) throws CryptoException {
		 // This CryptoMessage only use ONE remote key, CryptoKeyReader not used.
	}

	@Override
	public void encrypt(Set<String> encKeys, CryptoKeyReader keyReader,
			Supplier<MessageMetadata> messageMetadataBuilderSupplier, ByteBuffer payload, ByteBuffer outBuffer)
			throws PulsarClientException {
	
		SessionKey sessionKey = keyManager.current();
		log.trace("encrypt {} bytes with key {} / wrapKeyId {}", payload.remaining(), 
				sessionKey.sessionKeyId(), sessionKey.wrapKeyId());
	
		MessageMetadata meta = messageMetadataBuilderSupplier.get();
		meta.clearEncryptionKeys();
	
		meta.addEncryptionKey()
			.setKey(sessionKey.sessionKeyId())
			.setValue(sessionKey.wrapped());
	
		meta.addProperty()
			.setKey("wrapKeyId")
			.setValue(sessionKey.wrapKeyId());
	
		byte[] iv = new byte[16];
		rand.nextBytes(iv);
		meta.setEncryptionParam(iv);
		log.trace("IV set to  {}", Base64.getEncoder().encodeToString(iv));
	
		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			cipher.init(Cipher.ENCRYPT_MODE, sessionKey.cipherKey(), ivSpec);

	        int maxLength = cipher.getOutputSize(payload.remaining());
	        if (outBuffer.remaining() < maxLength) {
	            throw new IllegalArgumentException("Outbuffer has not enough space available");
	        }

	        int bytesStored = cipher.doFinal(payload, outBuffer);
	        outBuffer.flip();
	        outBuffer.limit(bytesStored);
	        log.trace("encrypted size: {} bytes", bytesStored);
		} catch(GeneralSecurityException e) {
			log.error("Failed to encrypt message. {}", e);
	        throw new PulsarClientException.CryptoException(e.getMessage());
		}
	
	}

	@Override
	public boolean decrypt(Supplier<MessageMetadata> messageMetadataSupplier, ByteBuffer payload, ByteBuffer outBuffer,
			CryptoKeyReader keyReader) {
		log.trace("decrypt {} bytes", payload.remaining());

		MessageMetadata meta = messageMetadataSupplier.get();
		EncryptionKeys keyInfo = meta.getEncryptionKeyAt(0);

		Optional<String> wrapKeyProperty = meta.getPropertiesList()
				.stream()
				.filter( kv -> kv.getKey().equals("wrapKeyId") )
				.map( KeyValue::getValue )
				.findFirst();
		if ( wrapKeyProperty.isEmpty() ) {
			log.warn("No wrapKeyId found in event");
			return false;
		}
		String wrapKeyId = wrapKeyProperty.get();

		SecretKey secretKey = keyManager.unwrap(keyInfo.getValue(), keyInfo.getKey(), wrapKeyId);

		byte[] iv = meta.getEncryptionParam();
		log.trace("IV received is  {}", Base64.getEncoder().encodeToString(iv));

		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

            int maxLength = cipher.getOutputSize(payload.remaining());
            if (outBuffer.remaining() < maxLength) {
                throw new IllegalArgumentException("Target buffer size is too small");
            }
            int decryptedSize = cipher.doFinal(payload, outBuffer);
            outBuffer.flip();
            outBuffer.limit(decryptedSize);
            log.trace("decrypted size: {} bytes", decryptedSize);
            return true;
		} catch(GeneralSecurityException e) {
			log.warn("Unable to decrypt payload", e);
			return false;
		}
	}

	@Override
	public int getMaxOutputSize(int inputLen) {
		return Math.max(128, (inputLen / 128) * 128 + 128);
	}

	@Override
	public boolean removeKeyCipher(String keyName) {
		return true;
	}

}
