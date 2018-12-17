package at.ac.tuwien.infosys.viepepc.scheduler.frincu;

import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.exceptions.NoVmFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by philippwaibel on 12/07/2017.
 */
public abstract class AbstractContainerProvisioningImpl extends AbstractProvisioningImpl {

    @Override
    protected VirtualMachine startNewVm(OptimizationResult result, VMType vmType, ContainerConfiguration containerConfiguration) throws NoVmFoundException {
        List<VirtualMachine> virtualMachineList = cacheVirtualMachineService.getVMs(vmType);
        List<VirtualMachine> vmsShouldBeStarted = result.getProcessSteps().stream().map(ProcessStep::getScheduledAtVM).collect(Collectors.toList());

        vmsShouldBeStarted.addAll(result.getProcessSteps().stream().map(processStep -> processStep.getScheduledAtContainer().getVirtualMachine()).collect(Collectors.toList()));
        vmsShouldBeStarted.addAll(result.getVms());

        if (containerConfiguration != null) {
            List<VirtualMachine> waitingVMs = inMemoryCache.getWaitingForExecutingProcessSteps().stream().map(processStep -> processStep.getScheduledAtContainer().getVirtualMachine()).collect(Collectors.toList());
            vmsShouldBeStarted.addAll(waitingVMs.stream().filter(virtualMachine -> checkIfEnoughResourcesLeftOnVM(virtualMachine, containerConfiguration, result)).collect(Collectors.toList()));
        }

        return virtualMachineList.stream().filter(currentVM -> !vmsShouldBeStarted.contains(currentVM) && !currentVM.isLeased() && !currentVM.isStarted()).findFirst().orElseThrow(() -> new NoVmFoundException());
    }


}
