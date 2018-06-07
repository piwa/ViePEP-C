package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.bootstrap.containers.ContainerConfigurationsReader;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.registry.ServiceRegistryReader;
import at.ac.tuwien.infosys.viepepc.registry.impl.service.ServiceRegistry;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker.ServiceInvoker;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StopWatch;


/**
 * Created by philippwaibel on 03/04/2017.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = {
        "classpath:container-config/container.properties",
        "classpath:cloud-config/viepep4.0.properties",
        "classpath:messagebus-config/messagebus.properties",
        "classpath:database-config/mysql.properties",
        "classpath:application.properties",
        "classpath:application-heuristic.properties",
        "classpath:application-container.properties"})
@ActiveProfiles("test")
@Slf4j
public class ViePEPAzureContainerServiceImplTest {

    @Autowired
    private ViePEPAzureContainerServiceImpl azureContainerService;
    @Autowired
    private ServiceRegistryReader serviceRegistryReader;
    @Autowired
    private ContainerImageRegistryReader containerImageRegistryReader;
    @Autowired
    private ContainerConfigurationsReader containerConfigurationsReader;
    @Autowired
    private ServiceInvoker serviceInvoker;

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {



    }

    @Test
    public void startAndStopAContainerOnAzure_success() throws Exception {
        StopWatch stopWatch = new StopWatch();
        ProcessStep processStep = new ProcessStep();
        Container container = startAContainer(processStep,stopWatch);
//        azureContainerService.removeContainer(container);
    }

    @Test
    public void startInvokeAndStopAContainerOnAzure_success() throws Exception {


        startInvokeAndStopAContainer();
    }

    @Test
    public void startInvokeAndStopTwoContainersOnAzure_success() throws Exception {
        startInvokeAndStopAContainer();
        startInvokeAndStopAContainer();
    }

    private void startInvokeAndStopAContainer() throws Exception {

        StopWatch stopWatch = new StopWatch();

        ProcessStep processStep = new ProcessStep();
        Container container = startAContainer(processStep,stopWatch);

        stopWatch.stop();
        log.info("Container running. Duration=" +stopWatch.getTotalTimeMillis());

        stopWatch = new StopWatch();
        stopWatch.start();
        serviceInvoker.invoke(container, processStep);
        stopWatch.stop();

        log.info("Service invoked. Duration=" +stopWatch.getTotalTimeMillis());

        azureContainerService.removeContainer(container);
    }

    private Container startAContainer(ProcessStep processStep, StopWatch stopWatch) throws Exception {
        processStep.setServiceType(serviceRegistryReader.findServiceType("Service1"));
        processStep.setName("testprocessstep");

        stopWatch.start();
        Container container = new Container();
        ContainerConfiguration configuration = new ContainerConfiguration();
        configuration.setCores(2);
        configuration.setRam(2000);
        container.setContainerConfiguration(configuration);

        ContainerImage containerImage = containerImageRegistryReader.findContainerImage(processStep.getServiceType());
        container.setContainerImage(containerImage);

        container = azureContainerService.startContainer(container);
        return container;
    }


}