package at.ac.tuwien.infosys.viepepc.registry;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;

/**
 * Created by philippwaibel on 18/10/2016.
 */
public interface ContainerImageRegistryReader {
    ContainerImage findContainerImage(ServiceType serviceType) throws ContainerImageNotFoundException, ContainerImageNotFoundException;

    int getContainerImageAmount();
}
