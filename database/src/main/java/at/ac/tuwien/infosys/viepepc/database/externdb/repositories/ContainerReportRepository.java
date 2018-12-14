package at.ac.tuwien.infosys.viepepc.database.externdb.repositories;

import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerReportingAction;
import org.springframework.data.repository.CrudRepository;

/**
 * Created by philippwaibel on 17/05/16.
 */
public interface ContainerReportRepository extends CrudRepository<ContainerReportingAction, Long> {

}
