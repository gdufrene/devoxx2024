package fr.axa.demo.kafka;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SecuredObjectDeserializer<T> implements Deserializer<T> {
	
	private ObjectMapper mapper;
	private JavaType javaType;
	private KeyManager keyManager;
	
	public SecuredObjectDeserializer(Class<T> type) throws NoSuchAlgorithmException {
		this.mapper = JacksonUtils.enhancedObjectMapper();
		this.javaType = mapper.constructType(type);
		this.keyManager = KeyManager.getInstance();
	}
	
	@Override
	public void configure(Map<String, ?> configs, boolean isKey) {
	}
	
	@Override
	public T deserialize(String topic, byte[] data) {
		ByteArrayInputStream bis = new ByteArrayInputStream(data);
		
		try (DataInputStream stream = new DataInputStream(bis)) {
			int len = stream.readShort();
			String sessionKid = new String(stream.readNBytes(len));

			len = stream.readShort();
			String wrapKeyId = new String(stream.readNBytes(len));

			len = stream.readShort();
			byte[] wrapped = stream.readNBytes(len);

			byte[] iv = new byte[16];
			int read = stream.read(iv);
			Assert.state(read == 16, "event should start with 16 bytes of Init Vector");

			SecretKey secretKey = keyManager.unwrap(wrapped, sessionKid, wrapKeyId);

			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

			try (CipherInputStream cis = new CipherInputStream(stream, cipher);
					GZIPInputStream zip = new GZIPInputStream(cis)) {
				return mapper.readValue(zip, javaType);
			}
		} catch(IOException | GeneralSecurityException e) {
			throw new DeserializationException(topic, data, false, e);
		}
	}

}
