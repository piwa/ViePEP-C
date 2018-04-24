package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
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
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by philippwaibel on 12/07/2017.
 */
public class AbstractHeuristicImpl extends AbstractProvisioningImpl {

    // Not needed
    @Override
    protected VirtualMachine startNewVm(OptimizationResult result, VMType vmType, ContainerConfiguration containerConfiguration) throws NoVmFoundException {
        throw new NotImplementedException();
    }

    protected List<ContainerConfiguration> getContainerConfigurations(VirtualMachine vm, List<ProcessStep> processStepList) throws ContainerConfigurationNotFoundException {

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

            boolean alreadyDeployed = false;
            for(Container container : vm.getDeployedContainers()) {
                if(entry.getKey() == container.getContainerImage().getServiceType()) {
                    alreadyDeployed = true;
                    break;
                }
            }
            if(!alreadyDeployed) {

                double cpuRequirement = entry.getKey().getServiceTypeResources().getCpuLoad();
                double ramRequirement = entry.getKey().getServiceTypeResources().getMemory();

                if (entry.getValue() > 1) {
                    cpuRequirement = cpuRequirement + (entry.getValue() - 1) * entry.getKey().getServiceTypeResources().getCpuLoad() / 4;
                    ramRequirement = ramRequirement + (entry.getValue() - 1) * entry.getKey().getServiceTypeResources().getMemory() / 4;
                }

                containerConfigurations.add(cacheContainerService.getBestContainerConfigurations(cpuRequirement, ramRequirement));
            }
        }
        return containerConfigurations;
    }

    protected Map<VirtualMachine, List<ProcessStep>> createVirtualMachineListMap(Chromosome<AnyGene<VirtualMachine>> chromosome, List<ProcessStep> notScheduledProcessSteps, List<ProcessStep> alreadyScheduledProcessSteps) {
        Map<VirtualMachine, List<ProcessStep>> vmToProcessMap = new HashMap<>();
        for (int i = 0; i < chromosome.length(); i++) {
            VirtualMachine vm = chromosome.getGene(i).getAllele();

            if (!vmToProcessMap.containsKey(vm)) {
                vmToProcessMap.put(vm, new ArrayList<>());
            }
            vmToProcessMap.get(vm).add(notScheduledProcessSteps.get(i));

            for(ProcessStep ps : alreadyScheduledProcessSteps) {
                if(ps.getScheduledAtContainer().getVirtualMachine() == vm) {
                    vmToProcessMap.get(vm).add(ps);
                }
            }

        }
        return vmToProcessMap;
    }

}
