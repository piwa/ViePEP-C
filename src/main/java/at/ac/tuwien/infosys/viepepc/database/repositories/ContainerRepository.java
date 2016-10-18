package at.ac.tuwien.infosys.viepepc.database.repositories;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import org.springframework.data.repository.CrudRepository;

/**
 * Created by philippwaibel on 13/06/16.
 */
public interface ContainerRepository extends CrudRepository<Container, Long> {
	
}
