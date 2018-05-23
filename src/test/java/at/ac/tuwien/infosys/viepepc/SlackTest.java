package at.ac.tuwien.infosys.viepepc;

import lombok.extern.slf4j.Slf4j;
import net.gpedro.integrations.slack.SlackApi;
import net.gpedro.integrations.slack.SlackMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = {
        "classpath:container-config/container.properties",
        "classpath:cloud-config/viepep4.0.properties",
        "classpath:messagebus-config/messagebus.properties",
        "classpath:database-config/mysql.properties",
        "classpath:application.properties",
        "classpath:application-heuristic.properties",
        "classpath:application-container.properties",
        "classpath:slack-config/slack.properties"})
@ActiveProfiles("test")

//@TestPropertySource(locations = {"classpath:slack-config/slack.properties"})

@Slf4j
public class SlackTest {

    @Value("${slack.webhook}")
    private String webhook;

    @Test
    public void sendSlackMessage() throws Exception {

        SlackApi api = new SlackApi(webhook);
        api.call(new SlackMessage("test message"));

    }

    @Test
    public void sendErrorMessageViaSlackMessage() throws Exception {

        log.info("Info");
        log.debug("Debug");
        log.error("Error");

    }
}
