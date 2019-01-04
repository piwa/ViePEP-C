package at.ac.tuwien.infosys.viepepc.scheduler.library;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import lombok.Getter;
import lombok.Setter;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by philippwaibel on 19/10/2016.
 */
@Getter
@Setter
public class OptimizationResult {

    private List<ProcessStep> processSteps = new ArrayList<>();
    private List<VirtualMachineInstance> virtualMachineInstances = new ArrayList<>();
    private List<Container> containers = new ArrayList<>();

}
