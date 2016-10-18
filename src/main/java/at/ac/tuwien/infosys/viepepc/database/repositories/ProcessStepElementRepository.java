package at.ac.tuwien.infosys.viepepc.database.repositories;

import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * Created by philippwaibel on 17/05/16, edited by Gerta Sheganaku
 */
public interface ProcessStepElementRepository extends CrudRepository<ProcessStep, Long> {

    @Query("select p from ProcessStep p where p.scheduledAtVM.id = ?1")
    List<ProcessStep> findByVM(Long virtualMachineId) ;
    
    @Query("select p from ProcessStep p where p.scheduledAtContainer.id = ?1")
    List<ProcessStep> findByContainer(Long containerId) ;
}
