package at.ac.tuwien.infosys.viepepc.database.repositories;

import at.ac.tuwien.infosys.viepepc.database.entities.workflow.Element;
import org.springframework.data.repository.CrudRepository;

/**
 * Created by philippwaibel on 17/05/16.
 */
public interface ElementRepository extends CrudRepository<Element, Long> {

    <S extends Element> S save(S entity);

}
