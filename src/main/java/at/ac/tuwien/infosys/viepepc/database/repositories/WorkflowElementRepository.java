package at.ac.tuwien.infosys.viepepc.database.repositories;

import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import org.springframework.data.repository.CrudRepository;

/**
 * Created by philippwaibel on 17/05/16.
 */

public interface WorkflowElementRepository extends CrudRepository<WorkflowElement, Long> {

    Iterable<WorkflowElement> findAll();

    <S extends WorkflowElement> S save(S entity);
}
