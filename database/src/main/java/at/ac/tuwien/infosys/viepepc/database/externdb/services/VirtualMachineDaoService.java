package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.VirtualMachineRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityNotFoundException;

/**
 * Created by philippwaibel on 17/05/16.
 */
@Component
@Slf4j
public class VirtualMachineDaoService {

    @Autowired
    private VirtualMachineRepository virtualMachineRepository;

    public VirtualMachine update(VirtualMachine virtualMachine) {
        return virtualMachineRepository.save(virtualMachine);
    }

    public VirtualMachine getVm(VirtualMachine vm) {
        return virtualMachineRepository.findById(vm.getId()).orElseThrow(EntityNotFoundException::new);
    }
}
