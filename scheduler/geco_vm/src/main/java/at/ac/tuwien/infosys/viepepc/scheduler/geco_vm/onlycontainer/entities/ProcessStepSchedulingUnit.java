package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import lombok.Data;
import lombok.ToString;
import org.apache.tomcat.jni.Proc;

import java.util.UUID;

@Data
public class ProcessStepSchedulingUnit {

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
