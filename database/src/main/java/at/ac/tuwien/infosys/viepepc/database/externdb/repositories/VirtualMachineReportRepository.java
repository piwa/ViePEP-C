package at.ac.tuwien.infosys.viepepc.database.externdb.repositories;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineReportingAction;
import org.springframework.data.repository.CrudRepository;

/**
 * Created by philippwaibel on 17/05/16.
 */
public interface VirtualMachineReportRepository extends CrudRepository<VirtualMachineReportingAction, Long> {

}
