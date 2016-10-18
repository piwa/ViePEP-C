package at.ac.tuwien.infosys.viepepc.registry;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;

/**
 * Created by philippwaibel on 18/10/2016.
 */
public interface ContainerRegistryReader {
    ContainerImage findContainerImage(String serviceTypeName);

    int getContainerImageAmount();
}
