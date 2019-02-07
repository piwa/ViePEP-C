package at.ac.tuwien.infosys.viepepc.database.inmemory.database;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


@Component
@Getter
public class InMemoryCacheImpl {

    private List<VMType> vmTypes = new ArrayList<>();
    private Map<UUID, VirtualMachineInstance> vmInstances = new HashMap<>();


    private List<WorkflowElement> allWorkflowInstances = new ArrayList<>();
    private List<WorkflowElement> runningWorkflows = new ArrayList<>();

//    private Map<String, ProcessStep> processStepsWaitingForServiceDone = new HashMap<>();
//    private Set<ProcessStep> processStepsWaitingForExecution = new HashSet<>();
    private Map<UUID, ProcessStep> allProcessSteps = new HashMap<>();


    private List<ContainerConfiguration> containerConfigurations = new ArrayList<>();
    private Map<UUID, Container> containerInstances = new HashMap<>();


}
