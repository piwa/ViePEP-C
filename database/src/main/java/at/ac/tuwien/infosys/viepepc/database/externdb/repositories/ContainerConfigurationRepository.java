package at.ac.tuwien.infosys.viepepc.database.externdb.repositories;

import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import org.springframework.data.repository.CrudRepository;

/**
 * Created by philippwaibel on 13/06/16.
 */
public interface ContainerConfigurationRepository extends CrudRepository<ContainerConfiguration, Long> {
}
