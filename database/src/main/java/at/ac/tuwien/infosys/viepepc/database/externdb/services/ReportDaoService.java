package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineReportingAction;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ContainerReportRepository;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.VirtualMachineReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 17/05/16. edited by Gerta Sheganaku
 */
@Component
@Slf4j
public class ReportDaoService {

    @Autowired
    private VirtualMachineReportRepository virtualMachineReportRepository;
    @Autowired
    private ContainerReportRepository containerReportRepository;

    public void save(VirtualMachineReportingAction reportingAction) {
        virtualMachineReportRepository.save(reportingAction);
    }
    
    public void save(ContainerReportingAction reportingAction) {
        containerReportRepository.save(reportingAction);
    }


}
