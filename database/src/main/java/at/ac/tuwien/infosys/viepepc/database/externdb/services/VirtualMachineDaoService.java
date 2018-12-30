package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
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

    public VirtualMachineInstance update(VirtualMachineInstance virtualMachineInstance) {
        return virtualMachineRepository.save(virtualMachineInstance);
    }

    public VirtualMachineInstance getVm(VirtualMachineInstance vm) {
        return virtualMachineRepository.findById(vm.getId()).orElseThrow(EntityNotFoundException::new);
    }
}
