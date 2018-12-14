package at.ac.tuwien.infosys.viepepc.database.externdb.repositories;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import org.springframework.data.repository.CrudRepository;

/**
 * Created by philippwaibel on 17/05/16.
 */
public interface VirtualMachineRepository extends CrudRepository<VirtualMachine, Long> {

    Iterable<VirtualMachine> findAll();

    <S extends VirtualMachine> S save(S entity);

    <S extends VirtualMachine> Iterable<S> save(Iterable<S> entities);
}
