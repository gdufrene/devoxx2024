package fr.axa.demo.kafka;

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
	KafkaTemplate<String, String> kafka;

	@Scheduled(fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
	public void generateText() { 
		String event = "Hi devoxx 2024 !";
		kafka.send(KafkaCipherApplication.TOPIC_NAME, event);
		log.debug("sent: {}", event);
	}
}

@Component
class EventHandler {
	private final Logger log = LoggerFactory.getLogger(EventHandler.class);
	
    @KafkaListener(id = "myId", topics = KafkaCipherApplication.TOPIC_NAME)
    public void listen(String event) {
    	log.debug("Received: {}", event);
    }
}

