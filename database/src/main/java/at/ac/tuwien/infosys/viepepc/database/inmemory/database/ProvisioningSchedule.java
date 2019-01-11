package at.ac.tuwien.infosys.viepepc.database.inmemory.database;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Data
public class ProvisioningSchedule {

    private Map<UUID, ProcessStep> processStepsMap = new HashMap<>();
    private Map<UUID, Container> containersMap = new HashMap<>();
    private Map<UUID, VirtualMachineInstance> virtualMachineInstancesMap = new HashMap<>();

    public List<ProcessStep> getProcessSteps() {
        List<ProcessStep> list = new ArrayList<>(processStepsMap.values());
        list.sort(Comparator.comparing(ProcessStep::getScheduledStartDate));
        return Collections.unmodifiableList(list);
    }

    public List<Container> getContainers() {
        List<Container> list = new ArrayList<>(containersMap.values());
        list.sort(Comparator.comparing(container -> container.getScheduledCloudResourceUsage().getStart()));
        return Collections.unmodifiableList(list);
    }

    public List<VirtualMachineInstance> getVirtualMachineInstances() {
        List<VirtualMachineInstance> list = new ArrayList<>(virtualMachineInstancesMap.values());
        list.sort(Comparator.comparing(vm -> vm.getScheduledCloudResourceUsage().getStart()));
        return Collections.unmodifiableList(list);
    }

    public void addAllProcessSteps(List<ProcessStep> processSteps) {
        processSteps.forEach(processStep -> processStepsMap.putIfAbsent(processStep.getInternId(), processStep));
    }

    public void addAllContainers(List<Container> containers) {
        containers.forEach(container -> containersMap.putIfAbsent(container.getInternId(), container));
    }

    public void addAllVirtualMachineInstances(List<VirtualMachineInstance> virtualMachineInstances) {
        virtualMachineInstances.forEach(vm -> virtualMachineInstancesMap.putIfAbsent(vm.getInternId(), vm));
    }

    public void cleanup() {
        Set<UUID> usedContainer = processStepsMap.values().stream().map(processStep -> processStep.getContainer().getInternId()).collect(Collectors.toSet());
        Set<UUID> usedVMs = processStepsMap.values().stream().map(processStep -> processStep.getContainer().getVirtualMachineInstance().getInternId()).collect(Collectors.toSet());

        containersMap.entrySet().removeIf(entry -> !usedContainer.contains(entry.getKey()));
        virtualMachineInstancesMap.entrySet().removeIf(entry -> !usedVMs.contains(entry.getKey()));
    }
}
