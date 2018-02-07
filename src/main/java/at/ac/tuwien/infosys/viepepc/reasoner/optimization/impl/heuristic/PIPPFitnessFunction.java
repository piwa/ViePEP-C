package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import io.jenetics.AnyGene;
import io.jenetics.Chromosome;
import io.jenetics.Genotype;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class PIPPFitnessFunction implements Function<Genotype<AnyGene<VirtualMachine>>, Double> {

    private final List<ProcessStep> processStepList;

    public PIPPFitnessFunction(List<ProcessStep> processStepList) {
        this.processStepList = processStepList;
    }

    @Override
    public Double apply(Genotype<AnyGene<VirtualMachine>> genotype) {

        Chromosome<AnyGene<VirtualMachine>> chromosome = genotype.getChromosome();

        Map<VirtualMachine, List<ProcessStep>> vmToProcessMap = createVirtualMachineListMap(chromosome);

        double overallCost = 0.0;


        for (Map.Entry<VirtualMachine, List<ProcessStep>> entry : vmToProcessMap.entrySet()) {
            VirtualMachine vm = entry.getKey();
            List<ProcessStep> processSteps = entry.getValue();

            // avaiable container images:
            vm.getAvailableContainerImages();

            overallCost = overallCost + vm.getVmType().getCosts();

            for(ProcessStep processStep : processSteps) {
                if (processStep.getScheduledAtContainer() != null && processStep.getScheduledAtContainer().getVirtualMachine() != null && processStep.getScheduledAtContainer().getVirtualMachine() != vm) {
                    overallCost = overallCost + 1;
                }
            }
        }

        return overallCost;
    }

    private Map<VirtualMachine, List<ProcessStep>> createVirtualMachineListMap(Chromosome<AnyGene<VirtualMachine>> chromosome) {
        Map<VirtualMachine, List<ProcessStep>> vmToProcessMap = new HashMap<>();
        for (int i = 0; i < chromosome.length(); i++) {
            VirtualMachine vm = chromosome.getGene(i).getAllele();
            if (!vmToProcessMap.containsKey(vm)) {
                vmToProcessMap.put(vm, new ArrayList<>());
            }
            vmToProcessMap.get(vm).add(processStepList.get(i));
        }
        return vmToProcessMap;
    }



}
