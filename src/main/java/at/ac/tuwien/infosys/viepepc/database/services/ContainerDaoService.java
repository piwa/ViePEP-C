package at.ac.tuwien.infosys.viepepc.database.services;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.repositories.ContainerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
        return containerRepository.findOne(container.getId());
    }
}
