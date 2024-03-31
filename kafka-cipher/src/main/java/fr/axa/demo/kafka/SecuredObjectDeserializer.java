package fr.axa.demo.kafka;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SecuredObjectDeserializer<T> implements Deserializer<T> {
	
	/**
	 * Kafka config property for cipher operations.
	 */
	public static final String SECRET_CONFIG = "secret.key";
	
	private byte[] secret;
	private ObjectMapper mapper;
	private JavaType javaType;
	
	public SecuredObjectDeserializer(Class<T> type) {
		this.mapper = JacksonUtils.enhancedObjectMapper();
		this.javaType = mapper.constructType(type);
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
	public T deserialize(String topic, byte[] data) {
		
		ByteArrayInputStream bis = new ByteArrayInputStream(data);
		
		try {
	    	byte[] iv = new byte[16];
	    	int read = bis.read(iv);
	    	Assert.state(read == 16, "event should start with 16 bytes of Init Vector");
	    	
	    	IvParameterSpec ivSpec = new IvParameterSpec(iv);
	    	Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			SecretKeySpec secretKeySpec = new SecretKeySpec(secret, "AES");
			cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec);

			try (CipherInputStream cis = new CipherInputStream(bis, cipher);
					GZIPInputStream zip = new GZIPInputStream(cis)) {
				return mapper.readValue(zip, javaType);
			} 
		} catch(IOException | GeneralSecurityException e) {
			throw new DeserializationException(topic, data, false, e);
		}
	}

}
