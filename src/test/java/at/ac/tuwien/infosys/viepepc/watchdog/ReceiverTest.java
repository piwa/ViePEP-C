package at.ac.tuwien.infosys.viepepc.watchdog;

import at.ac.tuwien.infosys.viepepc.configuration.MessagingConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = {
        "classpath:container-config/container.properties",
        "classpath:cloud-config/viepep4.0.properties",
        "classpath:database-config/mysql.properties",
        "classpath:application.properties",
        "classpath:messagebus-config/messagebus.properties",
        "classpath:application-container.properties"})
@ActiveProfiles("test")
@Slf4j
public class ReceiverTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${messagebus.queue.name}")
    private String queueName;


    @Test
    public void sendMessage() throws Exception {

        log.info("Sending a result message.");
        rabbitTemplate.convertAndSend(queueName, new Message());


    }

}