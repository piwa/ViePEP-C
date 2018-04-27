package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.withvm;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import io.jenetics.AnyGene;
import io.jenetics.Chromosome;
import io.jenetics.Genotype;
import io.jenetics.util.ISeq;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ValidityChecker extends AbstractHeuristicImpl {

    @Setter private List<ProcessStep> alreadyScheduledProcessSteps;
    @Setter private List<ProcessStep> notScheduledProcessSteps;

    public boolean isValid(Genotype<AnyGene<VirtualMachine>> genotype) {
        Chromosome<AnyGene<VirtualMachine>> chromosome = genotype.getChromosome();

        Map<VirtualMachine, List<ProcessStep>> vmToProcessMap = createVirtualMachineListMap(chromosome, notScheduledProcessSteps, alreadyScheduledProcessSteps);

        return check(vmToProcessMap);
    }

    public boolean isValidNewInstance(ISeq<VirtualMachine> object) {
        Map<VirtualMachine, List<ProcessStep>> vmToProcessMap = new HashMap<>();
        for (int i = 0; i < object.length(); i++) {
            VirtualMachine vm = object.get(i);
            if (!vmToProcessMap.containsKey(vm)) {
                vmToProcessMap.put(vm, new ArrayList<>());
            }
            vmToProcessMap.get(vm).add(notScheduledProcessSteps.get(i));
        }

        return check(vmToProcessMap);
    }

    private boolean check(Map<VirtualMachine, List<ProcessStep>> vmToProcessMap) {
        for (Map.Entry<VirtualMachine, List<ProcessStep>> entry : vmToProcessMap.entrySet()) {
            VirtualMachine vm = entry.getKey();
            List<ProcessStep> processSteps = entry.getValue();

            try {
                if (!checkResourceAvailability(vm, processSteps)) {
                    return false;
                }
            } catch (ContainerConfigurationNotFoundException e) {
                log.error("Container Configuration not found");
                return false;
            }
        }
        return true;
    }


    private boolean checkResourceAvailability(VirtualMachine vm, List<ProcessStep> processStepList) throws ContainerConfigurationNotFoundException {

        double cpuRequirement = 0.0;
        double ramRequirement = 0.0;

        cpuRequirement = cpuRequirement + vm.getDeployedContainers().stream().mapToDouble(c -> c.getContainerConfiguration().getCPUPoints()).sum();
        ramRequirement = ramRequirement + vm.getDeployedContainers().stream().mapToDouble(c -> c.getContainerConfiguration().getRam()).sum();

        for(ContainerConfiguration config : getContainerConfigurations(vm, processStepList)) {
            cpuRequirement = cpuRequirement + config.getCPUPoints();
            ramRequirement = ramRequirement + config.getRam();
        }

        if (vm.getVmType().getCpuPoints() >= cpuRequirement && vm.getVmType().getRamPoints() >= ramRequirement) {
            return true;
        }

        return false;
    }
}
