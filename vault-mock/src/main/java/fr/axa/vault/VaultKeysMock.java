package fr.axa.vault;

import java.io.FileNotFoundException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.MGF1ParameterSpec;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableScheduling
public class VaultKeysMock {

	public static void main(String[] args) {
		SpringApplication.run(VaultKeysMock.class, args);
	}

}

record CipherData(String data) {}

record KeyInfo(String id, OffsetDateTime generated) {}

@RestController
@RequestMapping("/keys")
class KeyController {

	private final Logger log = LoggerFactory.getLogger(KeyController.class);

	private Map<Integer, KeyPair> keys = new ConcurrentHashMap<>();
	private AtomicReference<KeyInfo> currentKey = new AtomicReference<>();
	private AtomicInteger serial = new AtomicInteger(1);

	private KeyPairGenerator kpg;

	public KeyController() throws NoSuchAlgorithmException {
		kpg = KeyPairGenerator.getInstance("RSA");
		kpg.initialize(2048);
	}

	@Scheduled(fixedDelay = 20, timeUnit = TimeUnit.SECONDS)
	public void keyRotate() throws NoSuchAlgorithmException {
		KeyPair kp = kpg.generateKeyPair();
		Integer newId = serial.getAndIncrement();
		keys.put(newId, kp);
		currentKey.set(new KeyInfo(newId.toString(), OffsetDateTime.now()));
		log.info("🔑 New keypair generated with id {}", newId);
	}

	@GetMapping("/current")
	public KeyInfo current() {
		return currentKey.get();
	}

	@PostMapping("/{kid}/encrypt")
	public CipherData cipher(@PathVariable Integer kid, @RequestBody CipherData operation) 
			throws FileNotFoundException, GeneralSecurityException {
		return crypto(kid, Cipher.ENCRYPT_MODE, operation);
	}

	@PostMapping("/{kid}/decrypt")
	public CipherData uncipher(@PathVariable Integer kid, @RequestBody CipherData operation)
			throws FileNotFoundException, GeneralSecurityException {
		return crypto(kid, Cipher.DECRYPT_MODE, operation);
	}

	private CipherData crypto(Integer kid, int mode, CipherData operation) 
			throws FileNotFoundException, GeneralSecurityException {
		long start = System.currentTimeMillis();
		KeyPair kp = Optional.ofNullable( keys.get(kid) )
			.orElseThrow( () -> new FileNotFoundException("Key="+kid) );

		Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
		OAEPParameterSpec oaepParams = new OAEPParameterSpec("SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);
		cipher.init(mode, mode == Cipher.ENCRYPT_MODE ? kp.getPublic() : kp.getPrivate(), oaepParams);
		byte[] data = Base64.getDecoder().decode(operation.data());
		byte[] res = cipher.doFinal( data );
		log.info("🚩 {} 📦 {} bytes in ⏰ {}ms", 
				mode == Cipher.ENCRYPT_MODE ? "Encrypt" : "Decrypt",
				data.length, 
				System.currentTimeMillis() - start);
		return new CipherData( Base64.getEncoder().encodeToString(res) );
	}

}


