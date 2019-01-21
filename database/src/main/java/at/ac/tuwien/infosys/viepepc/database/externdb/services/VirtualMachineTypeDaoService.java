package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.VirtualMachineRepository;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.VirtualMachineTypeRepository;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityNotFoundException;

/**
 * Created by philippwaibel on 17/05/16.
 */
@Component
@Slf4j
public class VirtualMachineTypeDaoService {

    @Autowired
    private VirtualMachineTypeRepository virtualMachineTypeRepository;

    public VMType update(VMType vmType) {
        return virtualMachineTypeRepository.save(vmType);
    }

    public VMType getVMType(VMType vmType) {
        return virtualMachineTypeRepository.findById(vmType.getTableId()).orElseThrow(EntityNotFoundException::new);
    }
}
