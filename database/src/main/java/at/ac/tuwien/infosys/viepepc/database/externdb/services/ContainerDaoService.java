package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ContainerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityNotFoundException;

/**
 * Created by philippwaibel on 13/06/16. edited by Gerta Sheganaku.
 */
@Component
public class ContainerDaoService {

    @Autowired
    private ContainerRepository containerRepository;

    public Container update(Container container) {
        return containerRepository.save(container);
    }

    public Container getContainer(Container container) {
        return containerRepository.findById(container.getId()).orElseThrow(EntityNotFoundException::new);
    }
}
