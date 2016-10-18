package at.ac.tuwien.infosys.viepepc.database.services;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.repositories.WorkflowElementRepository;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
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
	private PlacementHelper placementHelperImpl;
	@Autowired
	private VirtualMachineDaoService virtualMachineDaoService;
	@Autowired
	private ContainerDaoService containerDaoService;
	@Autowired
	private ContainerImageDaoService containerImageDaoService;

	@Value("${use.docker}")
	private boolean useDocker;

    @Transactional(propagation= Propagation.REQUIRES_NEW)
	public WorkflowElement finishWorkflow(WorkflowElement workflow) {
		log.info("-- Update workflowElement: " + workflow.toString());

		List<Element> flattedWorkflow = placementHelperImpl.getFlattenWorkflow(new ArrayList<>(), workflow);
		Date finishedDate = getFinishedDate(flattedWorkflow);

		workflow.setFinishedAt(finishedDate);
		for (Element element : flattedWorkflow) {
			if (element.getFinishedAt() == null) {
				element.setFinishedAt(workflow.getFinishedAt()); // TODO can be deleted?
			}
			if (element instanceof ProcessStep) {
				VirtualMachine vm = ((ProcessStep) element).getScheduledAtVM();
				if (vm != null) { // if the process step is after an XOR the process steps on one side of the XOR are not executed
					if (vm.getId() != null) {
						vm = virtualMachineDaoService.getVm(vm);
						((ProcessStep) element).setScheduledAtVM(vm);
						virtualMachineDaoService.update(vm);
					} else {
						vm = virtualMachineDaoService.update(vm);
						((ProcessStep) element).setScheduledAtVM(vm);
					}
				}
				
				if (useDocker) {
					Container container = ((ProcessStep) element).getScheduledAtContainer();
					if (container != null) { // if the process step is after an XOR the process steps on one side of the XOR are not executed
						// make sure we save the DockerImage first, to avoid org.hibernate.TransientPropertyValueException:
						ContainerImage img = container.getContainerImage();
						if(img != null) {
							ContainerImage dockerImgInDB = containerImageDaoService.getContainerImage(img);
							if(dockerImgInDB == null) {
								img = containerImageDaoService.save(img);
								container.setContainerImage(img);
							} else {
								container.setContainerImage(dockerImgInDB);
							}
						}
						if (container.getId() != null) {
							container = containerDaoService.getContainer(container);
							((ProcessStep) element).setScheduledAtContainer(container);
							containerDaoService.update(container);
						} else {
							container = containerDaoService.update(container);
							((ProcessStep) element).setScheduledAtContainer(container);
						}
					}
				}
			}
		}
		return workflowElementRepository.save(workflow);
	}

	private Date getFinishedDate(List<Element> flattedWorkflow) {
		Date finishedDate = null;
		for (Element element : flattedWorkflow) {
			if (element instanceof ProcessStep && element.isLastElement()) {
				if (element.getFinishedAt() != null) {
					if (finishedDate == null) {
						finishedDate = element.getFinishedAt();
					} else if (element.getFinishedAt().after(finishedDate)) {
						finishedDate = element.getFinishedAt();
					}
				}
			}
		}
		return finishedDate;
	}

}
