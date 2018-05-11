package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPDockerControllerService;
import at.ac.tuwien.infosys.viepepc.bootstrap.containers.ContainerConfigurationsReader;
import at.ac.tuwien.infosys.viepepc.bootstrap.vmTypes.VmTypesReaderImpl;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.registry.ServiceRegistryReader;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.service.ServiceTypeNotFoundException;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StopWatch;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/**
 * Created by philippwaibel on 03/04/2017.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = {
        "classpath:container-config/container.properties",
        "classpath:cloud-config/viepep4.0.properties",
        "classpath:database-config/mysql.properties",
        "classpath:application.properties",
        "classpath:application-heuristic.properties",
        "classpath:application-container.properties"})
@ActiveProfiles("test")
@Slf4j
public class ViePEPAWSFargateServiceImplTest {

    @Autowired
    private ViePEPAWSFargateServiceImpl fargateService;

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {



    }

    @Test
    public void startAndStopAContainerOnFargate_success() throws Exception {
        Container container = new Container();
        container = fargateService.startContainer(container);

        fargateService.removeContainer(container);

    }



}