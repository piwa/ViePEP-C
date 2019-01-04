package at.ac.tuwien.infosys.viepepc.database.inmemory.database;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


@Component
@Getter
public class InMemoryCacheImpl {

    private List<VMType> vmTypes = new ArrayList<>();
    private List<VirtualMachineInstance> vmInstances = new ArrayList<>();
    private ConcurrentMap<VirtualMachineInstance, Object> vmDeployedWaitObjectMap = new ConcurrentHashMap<>();

    private List<WorkflowElement> allWorkflowInstances = new ArrayList<>();
    private List<WorkflowElement> runningWorkflows = new ArrayList<>();

    private ConcurrentMap<String, ProcessStep> processStepsWaitingForServiceDone = new ConcurrentHashMap<>();
    private Set<ProcessStep> processStepsWaitingForExecution = new HashSet<>();

    private List<ContainerConfiguration> containerConfigurations = new ArrayList<>();
    private List<Container> containerInstances = new ArrayList<>();


}
