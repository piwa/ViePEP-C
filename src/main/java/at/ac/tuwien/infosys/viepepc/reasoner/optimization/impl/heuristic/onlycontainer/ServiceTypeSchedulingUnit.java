package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer;

import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.joda.time.Interval;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Getter
public class ServiceTypeSchedulingUnit {

    @Setter private Interval deploymentInterval;
    private final ServiceType serviceType;
    private List<ProcessStep> processSteps = new ArrayList<>();

    public void addProcessStep(ProcessStep processStep) {
        processSteps.add(processStep);
    }

}