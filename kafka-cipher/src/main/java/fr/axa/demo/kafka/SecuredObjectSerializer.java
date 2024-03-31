package fr.axa.demo.kafka;

import java.io.ByteArrayOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.kafka.support.JacksonUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SecuredObjectSerializer implements Serializer<Object> {
	
	/**
	 * Kafka config property for cipher operations.
	 */
	public static final String SECRET_CONFIG = "secret.key";
	
	private byte[] secret;
	private SecureRandom random;
	private ObjectMapper mapper;
	
	public SecuredObjectSerializer() throws NoSuchAlgorithmException {
		this.random = SecureRandom.getInstanceStrong();
		this.mapper = JacksonUtils.enhancedObjectMapper();
	}
	
	@Override
	public void configure(Map<String, ?> configs, boolean isKey) {
		if (configs.containsKey(SECRET_CONFIG)) {
			Object config = configs.get(SECRET_CONFIG);
			if (config instanceof String str) {
				this.secret = Base64.getDecoder().decode(str);
			}
			else {
				throw new IllegalStateException(SECRET_CONFIG + " must be String");
			}
		}
	}
	
	@Override
	public byte[] serialize(String topic, Object data) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

		try {
			byte[] iv = new byte[16];
			random.nextBytes(iv);
			bos.writeBytes(iv);
			
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			SecretKeySpec secretKeySpec = new SecretKeySpec(secret, "AES");
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);
			
			try (CipherOutputStream cos = new CipherOutputStream(bos, cipher);
					GZIPOutputStream zip = new GZIPOutputStream(cos)) {
				mapper.writeValue(zip, data);
			}
		} catch(Exception e) {
			throw new SerializationException("Unable to serialize data to "+topic, e);
		}
		
		return bos.toByteArray();
	}

}
