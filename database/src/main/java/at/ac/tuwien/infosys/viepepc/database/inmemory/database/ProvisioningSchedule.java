package at.ac.tuwien.infosys.viepepc.database.inmemory.database;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@Data
public class ProvisioningSchedule {

    private List<ProcessStep> processSteps = new ArrayList<>();
    private List<Container> containers = new ArrayList<>();
    private List<VirtualMachineInstance> virtualMachineInstances = new ArrayList<>();


    public void sortLists() {
        processSteps.sort(Comparator.comparing(ProcessStep::getScheduledStartDate));
        containers.sort(Comparator.comparing(container -> container.getScheduledCloudResourceUsage().getStart()));
        virtualMachineInstances.sort(Comparator.comparing(vm -> vm.getScheduledCloudResourceUsage().getStart()));
    }
}
