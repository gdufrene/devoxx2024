package fr.axa.demo.pulsar;

import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Base64;
import java.util.HexFormat;
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

	private static final TemporalAmount KEY_DURATION = Duration.of(10, ChronoUnit.SECONDS);

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
		if (renew) {
			nextGeneration = now.plus(KEY_DURATION);
			SecretKey nextKey = generator.generateKey();
			String sessionKid = UUID.randomUUID().toString();
			log.debug("🔑 Renew session key \n id:{}\n value:{} ", sessionKid, 
					HexFormat.of().formatHex(nextKey.getEncoded()) );

			record KeyInfo(String id, OffsetDateTime generated) {}

			KeyInfo info = client.get()
				.uri("/keys/current")
				.retrieve()
				.body(KeyInfo.class);

			byte[] wrapped = wrap(nextKey, info.id());
			SessionKey sessionKey = new SessionKey(info.id(), sessionKid, wrapped, nextKey);
			log.debug("🔐 Wrapped session key with remote key ID[{}]\n value:{} ", info.id(), 
					HexFormat.of().formatHex(wrapped) );

			// would be a good idea in general case ... 
			// keyCache.put(sessionKid, sessionKey);
			current = sessionKey;
		}
		log.trace("used SessionKey wrapKeyId:{}, sessionKeyId:{}", current.wrapKeyId(), current.sessionKeyId());
		return current;
	}

	public SecretKey unwrap(byte[] keyData, String sessionKid, String wrapKeyId) {
		SessionKey key = keyCache.get(sessionKid);
		boolean cacheHit = key != null; 
		log.debug("🔎 search cache : {} for key {}", cacheHit ? "📗 HIT!" : "📕 MISS", sessionKid);
		if ( key != null ) {
			return key.cipherKey();
		}

		byte[] unwrapped = callVault(wrapKeyId, "decrypt", keyData);
		SecretKey secretKey = new SecretKeySpec(unwrapped, "AES");
		key = new SessionKey(wrapKeyId, sessionKid, unwrapped, secretKey);
		keyCache.put(sessionKid, key);
		log.debug("🔐 Unwrapped session key with remote key ID[{}]\n value:{} \n 📚 cache size: {}", wrapKeyId, 
				HexFormat.of().formatHex(unwrapped), keyCache.size() );
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

