package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ProcessStepElementRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Created by philippwaibel on 17/05/16. edited by Gerta Sheganaku
 */
@Component
@Slf4j
public class ProcessStepDaoService {

    @Autowired
    private ProcessStepElementRepository processStepElementRepository;

//    public List<ProcessStep> findByVM(VirtualMachineInstance virtualMachineInstance) {
//        return processStepElementRepository.findByVM(virtualMachineInstance.getId());
//    }
    
    public List<ProcessStep> findByContainer(Container container) {
        return processStepElementRepository.findByContainer(container.getId());
    }
}
