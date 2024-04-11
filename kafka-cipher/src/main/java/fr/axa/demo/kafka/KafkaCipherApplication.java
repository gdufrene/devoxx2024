package fr.axa.demo.kafka;

import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@SpringBootApplication
@EnableScheduling
public class KafkaCipherApplication {
	
	public static final String TOPIC_NAME = "hello-devoxx"; 

	public static void main(String[] args) {
		SpringApplication.run(KafkaCipherApplication.class, args);
	}

    @Bean
    public NewTopic topic() {
        return TopicBuilder.name(TOPIC_NAME)
                .partitions(3)
                .replicas(1)
                .build();
    }

}

@Component
class EventGenerator {
	private final Logger log = LoggerFactory.getLogger(EventGenerator.class);

	@Autowired
	KafkaTemplate<String, byte[]> kafka;

	private byte[] secretKey;

	@Autowired
	public void setBase64Key(@Value("${secret.key}") String value) {
		secretKey = Base64.getDecoder().decode(value);
	}

	@Scheduled(fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
	public void generateText() throws GeneralSecurityException {
		String event = "Hi devoxx 2024 !";
		for (int i = 0; i < 4; i++) {
			event += event;
		}

		Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
		SecretKeySpec key = new SecretKeySpec(secretKey, "AES");
		cipher.init(Cipher.ENCRYPT_MODE, key);
		byte[] data = cipher.doFinal(event.getBytes());

		kafka.send(KafkaCipherApplication.TOPIC_NAME, data);
		log.debug("sent: {}", event);
	}
}

@Component
class EventHandler {
	private final Logger log = LoggerFactory.getLogger(EventHandler.class);

	private byte[] secretKey;

	@Autowired
	public void setBase64Key(@Value("${secret.key}") String value) {
		secretKey = Base64.getDecoder().decode(value);
	}

    @KafkaListener(id = "myId", topics = KafkaCipherApplication.TOPIC_NAME)
    public void listen(byte[] event) throws GeneralSecurityException {

		Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
		SecretKeySpec key = new SecretKeySpec(secretKey, "AES");
		cipher.init(Cipher.DECRYPT_MODE, key);
		byte[] data = cipher.doFinal(event);

		log.debug("Received: {}", new String(data));
    }
}

