package at.ac.tuwien.infosys.viepepc.database.externdb.repositories;

/**
 * Created by philippwaibel on 30/12/2016.
 */

import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceTypeResources;
import org.springframework.data.repository.CrudRepository;

public interface ServiceTypeResourcesRepository extends CrudRepository<ServiceTypeResources, Long> {
}
