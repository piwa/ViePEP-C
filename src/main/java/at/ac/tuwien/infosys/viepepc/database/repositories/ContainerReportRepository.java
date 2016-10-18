package at.ac.tuwien.infosys.viepepc.database.repositories;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerReportingAction;
import org.springframework.data.repository.CrudRepository;

/**
 * Created by philippwaibel on 17/05/16.
 */
public interface ContainerReportRepository extends CrudRepository<ContainerReportingAction, Long> {

}
