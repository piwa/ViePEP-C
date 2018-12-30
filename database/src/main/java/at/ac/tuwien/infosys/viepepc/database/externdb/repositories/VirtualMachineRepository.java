package at.ac.tuwien.infosys.viepepc.database.externdb.repositories;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import org.springframework.data.repository.CrudRepository;

/**
 * Created by philippwaibel on 17/05/16.
 */
public interface VirtualMachineRepository extends CrudRepository<VirtualMachineInstance, Long> {

    Iterable<VirtualMachineInstance> findAll();

    <S extends VirtualMachineInstance> S save(S entity);

    <S extends VirtualMachineInstance> Iterable<S> save(Iterable<S> entities);
}
