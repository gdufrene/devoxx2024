package fr.axa.demo.kafka;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootApplication
@EnableScheduling
public class KafkaCipherApplication {
	
	public static final String TOPIC_NAME = "new-user-event"; 

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
    public ObjectMapper mapper() {
    	return new ObjectMapper();
    }

}

@Component
class NewUserGenerator {
	private final Logger log = LoggerFactory.getLogger(NewUserGenerator.class);

	@Autowired
	KafkaTemplate<String, String> kafka;
	
	@Autowired
	ObjectMapper mapper;

	@Scheduled(fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
	public void generateUser() throws JsonProcessingException, GeneralSecurityException, IOException {
		UserData user = new UserData(
			UUID.randomUUID().toString(),
			"test.username@mailtest.com",
			"test",
			"username",
			"+33633663366"
		);

		String json = mapper.writeValueAsString(user);
		String event = json;
		kafka.send(KafkaCipherApplication.TOPIC_NAME, event);

		log.debug("New user created and sent with ID {}", user.id());
	}
}

@Component
class NewUserHandler {
	private final Logger log = LoggerFactory.getLogger(NewUserHandler.class);

	@Autowired
	ObjectMapper mapper;
	
    @KafkaListener(id = "myId", topics = KafkaCipherApplication.TOPIC_NAME)
    public void listen(String event) throws JsonProcessingException, GeneralSecurityException, IOException {
		
		String json = event;
    	UserData user = mapper.readValue(json, UserData.class);
    	
    	log.debug("A new user {} has been registered with email {}", user.id(), user.mail());
    }
}

