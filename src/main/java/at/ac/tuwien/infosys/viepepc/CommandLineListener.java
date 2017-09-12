package at.ac.tuwien.infosys.viepepc;

import at.ac.tuwien.infosys.viepepc.reasoner.ReasoningActivator;
import lombok.extern.slf4j.Slf4j;
import net.gpedro.integrations.slack.SlackApi;
import net.gpedro.integrations.slack.SlackMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Scanner;
import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 20/06/16. edited by gerta sheganaku
 */
@Component
@Slf4j
@Profile("!test")
public class CommandLineListener implements CommandLineRunner {

    @Autowired
    private ReasoningActivator reasoningActivator;

    @Value("${slack.webhook}")
    private String slackWebhook;
    @Value("${spring.profiles.active}")
    private String activeProfile;

    @Value("${simulate}")
    private boolean simulate;
    @Value("${autostart}")
    private boolean autostart;

    public void run(String... args) {
        log.info("Starting ViePEP 4.0...");

        try {
            Scanner scanner = new Scanner(System.in);

            boolean running = true;
            boolean started = false;
            String input = "";

            started = true;

            if(!simulate) {
//                viePEPOpenstackClientService.initialize();
//                viePEPAwsClientService.initialize();
            }

            reasoningActivator.initialize();

            if(autostart) {
                startReasoning();
            }
            else {
                while (running) {
                    log.info("-----------Enter 'start' to begin or 'stop' to end -------------");
                    input = scanner.nextLine();
                    while (!input.equalsIgnoreCase("start") && !input.equalsIgnoreCase("stop")) {
                        input = scanner.nextLine();
                    }
                    switch (input) {
                        case "start":
                            if (!started) {
                                started = true;
                                startReasoning();
                            }
                            break;
                        case "stop":
                            running = false;
                            reasoningActivator.stop();
                            break;
                    }
                }
            }

        } catch (Exception e) {
            log.error("EXCEPTION", e);
        } finally {
            log.info("Terminating....");

            SlackApi api = new SlackApi(slackWebhook);
            api.call(new SlackMessage("Evaluation of profile: " + activeProfile + " done!"));

            System.exit(1);
        }

    }


    private void startReasoning() {
        try {

            if(autostart) {
                Future<Boolean> reasoningDone = reasoningActivator.start();
                reasoningDone.get();                 // waits for result
//                while(!reasoningDone.isDone()) {
//                    Thread.sleep(10000);
//                }
            }
            else {
                reasoningActivator.start();
            }

        } catch (Exception e) {
            System.out.println("Could not start Reasoning Activator Database done \n " +
                    "-------------------------------------- ....");
            e.printStackTrace();

        }
    }

}

