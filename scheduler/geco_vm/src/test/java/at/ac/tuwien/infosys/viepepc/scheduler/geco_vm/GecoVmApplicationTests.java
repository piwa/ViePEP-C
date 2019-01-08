package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm;

import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.TestSchedulerGecoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Random;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {GeCoVmApplication.class, TestSchedulerGecoConfiguration.class},
        properties = {"evaluation.prefix=develop",
                "profile.specific.database.name=geco_vm",
                "evaluation.suffix=1",
                "min.optimization.interval.ms = 20000",
                "vm.simulation.deploy.duration.average=53819",
                "vm.simulation.deploy.duration.stddev=8504",
                "simulate = true",
                "container.imageNotAvailable.simulation.deploy.duration.average=0",
                "container.imageNotAvailable.simulation.deploy.duration.stddev=0",
                "container.imageAvailable.simulation.deploy.duration.average=0",
                "container.imageAvailable.simulation.deploy.duration.stddev=0",
                "slack.webhook="
        })
@ActiveProfiles({"test", "GeCo_VM", "VmAndContainer"})
@Slf4j
public class GecoVmApplicationTests {

    @Test
    public void contextLoads() {
        Random rand = new Random();
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));

        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));

        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));

        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));

        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));

        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));

        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));

        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));
        log.info("" + rand.nextInt(2));


    }

}

