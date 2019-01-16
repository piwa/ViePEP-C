package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.WorkflowElementRepository;
import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private ContainerDaoService containerDaoService;
    @Autowired
    private ContainerImageDaoService containerImageDaoService;
    @Autowired
    private ContainerConfigurationDaoService containerConfigurationDaoService;
    @Autowired
    private ServiceTypeDaoService serviceTypeDaoService;

//	@Value("${use.container}")
//	private boolean useContainer;

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
                ServiceType serviceType = null;

                Container container = ((ProcessStep) element).getContainer();
                if (container != null) { // if the process step is after an XOR the process steps on one side of the XOR are not executed
                    // make sure we save the DockerImage first, to avoid org.hibernate.TransientPropertyValueException:


                    VirtualMachineInstance virtualMachineInstance = container.getVirtualMachineInstance();

                    if (virtualMachineInstance != null) { // if the process step is after an XOR the process steps on one side of the XOR are not executed
                        if (virtualMachineInstance.getId() != null) {
                            virtualMachineInstance = virtualMachineDaoService.getVm(virtualMachineInstance);
                            ((ProcessStep) element).getContainer().setVirtualMachineInstance(virtualMachineInstance);
                            virtualMachineDaoService.update(virtualMachineInstance);
                        } else {
                            virtualMachineDaoService.update(virtualMachineInstance);
                            virtualMachineInstance = virtualMachineDaoService.getVm(virtualMachineInstance);
                            ((ProcessStep) element).getContainer().setVirtualMachineInstance(virtualMachineInstance);
                        }
                    }


//                    if(virtualMachineInstance != null) {
//                        if (virtualMachineInstance.getId() != null) {
//                            virtualMachineInstance = virtualMachineDaoService.getVm(virtualMachineInstance);
//                        } else {
//                            virtualMachineInstance = virtualMachineDaoService.update(virtualMachineInstance);
//                        }
//                        container.setVirtualMachineInstance(virtualMachineInstance);
//                    }


                    ContainerImage img = container.getContainerImage();
                    if (img != null) {
                        ContainerImage dockerImgInDB = containerImageDaoService.getContainerImage(img);
                        if (dockerImgInDB == null) {
                            img = containerImageDaoService.save(img);
                            container.setContainerImage(img);
                        } else {
                            container.setContainerImage(dockerImgInDB);
                        }
                    }

                    ContainerConfiguration config = container.getContainerConfiguration();
                    if (config != null) {
                        ContainerConfiguration dockerConfigInDB = containerConfigurationDaoService.getContainerConfiguration(config);
                        if (dockerConfigInDB == null) {
                            config = containerConfigurationDaoService.save(config);
                            container.setContainerConfiguration(config);
                        } else {
                            container.setContainerConfiguration(dockerConfigInDB);
                        }
                    }

                    if (container.getId() != null) {
                        container = containerDaoService.getContainer(container);
                        ((ProcessStep) element).setContainer(container);
                        containerDaoService.update(container);
                    } else {
                        virtualMachineInstance = virtualMachineDaoService.getVm(virtualMachineInstance);
                        container.setVirtualMachineInstance(virtualMachineInstance);
                        container = containerDaoService.update(container);
                        ((ProcessStep) element).setContainer(container);
                    }
                    serviceType = container.getContainerImage().getServiceType();

                }


                ((ProcessStep) element).setServiceType(serviceType);

            }
        }
        return workflowElementRepository.save(workflow);
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
