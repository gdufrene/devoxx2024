package fr.axa.demo.kafka;

import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@SpringBootApplication
@EnableScheduling
public class KafkaCipherApplication {
	
	public static final String TOPIC_NAME = "new-user-event";
	
	private final Logger log = LoggerFactory.getLogger(KafkaCipherApplication.class);

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

    @Bean
    public ConsumerFactory<String, UserData> consumerFactory(KafkaProperties kafkaProperties, SslBundles sslBubles) throws NoSuchAlgorithmException {
    	log.info("create consumer factory for UserData.");
    	StringDeserializer keyDeserializer = new StringDeserializer();
    	SecuredObjectDeserializer<UserData> valueDeserializer = new SecuredObjectDeserializer<UserData>(UserData.class);
		return new DefaultKafkaConsumerFactory<String, UserData>(kafkaProperties.buildConsumerProperties(sslBubles),
				keyDeserializer, valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserData> kafkaListenerContainerFactory(ConsumerFactory<String, UserData> consumer) {
        ConcurrentKafkaListenerContainerFactory<String, UserData> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumer);
        return factory;
    }

    // Used by RestClient
    @Bean
    public ObjectMapper mapper() {
        return new ObjectMapper()
            .registerModule( new JavaTimeModule() );
    }

}

@Component
class NewUserGenerator {
	private final Logger log = LoggerFactory.getLogger(NewUserGenerator.class);

	@Autowired KafkaTemplate<String, UserData> kafka;

	@Scheduled(fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
	public void generateUser() {
		UserData user = new UserData(
			UUID.randomUUID().toString(),
			"test.username@mailtest.com",
			"test",
			"username",
			"+33633663366"
		);

		kafka.send(KafkaCipherApplication.TOPIC_NAME, user);
		log.debug(">> New user sent id:{}", user.id());
	}
}

@Component
class NewUserHandler {
	private final Logger log = LoggerFactory.getLogger(NewUserHandler.class);
	
    @KafkaListener(id = "myId", topics = KafkaCipherApplication.TOPIC_NAME)
    public void listen(@Payload UserData user) {
		log.debug("<< Received user id:{} \n{}", user.id(), user);
    }
}

