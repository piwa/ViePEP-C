package at.ac.tuwien.infosys.viepepc.registry;

import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.registry.impl.service.ServiceTypeNotFoundException;

/**
 * Created by philippwaibel on 18/10/2016.
 */
public interface ServiceRegistryReader {
    ServiceType findServiceType(String serviceTypeName) throws ServiceTypeNotFoundException;

    int getServiceTypeAmount();
}
