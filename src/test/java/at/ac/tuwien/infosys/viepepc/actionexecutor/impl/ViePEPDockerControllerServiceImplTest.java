package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPOpenStackClientService;
import at.ac.tuwien.infosys.viepepc.bootstrap.containers.ContainerConfigurationsReaderImpl;
import at.ac.tuwien.infosys.viepepc.bootstrap.vmTypes.VmTypesReaderImpl;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.registry.ServiceRegistryReader;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

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
        "classpath:application-container.properties"})
@ActiveProfiles("test")
public class ViePEPDockerControllerServiceImplTest {

    @Autowired
    private ViePEPOpenStackClientService viePEPOpenStackClientService;
    @Autowired
    private CacheVirtualMachineService virtualMachineService;
    @Autowired
    private VmTypesReaderImpl vmTypesReader;
    @Autowired
    private ContainerConfigurationsReaderImpl containerConfigurationsReader;
    @Autowired
    private ContainerImageRegistryReader containerImageRegistryReader;
    @Autowired
    private ViePEPDockerControllerServiceImpl viePEPDockerControllerServiceImpl;
    @Autowired
    private CacheContainerService cacheContainerService;
    @Autowired
    private ServiceRegistryReader serviceRegistryReader;


    private VirtualMachine vm;

    @Before
    public void setUp() throws Exception {
        vmTypesReader.readVMTypes();
        containerConfigurationsReader.readContainerConfigurations();
        serviceRegistryReader.getServiceTypeAmount();


        VMType vmType = virtualMachineService.getVmTypeFromIdentifier(3);
        vm = new VirtualMachine("test", vmType);
        vm = viePEPOpenStackClientService.startVM(vm);
    }

    @After
    public void tearDown() throws Exception {

        if(vm != null) {
            viePEPOpenStackClientService.stopVirtualMachine(vm);
        }

    }

    @Test
    public void startContainer() throws Exception {

        ServiceType serviceType = serviceRegistryReader.findServiceType("HelloWorldService");
        Container container = getContainer(serviceType);

        container = viePEPDockerControllerServiceImpl.startContainer(vm, container);

        System.out.println(container.getExternPort());

        assertThat(Integer.valueOf(container.getExternPort())).isBetween(20000, 20300);

        TimeUnit.SECONDS.sleep(10);
        RestTemplate restTemplate = new RestTemplate();
        String response = restTemplate.getForObject("http://" + vm.getIpAddress() + ":" + container.getExternPort(), String.class);
        assertThat(response).isEqualTo("Hello World!");

        viePEPDockerControllerServiceImpl.removeContainer(container);

        Container finalContainer = container;
        assertThatThrownBy(() -> restTemplate.getForObject("http://" + vm.getIpAddress() + ":" + finalContainer.getExternPort(), String.class)).isInstanceOf(ResourceAccessException.class);

    }


    private Container getContainer(ServiceType serviceType) throws ContainerImageNotFoundException, ContainerConfigurationNotFoundException {
        ContainerConfiguration containerConfiguration = null;
        for (ContainerConfiguration tempContainerConfig : cacheContainerService.getContainerConfigurations(serviceType)) {
            if (containerConfiguration == null) {
                containerConfiguration = tempContainerConfig;
            }
            else if (containerConfiguration.getCPUPoints() > tempContainerConfig.getCPUPoints() || containerConfiguration.getRam() > tempContainerConfig.getRam()) {
                containerConfiguration = tempContainerConfig;
            }
        }
        if(containerConfiguration == null) {
            throw new ContainerConfigurationNotFoundException();
        }

        ContainerImage containerImage = containerImageRegistryReader.findContainerImage(serviceType);

        Container container = new Container();
        container.setContainerConfiguration(containerConfiguration);
        container.setContainerImage(containerImage);

        return container;
    }

}