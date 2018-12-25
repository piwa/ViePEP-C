package at.ac.tuwien.infosys.viepepc.database.externdb.repositories;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * Created by philippwaibel on 17/05/16, edited by Gerta Sheganaku
 */
public interface ProcessStepElementRepository extends CrudRepository<ProcessStep, Long> {

//    @Query("select p from process_step p where p.scheduledAtVM.id = ?1")
//    List<ProcessStep> findByVM(Long virtualMachineId) ;
    
    @Query("select p from process_step p where p.container.id = ?1")
    List<ProcessStep> findByContainer(Long containerId) ;

//    @Query("select p from process_step p where p.container.id = ?1 and p.finishedAt is null")
//    ProcessStep findByContainerAndRunning(Long containerId);

//    @Query("select p from process_step p where p.scheduledAtVM.id = ?1 and p.finishedAt is null")
//    List<ProcessStep> findByVMAndRunning(Long virtualMachineId) ;

}
