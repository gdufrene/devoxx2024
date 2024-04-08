package fr.axa.demo.kafka;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.kafka.support.JacksonUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SecuredObjectSerializer implements Serializer<Object> {
	
	private ObjectMapper mapper;
	private KeyManager keyManager;
	private SecureRandom random;
	
	public SecuredObjectSerializer() throws NoSuchAlgorithmException {
		this.mapper = JacksonUtils.enhancedObjectMapper();
		this.random = SecureRandom.getInstanceStrong();
		this.keyManager = KeyManager.getInstance();
	}
	
	@Override
	public void configure(Map<String, ?> configs, boolean isKey) {
	}
	
	@Override
	public byte[] serialize(String topic, Object data) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

		try (DataOutputStream stream = new DataOutputStream(bos)) {
			SessionKey key = keyManager.current();
			stream.writeShort(key.sessionKeyId().length());
			stream.write(key.sessionKeyId().getBytes());

			stream.writeShort(key.wrapKeyId().length());
			stream.write(key.wrapKeyId().getBytes());

			stream.writeShort(key.wrapped().length);
			stream.write(key.wrapped());

			byte[] iv = new byte[16];
			random.nextBytes(iv);
			stream.write(iv);

			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			cipher.init(Cipher.ENCRYPT_MODE, key.cipherKey(), ivSpec);

			try (CipherOutputStream cos = new CipherOutputStream(stream, cipher);
					GZIPOutputStream zip = new GZIPOutputStream(cos)) {
				mapper.writeValue(zip, data);
			}
		} catch(Exception e) {
			throw new SerializationException("Unable to serialize data to "+topic, e);
		}
		
		return bos.toByteArray();
	}

}
