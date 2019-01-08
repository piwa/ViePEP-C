package at.ac.tuwien.infosys.viepepc.database.inmemory.services;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.library.registry.ServiceRegistryReader;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerConfigurationNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by philippwaibel on 13/06/16. edited by Gerta Sheganaku
 */
@Component
public class CacheContainerService {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;
    @Autowired
    private ServiceRegistryReader serviceRegistryReader;

    @Value("${docker.repo.name}")
    private String repoName;
    @Value("${docker.image.name}")
    private String imageNamePrefix;

    private Integer serviceTypeAmount = 10; // how many docker images (mapping one service types)
    private Integer containerConfigurationAmount = 4; //different configurations per Image/Service Type
    
    public void initializeDockerContainers() {

        serviceTypeAmount = serviceRegistryReader.getServiceTypeAmount();
        containerConfigurationAmount = inMemoryCache.getContainerConfigurations().size();
    }

    public List<Container> getDeployedContainers() {
        return inMemoryCache.getContainerInstances().stream().filter(container -> container.getContainerStatus().equals(ContainerStatus.DEPLOYED)).collect(Collectors.toList());
    }

    public List<Container> getDeployingContainers() {
        return inMemoryCache.getContainerInstances().stream().filter(container -> container.getContainerStatus().equals(VirtualMachineStatus.DEPLOYING)).collect(Collectors.toList());
    }

    public List<Container> getDeployingAndDeployedContainers() {
        List<Container> returnSet = getDeployedContainers();
        returnSet.addAll(getDeployingContainers());
        return returnSet;
    }

    public void addRunningContainer(Container container) {
        inMemoryCache.getContainerInstances().add(container);
    }

    public Set<Container> getAllContainerInstances() {
        return inMemoryCache.getContainerInstances();
    }



}
