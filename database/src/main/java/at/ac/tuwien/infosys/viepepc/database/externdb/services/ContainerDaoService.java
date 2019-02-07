package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ContainerConfigurationRepository;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ContainerImageRepository;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ContainerRepository;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.Optional;

/**
 * Created by philippwaibel on 13/06/16. edited by Gerta Sheganaku.
 */
@Component
@SuppressWarnings("Duplicates")
public class ContainerDaoService {

    @Autowired
    private ContainerRepository containerRepository;
    @Autowired
    private ContainerImageRepository containerImageRepository;
    @Autowired
    private ContainerConfigurationRepository containerConfigurationRepository;

    @Transactional
    public Container save(Container container) {

        ContainerImage containerImage = container.getContainerImage();
        containerImage.setServiceType(null);
        if(containerImage.getId() == null) {
            containerImageRepository.save(containerImage);
        } else {
            Optional<ContainerImage> containerImageFromDb = containerImageRepository.findById(containerImage.getId());
            if (!containerImageFromDb.isPresent()) {
                containerImageRepository.save(containerImage);
            }
        }

        ContainerConfiguration containerConfiguration = container.getContainerConfiguration();
        if(containerConfiguration.getId() == null) {
            containerConfigurationRepository.save(containerConfiguration);
        } else {
            Optional<ContainerConfiguration> containerConfigurationFromDb = containerConfigurationRepository.findById(containerConfiguration.getId());
            if (!containerConfigurationFromDb.isPresent()) {
                containerConfigurationRepository.save(containerConfiguration);
            }
        }

        return containerRepository.save(container);
    }

    public Container get(Container container) {
        if(container.getId() == null) {
            return null;
        }
        return containerRepository.findById(container.getId()).orElseThrow(EntityNotFoundException::new);
    }
}
