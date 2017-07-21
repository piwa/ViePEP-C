package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.NoVmFoundException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by philippwaibel on 12/07/2017.
 */
public class AbstractVMProvisioningImpl extends AbstractProvisioningImpl {

    @Override
    protected VirtualMachine startNewVm(OptimizationResult result, VMType vmType, ContainerConfiguration containerConfiguration) throws NoVmFoundException {
        List<VirtualMachine> virtualMachineList = cacheVirtualMachineService.getVMs(vmType);
        Set<VirtualMachine> vmsShouldBeStarted = result.getProcessSteps().stream().map(ProcessStep::getScheduledAtVM).collect(Collectors.toSet());
        vmsShouldBeStarted.addAll(inMemoryCache.getWaitingForExecutingProcessSteps().stream().map(processStep -> processStep.getScheduledAtContainer().getVirtualMachine()).collect(Collectors.toSet()));
        vmsShouldBeStarted.addAll(result.getProcessSteps().stream().map(processStep -> processStep.getScheduledAtContainer().getVirtualMachine()).collect(Collectors.toSet()));
        vmsShouldBeStarted.addAll(result.getVms());

        VirtualMachine vm = virtualMachineList.stream().filter(currentVM -> !vmsShouldBeStarted.contains(currentVM) && !currentVM.isLeased() && !currentVM.isStarted()).findFirst().orElseThrow(() -> new NoVmFoundException());
        return vm;
    }


}
