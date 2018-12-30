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
    private Set<ProcessStep> waitingForExecutingProcessSteps = new HashSet<>();

    private List<ContainerConfiguration> containerConfigurations = new ArrayList<>();
    private List<Container> containerInstances = new ArrayList<>();

//    private List<WorkflowElement> allWorkflowInstances = new ArrayList<>();
//
//    private Set<ProcessStep> waitingForExecutingProcessSteps = new HashSet<>();
//    private ConcurrentMap<VirtualMachineInstance, Object> vmDeployedWaitObject = new ConcurrentHashMap<>();
//
//    private List<Container> runningContainers = new ArrayList<>();
//
//    private ConcurrentMap<String, ProcessStep> processStepsWaitingForServiceDone = new ConcurrentHashMap<>();
//
//    public void clear() {
//        runningWorkflows = new ArrayList<>();
//        allWorkflowInstances = new ArrayList<>();
//        vmTypeVmMap = new HashMap<>();
//        containerConfigurations = new ArrayList<>();
//        waitingForExecutingProcessSteps = new HashSet<>();
//        vmDeployedWaitObject = new ConcurrentHashMap<>();
//        processStepsWaitingForServiceDone = new ConcurrentHashMap();
//    }
//
//    public void addRunningWorkflow(WorkflowElement workflowElement) {
//        runningWorkflows.add(workflowElement);
//    }
//
//    public void addToAllWorkflows(WorkflowElement workflowElement) {
//        allWorkflowInstances.add(workflowElement);
//    }
//
//    public void addContainerConfiguration(ContainerConfiguration containerConfiguration) {
//        containerConfigurations.add(containerConfiguration);
//    }
//
//    public void addVMType(VMType vmType) {
//        vmTypeVmMap.put(vmType, new ArrayList<VirtualMachineInstance>());
//    }
//
//    public void addAllVMType(List<VMType> vmTypes) {
//        for(VMType vmType : vmTypes) {
//            addVMType(vmType);
//        }
//    }
//
//    public void addVirtualMachine(VirtualMachineInstance vm) {
//    	if(!vmTypeVmMap.containsKey(vm.getVmType())) {
//    		addVMType(vm.getVmType());
//    	}
//        vmTypeVmMap.get(vm.getVmType()).add(vm);
//    }
//
//    public void addVirtualMachines(List<VirtualMachineInstance> virtualMachines) {
//    	for(VirtualMachineInstance vm : virtualMachines) {
//    		addVirtualMachine(vm);
//    	}
//    }
//
//    public void addAllContainerConfiguration(List<ContainerConfiguration> configurations) {
//        for(ContainerConfiguration configuration : configurations) {
//            addContainerConfiguration(configuration);
//        }
//    }
//
//    public Set<ProcessStep> getWaitingForExecutingProcessSteps() {
//        waitingForExecutingProcessSteps.removeIf(processStep -> processStep.getStartDate() != null);
//        return waitingForExecutingProcessSteps;
//    }
//
//    public void addRunningContainer(Container container) {
//        runningContainers.add(container);
//    }

}
