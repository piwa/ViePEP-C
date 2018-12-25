package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import lombok.Data;

import java.util.UUID;

@Data
public class ProcessStepSchedulingUnit {

    private ServiceType serviceType;
    private String name;
    private UUID internId;
    private String workflowName;
    private boolean lastElement;

    private ContainerSchedulingUnit containerSchedulingUnit;

    public ProcessStepSchedulingUnit(ProcessStep processStep, ServiceType clonedServiceType) {
        this.lastElement = processStep.isLastElement();
        this.serviceType = clonedServiceType;
        this.name = processStep.getName();
        this.internId = processStep.getInternId();
        this.workflowName = processStep.getWorkflowName();

    }

}
