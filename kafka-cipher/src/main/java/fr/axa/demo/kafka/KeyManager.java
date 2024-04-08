package fr.axa.demo.kafka;

import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;


public class KeyManager {
	
	private static KeyManager INSTANCE;
	private static final Logger log = LoggerFactory.getLogger(KeyManager.class);
	
	private KeyGenerator generator;
	private Map<String, SessionKey> keyCache;
	private Instant nextGeneration;
	private SessionKey current;
	private RestClient client;
	
	public KeyManager() throws NoSuchAlgorithmException {
		generator = KeyGenerator.getInstance("AES");
		generator.init(128);
		keyCache = new ConcurrentHashMap<>();
		nextGeneration = Instant.MIN;
		client = RestClient.builder()
			.baseUrl("http://localhost:8082")
			.build();
	}
	
	public SessionKey current() {
		Instant now = Instant.now();
		boolean renew = nextGeneration.isBefore(now);
		log.debug("renew current key : {}", renew);
		if (renew) {
			nextGeneration = now.plus(10, ChronoUnit.SECONDS);
			SecretKey nextKey = generator.generateKey();
			String sessionKid = UUID.randomUUID().toString();
			
			record KeyInfo(String id, OffsetDateTime generated) {}
			
			KeyInfo info = client.get()
				.uri("/keys/current")
				.retrieve()
				.body(KeyInfo.class);
			
			byte[] wrapped = wrap(nextKey, info.id());
			SessionKey sessionKey = new SessionKey(info.id(), sessionKid, wrapped, nextKey);
			// keyCache.put(sessionKid, sessionKey);
			current = sessionKey;
		}
		log.debug("current session key Wrap:{}, Session:{}", current.wrapKeyId(), current.sessionKeyId());
		return current;
	}
	
	public SecretKey unwrap(byte[] keyData, String sessionKid, String wrapKeyId) {
		SessionKey key = keyCache.get(sessionKid);
		if ( key != null ) {
			log.debug("unwrap cache HIT! for key {}", sessionKid);
			return key.cipherKey();
		}
		
		log.debug("unwrap cache MISS for key {}", sessionKid);
		byte[] unwrapped = callVault(wrapKeyId, "decrypt", keyData);
		SecretKey secretKey = new SecretKeySpec(unwrapped, "AES");
		key = new SessionKey(sessionKid, sessionKid, unwrapped, secretKey);
		keyCache.put(sessionKid, key);
		log.debug("unwrap key {} now in cache, {} items in cache", sessionKid, keyCache.size());
		return secretKey;
	}
	
	public byte[] wrap(SecretKey key, String kid) {
		return callVault(kid, "encrypt", key.getEncoded());
	}
	
	private byte[] callVault(String kid, String operation, byte[] data) {			
		record InOut(String data) {}
		
		InOut resp = client.post()
			.uri("/keys/{kid}/{operation}", kid, operation)
			.body( new InOut(Base64.getEncoder().encodeToString(data)) )
			.retrieve()
			.body( InOut.class );
		
		return Base64.getDecoder().decode(resp.data());
	}
	
	public static KeyManager getInstance() throws NoSuchAlgorithmException {
		if (INSTANCE == null) {
			INSTANCE = new KeyManager();
		}
		return INSTANCE;
	}

}

