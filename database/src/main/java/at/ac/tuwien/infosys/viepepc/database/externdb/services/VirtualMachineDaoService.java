package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.VirtualMachineTypeRepository;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.VirtualMachineRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityNotFoundException;
import java.util.Optional;

/**
 * Created by philippwaibel on 17/05/16.
 */
@Component
public class VirtualMachineDaoService {

    @Autowired
    private VirtualMachineRepository virtualMachineRepository;
    @Autowired
    private VirtualMachineTypeRepository virtualMachineTypeRepository;

    public VirtualMachineInstance save(VirtualMachineInstance virtualMachineInstance) {

        if(virtualMachineInstance.getVmType().getTableId() == null) {
            virtualMachineTypeRepository.save(virtualMachineInstance.getVmType());
        } else {
            Optional<VMType> vmType = virtualMachineTypeRepository.findById(virtualMachineInstance.getVmType().getTableId());
            if (!vmType.isPresent()) {
                virtualMachineTypeRepository.save(virtualMachineInstance.getVmType());
            }
        }

        return virtualMachineRepository.save(virtualMachineInstance);
    }

    public VirtualMachineInstance get(VirtualMachineInstance vm) {
        if(vm.getId() == null) {
            return null;
        }
        return virtualMachineRepository.findById(vm.getId()).orElseThrow(EntityNotFoundException::new);
    }
}
