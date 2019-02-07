package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.WorkflowElementRepository;
import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by philippwaibel on 17/05/16. edited by Gerta Sheganaku
 */
@Component
@Slf4j
public class WorkflowDaoService {

    @Autowired
    private WorkflowElementRepository workflowElementRepository;
    @Autowired
    private WorkflowUtilities workflowUtilities;
    @Autowired
    private VirtualMachineDaoService virtualMachineDaoService;
    @Autowired
    private ServiceTypeDaoService serviceTypeDaoService;
    @Autowired
    private ContainerDaoService containerDaoService;
    @Autowired
    private ContainerImageDaoService containerImageDaoService;
    @Autowired
    private ContainerConfigurationDaoService containerConfigurationDaoService;

    //    @Transactional(propagation= Propagation.REQUIRES_NEW)
    public WorkflowElement finishWorkflow(WorkflowElement workflow) {
        log.debug("-- Update workflowElement: " + workflow.toString());

        List<Element> flattedWorkflow = workflowUtilities.getFlattenWorkflow(new ArrayList<>(), workflow);
        DateTime finishedDate = getFinishedDate(flattedWorkflow);

        workflow.setFinishedAt(finishedDate);
        for (Element element : flattedWorkflow) {
            if (element.getFinishedAt() == null) {
                element.setFinishedAt(workflow.getFinishedAt()); // TODO can be deleted?
            }
            if (element instanceof ProcessStep) {
//                ServiceType serviceType = null;
                ProcessStep processStep = (ProcessStep) element;
                if (processStep.getContainer() != null) { // if the process step is after an XOR the process steps on one side of the XOR are not executed
//                    serviceType = saveToDatabase(processStep);
                    saveToDatabase(processStep);
                }
                ((ProcessStep) element).setServiceType(null);       // TODO
            }
        }
        return workflowElementRepository.save(workflow);
    }

    public ServiceType saveToDatabase(ProcessStep processStep) {
        Container container = processStep.getContainer();
        VirtualMachineInstance virtualMachineInstance = container.getVirtualMachineInstance();

        if (virtualMachineInstance != null) {

            VirtualMachineInstance virtualMachineInstanceFromDB = virtualMachineDaoService.get(virtualMachineInstance);
            if (virtualMachineInstanceFromDB == null) {
                virtualMachineDaoService.save(virtualMachineInstance);
            }
        }

        Container containerFromDb = containerDaoService.get(container);
        if (containerFromDb == null) {
            containerDaoService.save(container);
        }

//        ServiceType serviceTypeFromDb = serviceTypeDaoService.get(processStep.getServiceType());
//        if (serviceTypeFromDb == null) {
//            serviceTypeDaoService.save(processStep.getServiceType());
//        }

        return container.getContainerImage().getServiceType();
    }

    private DateTime getFinishedDate(List<Element> flattedWorkflow) {
        DateTime finishedDate = null;
        for (Element element : flattedWorkflow) {
            if (element instanceof ProcessStep && element.isLastElement()) {
                if (element.getFinishedAt() != null) {
                    if (finishedDate == null) {
                        finishedDate = element.getFinishedAt();
                    } else if (element.getFinishedAt().isAfter(finishedDate)) {
                        finishedDate = element.getFinishedAt();
                    }
                }
            }
        }
        return finishedDate;
    }

}
