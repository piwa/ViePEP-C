package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.registry.ServiceRegistryReader;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.service.ServiceTypeNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

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
public class OptimizationUtilityTest {

    @Autowired
    private ServiceRegistryReader serviceRegistryReader;
    @Autowired
    private OptimizationUtility optimizationUtility;

    @Test
    public void getContainerConfiguration_1_1() throws ServiceTypeNotFoundException, ContainerConfigurationNotFoundException, ContainerImageNotFoundException {

        ServiceType serviceType = serviceRegistryReader.findServiceType("Service1");
        List<Container> containerService1List = optimizationUtility.getContainer(serviceType, 4);

        assertThat(containerService1List.size()).isEqualTo(4);
        assertThat(containerService1List.get(0) == containerService1List.get(1)).isTrue();
        assertThat(containerService1List.get(1) == containerService1List.get(2)).isTrue();
        assertThat(containerService1List.get(2) == containerService1List.get(3)).isTrue();
    }

    @Test
    public void getContainerConfiguration_1_2() throws ServiceTypeNotFoundException, ContainerConfigurationNotFoundException, ContainerImageNotFoundException {

        ServiceType serviceType = serviceRegistryReader.findServiceType("Service1");
        List<Container> containerService1List = optimizationUtility.getContainer(serviceType, 5);

        assertThat(containerService1List.size()).isEqualTo(5);
        assertThat(containerService1List.get(0) == containerService1List.get(1)).isTrue();
        assertThat(containerService1List.get(1) == containerService1List.get(2)).isTrue();
        assertThat(containerService1List.get(2) == containerService1List.get(3)).isTrue();
    }

    @Test
    public void getContainerConfiguration_9_1() throws ServiceTypeNotFoundException, ContainerConfigurationNotFoundException, ContainerImageNotFoundException {

        ServiceType serviceType = serviceRegistryReader.findServiceType("Service9");
        List<Container> containerService9List = optimizationUtility.getContainer(serviceType, 4);

        assertThat(containerService9List.size()).isEqualTo(3);
        assertThat(containerService9List.get(0) == containerService9List.get(1)).isTrue();
        assertThat(containerService9List.get(1) != containerService9List.get(2)).isTrue();
        assertThat(containerService9List.get(2) == containerService9List.get(3)).isTrue();
    }

    @Test
    public void getContainerConfiguration_9_2() throws ServiceTypeNotFoundException, ContainerConfigurationNotFoundException, ContainerImageNotFoundException {
        ServiceType serviceType = serviceRegistryReader.findServiceType("Service9");
        List<Container> containerService9List_2 = optimizationUtility.getContainer(serviceType, 3);

        assertThat(containerService9List_2.size()).isEqualTo(3);
        assertThat(containerService9List_2.get(0) == containerService9List_2.get(1)).isTrue();
        assertThat(containerService9List_2.get(1) != containerService9List_2.get(2)).isTrue();
    }

    @Test
    public void getContainerConfiguration_10() throws ServiceTypeNotFoundException, ContainerConfigurationNotFoundException, ContainerImageNotFoundException {
        ServiceType serviceType = serviceRegistryReader.findServiceType("Service10");
        List<Container> containerService10List = optimizationUtility.getContainer(serviceType, 4);

        assertThat(containerService10List.size()).isEqualTo(4);
        assertThat(containerService10List.get(0) != containerService10List.get(1)).isTrue();
        assertThat(containerService10List.get(1) != containerService10List.get(2)).isTrue();
        assertThat(containerService10List.get(2) != containerService10List.get(3)).isTrue();

    }
}