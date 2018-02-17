package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.AbstractProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.NoVmFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import io.jenetics.AnyGene;
import io.jenetics.Chromosome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by philippwaibel on 12/07/2017.
 */
public class AbstractHeuristicImpl extends AbstractProvisioningImpl {

    @Override
    protected VirtualMachine startNewVm(OptimizationResult result, VMType vmType, ContainerConfiguration containerConfiguration) throws NoVmFoundException {
        List<VirtualMachine> virtualMachineList = cacheVirtualMachineService.getVMs(vmType);
        List<VirtualMachine> vmsShouldBeStarted = result.getProcessSteps().stream().map(ProcessStep::getScheduledAtVM).collect(Collectors.toList());

        vmsShouldBeStarted.addAll(result.getProcessSteps().stream().map(processStep -> processStep.getScheduledAtContainer().getVirtualMachine()).collect(Collectors.toList()));
        vmsShouldBeStarted.addAll(result.getVms());

        if(containerConfiguration != null) {
            List<VirtualMachine> waitingVMs = inMemoryCache.getWaitingForExecutingProcessSteps().stream().map(processStep -> processStep.getScheduledAtContainer().getVirtualMachine()).collect(Collectors.toList());
            vmsShouldBeStarted.addAll(waitingVMs.stream().filter(virtualMachine -> checkIfEnoughResourcesLeftOnVM(virtualMachine, containerConfiguration, result)).collect(Collectors.toList()));
        }

        return virtualMachineList.stream().filter(currentVM -> !vmsShouldBeStarted.contains(currentVM) && !currentVM.isLeased() && !currentVM.isStarted()).findFirst().orElseThrow(() -> new NoVmFoundException());
    }

    protected List<ContainerConfiguration> getContainerConfigurations(List<ProcessStep> processStepList) throws ContainerConfigurationNotFoundException {

        Map<ServiceType, Integer> serviceTypeCounter = new HashMap<>();
        List<ContainerConfiguration> containerConfigurations = new ArrayList<>();


        for(ProcessStep processStep : processStepList) {
            if(!serviceTypeCounter.containsKey(processStep.getServiceType())) {
                serviceTypeCounter.put(processStep.getServiceType(), 0);
            }
            int value = serviceTypeCounter.get(processStep.getServiceType()) + 1;
            serviceTypeCounter.put(processStep.getServiceType(), value);
        }

        for(Map.Entry<ServiceType, Integer> entry : serviceTypeCounter.entrySet()) {
            double cpuRequirement = 0.0;
            double ramRequirement = 0.0;

            cpuRequirement = entry.getKey().getServiceTypeResources().getCpuLoad();
            ramRequirement = entry.getKey().getServiceTypeResources().getMemory();

            if(entry.getValue() > 1) {
                cpuRequirement = cpuRequirement + (entry.getValue()-1) * entry.getKey().getServiceTypeResources().getCpuLoad() / 4;
                ramRequirement = ramRequirement + (entry.getValue()-1) * entry.getKey().getServiceTypeResources().getMemory() / 4;
            }

            containerConfigurations.add(cacheContainerService.getBestContainerConfigurations(cpuRequirement, ramRequirement));

        }
        return containerConfigurations;
    }

    protected Map<VirtualMachine, List<ProcessStep>> createVirtualMachineListMap(Chromosome<AnyGene<VirtualMachine>> chromosome) {
        Map<VirtualMachine, List<ProcessStep>> vmToProcessMap = new HashMap<>();
        for (int i = 0; i < chromosome.length(); i++) {
            VirtualMachine vm = chromosome.getGene(i).getAllele();
            if (!vmToProcessMap.containsKey(vm)) {
                vmToProcessMap.put(vm, new ArrayList<>());
            }
            vmToProcessMap.get(vm).add(processSteps.get(i));
        }
        return vmToProcessMap;
    }

}
