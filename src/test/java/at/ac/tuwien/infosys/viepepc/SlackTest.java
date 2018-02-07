package at.ac.tuwien.infosys.viepepc;

import lombok.extern.slf4j.Slf4j;
import net.gpedro.integrations.slack.SlackApi;
import net.gpedro.integrations.slack.SlackMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
//@SpringBootTest
@TestPropertySource(locations = {
        "classpath:slack-config/slack.properties"})
//@ActiveProfiles("test")
@Slf4j
public class SlackTest {

    @Value("${slack.webhook}")
    private String webhook;

    @Test
    public void sendSlackMessage() throws Exception {

        SlackApi api = new SlackApi(webhook);
        api.call(new SlackMessage("test message"));

    }
}
