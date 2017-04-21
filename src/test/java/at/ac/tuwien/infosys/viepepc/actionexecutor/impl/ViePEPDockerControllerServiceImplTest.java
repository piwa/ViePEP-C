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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    private ViePEPAwsClientService viePEPAwsClientService;
    @Autowired
    private ViePEPOpenStackClientService viePEPOpenStackClientService;
    @Autowired
    private CacheVirtualMachineService virtualMachineService;
    @Autowired
    private VmTypesReaderImpl vmTypesReader;
    @Autowired
    private ContainerConfigurationsReader containerConfigurationsReader;
    @Autowired
    private ContainerImageRegistryReader containerImageRegistryReader;
    @Autowired
    private ViePEPDockerControllerService viePEPDockerControllerService;
    @Autowired
    private CacheContainerService cacheContainerService;
    @Autowired
    private ServiceRegistryReader serviceRegistryReader;


    private VirtualMachine vm;

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

        if(vm != null) {
            if(vm.getResourcepool().equals("aws")) {
                viePEPAwsClientService.stopVirtualMachine(vm);
            }
            else {
                viePEPOpenStackClientService.stopVirtualMachine(vm);
            }
        }

    }

    @Test
    public void startAndStopAContainerOnOpenStack_success() throws Exception {

        vmTypesReader.readVMTypes();
        containerConfigurationsReader.readContainerConfigurations();
        serviceRegistryReader.getServiceTypeAmount();


        VMType vmType = virtualMachineService.getVmTypeFromIdentifier(3);
        vm = new VirtualMachine("test", vmType);
        vm = viePEPOpenStackClientService.startVM(vm);

        startAndStopContainer();
    }

    @Test
    public void startAndStopAContainerOnAWS_success() throws Exception {

        vmTypesReader.readVMTypes();
        containerConfigurationsReader.readContainerConfigurations();
        serviceRegistryReader.getServiceTypeAmount();

        VMType vmType = virtualMachineService.getVmTypeFromIdentifier(4);
        vm = new VirtualMachine("test", vmType);
        vm = viePEPAwsClientService.startVM(vm);

        startAndStopContainer();
    }

    private void startAndStopContainer() throws ServiceTypeNotFoundException, ContainerImageNotFoundException, ContainerConfigurationNotFoundException, DockerException, InterruptedException {
        ServiceType serviceType = serviceRegistryReader.findServiceType("HelloWorldService");
        Container container = getContainer(serviceType);

        container = viePEPDockerControllerService.startContainer(vm, container);

        assertThat(Integer.valueOf(container.getExternPort())).isBetween(20000, 20300);

        TimeUnit.SECONDS.sleep(10);
        RestTemplate restTemplate = new RestTemplate();
        String response = restTemplate.getForObject("http://" + vm.getIpAddress() + ":" + container.getExternPort(), String.class);
        assertThat(response).isEqualTo("Hello World!");

        viePEPDockerControllerService.removeContainer(container);

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