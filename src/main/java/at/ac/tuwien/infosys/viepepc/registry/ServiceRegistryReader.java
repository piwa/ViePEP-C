package at.ac.tuwien.infosys.viepepc.registry;

import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;

/**
 * Created by philippwaibel on 18/10/2016.
 */
public interface ServiceRegistryReader {
    ServiceType findServiceType(String serviceTypeName);

    int getServiceTypeAmount();
}
