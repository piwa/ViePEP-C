package at.ac.tuwien.infosys.viepepc.database.inmemory.services;

import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.library.registry.ServiceRegistryReader;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerConfigurationNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
    
    public List<ContainerConfiguration> getContainerConfigurations(ServiceType serviceType) {
        List<ContainerConfiguration> returnList = new ArrayList<>();

        for(ContainerConfiguration containerConfiguration : inMemoryCache.getContainerConfigurations()) {
            if (serviceType.getServiceTypeResources().getCpuLoad() <= containerConfiguration.getCPUPoints() && serviceType.getServiceTypeResources().getMemory() <= containerConfiguration.getRam()) {
                returnList.add(containerConfiguration);
            }
        }

        returnList.sort((config1, config2) -> (Double.compare(config1.getCores(), config2.getCores())));

        return returnList;
    }

    public ContainerConfiguration getBestContainerConfigurations(double requiredCpuLoad, double requiredRamLoad) throws ContainerConfigurationNotFoundException {
        List<ContainerConfiguration> allPossibleConfigs = getAllPossibleContainerConfigurations(requiredCpuLoad, requiredRamLoad);
        ContainerConfiguration containerConfiguration = null;
        for (ContainerConfiguration tempContainerConfig : allPossibleConfigs) {
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

        return containerConfiguration;
    }

    public List<ContainerConfiguration> getAllPossibleContainerConfigurations(double requiredCpuLoad, double requiredRamLoad) {
        List<ContainerConfiguration> returnList = new ArrayList<>();

        for(ContainerConfiguration containerConfiguration : inMemoryCache.getContainerConfigurations()) {
            if (requiredCpuLoad <= containerConfiguration.getCPUPoints() && requiredRamLoad <= containerConfiguration.getRam()) {
                returnList.add(containerConfiguration);
            }
        }

        returnList.sort((config1, config2) -> (Double.compare(config1.getCores(), config2.getCores())));

        return returnList;
    }

}
