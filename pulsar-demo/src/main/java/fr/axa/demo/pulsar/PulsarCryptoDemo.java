package fr.axa.demo.pulsar;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.CryptoKeyReader;
import org.apache.pulsar.client.api.MessageCrypto;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.impl.DefaultCryptoKeyReader;
import org.apache.pulsar.common.api.proto.MessageMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.pulsar.core.ConsumerBuilderCustomizer;
import org.springframework.pulsar.core.ProducerBuilderCustomizer;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@SpringBootApplication
@EnableScheduling
public class PulsarCryptoDemo {
	
	private final Logger log = LoggerFactory.getLogger(PulsarCryptoDemo.class);
	
	public static final String TOPIC_NAME = "new-user-event";

	public static void main(String[] args) {
		SpringApplication.run(PulsarCryptoDemo.class, args);
	}
	
	// Because we implement MessageCrypto as a remote service, CryptoKeyReader is not used. 
	// It is still required by Pulsar Client. 
	private CryptoKeyReader cryptoKeyReader = DefaultCryptoKeyReader.builder().build();
	
	@Bean
	ProducerBuilderCustomizer<UserData> customizeProducer(MessageCrypto<MessageMetadata, MessageMetadata> messageCrypto) {
		return (ProducerBuilder<UserData> builder) -> {
			log.debug("customizeProducer with {}", messageCrypto.getClass());
			builder.messageCrypto(messageCrypto);
			builder.cryptoKeyReader(cryptoKeyReader);
			builder.addEncryptionKey("toto");
		};
	}
	
	@Bean
	ConsumerBuilderCustomizer<UserData> customizeConsumer(MessageCrypto<MessageMetadata, MessageMetadata> messageCrypto) {
		return (ConsumerBuilder<UserData> builder) -> {
			log.debug("customizeConsumer with {}", messageCrypto.getClass());
			builder.messageCrypto(messageCrypto);
			builder.cryptoKeyReader(cryptoKeyReader);
		};
	}

}


@Component
class NewUserGenerator {
	private final Logger log = LoggerFactory.getLogger(NewUserGenerator.class);

	@Autowired
	PulsarTemplate<UserData> pulsar;

	@Scheduled(fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
	public void generateUser() {
		UserData user = new UserData(
			UUID.randomUUID().toString(),
			"test.username@mailtest.com",
			"test",
			"username",
			"+33633663366"
		);

		try {
			pulsar.send(PulsarCryptoDemo.TOPIC_NAME, user);
			log.debug("New user created and sent with ID {}", user.id());
		} catch (PulsarClientException e) {
			e.printStackTrace();
		}

	}
}

@Component
class NewUserHandler {
	private final Logger log = LoggerFactory.getLogger(NewUserHandler.class);
	
	@PulsarListener(subscriptionName = "user-subscription", topics = PulsarCryptoDemo.TOPIC_NAME)
    public void listen(@Payload UserData user) {
    	log.debug("A new user {} has been registered with email {}", user.id(), user.mail());
    }
}