package at.ac.tuwien.infosys.viepepc.library.registry;

import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;

/**
 * Created by philippwaibel on 18/10/2016.
 */
public interface ContainerImageRegistryReader {
    ContainerImage findContainerImage(ServiceType serviceType) throws ContainerImageNotFoundException, ContainerImageNotFoundException;

    int getContainerImageAmount();
}
