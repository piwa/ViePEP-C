package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ContainerConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 13/06/16. Edited by Gerta Sheganaku
 */
@Component
public class ContainerConfigurationDaoService {

    @Autowired
    private ContainerConfigurationRepository containerConfigurationRepository;

    public ContainerConfiguration save(ContainerConfiguration containerConfiguration) {
        return containerConfigurationRepository.save(containerConfiguration);
    }

    public ContainerConfiguration getContainerConfiguration(ContainerConfiguration containerConfiguration) {
        if(containerConfiguration.getId() != null) {
            return containerConfigurationRepository.findOne(containerConfiguration.getId());
        }
        return null;
    }
}
