package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ContainerImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityNotFoundException;

/**
 * Created by philippwaibel on 13/06/16. Edited by Gerta Sheganaku
 */
@Component
public class ContainerImageDaoService {

    @Autowired
    private ContainerImageRepository containerImageRepository;

    public ContainerImage save(ContainerImage containerImage) {
        return containerImageRepository.save(containerImage);
    }

    public ContainerImage getContainerImage(ContainerImage containerImage) {
        if(containerImage.getId() != null) {
            return containerImageRepository.findById(containerImage.getId()).orElseThrow(EntityNotFoundException::new);
        }
        return null;
    }
}
