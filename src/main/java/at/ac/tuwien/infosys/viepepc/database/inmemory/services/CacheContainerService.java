package at.ac.tuwien.infosys.viepepc.database.inmemory.services;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.registry.ServiceRegistryReader;
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

        returnList.sort((config1, config2) -> (new Double(config1.getCores()).compareTo((new Double(config2.getCores())))));

        return returnList;
    }

}
