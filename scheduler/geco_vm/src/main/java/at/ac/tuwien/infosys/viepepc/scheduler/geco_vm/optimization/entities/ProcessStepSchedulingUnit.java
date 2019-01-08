package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import lombok.Data;

import java.util.UUID;

@Data
public class ProcessStepSchedulingUnit implements Cloneable {

    private ProcessStep processStep;
    private String name;
    private UUID internId;
    private String workflowName;
    private ContainerSchedulingUnit containerSchedulingUnit;

    public ProcessStepSchedulingUnit(ProcessStep processStep) {
        this.processStep = processStep;
        this.name = processStep.getName();
        this.internId = processStep.getInternId();
        this.workflowName = processStep.getWorkflowName();

    }

    @Override
    public ProcessStepSchedulingUnit clone() {
        ProcessStepSchedulingUnit clone = new ProcessStepSchedulingUnit(this.processStep);
        clone.setName(this.name);
        clone.setInternId(this.internId);
        clone.setWorkflowName(this.workflowName);
        clone.setContainerSchedulingUnit(this.containerSchedulingUnit);
        return clone;
    }

    @Override
    public String toString() {
        return "ProcessStepSchedulingUnit{" +
                "processStepSchedulingUnit=" + processStep +
                ", name='" + name + '\'' +
                ", internId=" + internId +
                ", workflowName='" + workflowName + '\'' +
                ", containerSchedulingUnits=" + containerSchedulingUnit +
                '}';
    }
}
