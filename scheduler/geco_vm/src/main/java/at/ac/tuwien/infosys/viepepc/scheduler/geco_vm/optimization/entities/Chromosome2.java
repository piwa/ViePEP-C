package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("Duplicates")
public class Chromosome2 {

    @Getter
    private final List<ServiceTypeSchedulingUnit> genes;

    public Chromosome2(List<ServiceTypeSchedulingUnit> genes) {
        this.genes = genes;
    }


    public List<ServiceTypeSchedulingUnit> getFlattenChromosome() {
        return genes;
    }

    public Chromosome2 clone() {

        List<ServiceTypeSchedulingUnit> offspring = new ArrayList<>();

        Map<ServiceTypeSchedulingUnit, ServiceTypeSchedulingUnit> originalToCloneMap = new HashMap<>();

        for (ServiceTypeSchedulingUnit gene : this.getGenes()) {
            ServiceTypeSchedulingUnit clonedGene = gene.clone();
            originalToCloneMap.put(gene, clonedGene);
            offspring.add(clonedGene);
        }

        Map<VirtualMachineSchedulingUnit, VirtualMachineSchedulingUnit> originalToCloneVirtualMachineSchedulingMap = new HashMap<>();
        Map<Container, Container> originalToCloneContainerMap = new HashMap<>();
        for (ServiceTypeSchedulingUnit originalGene : this.getGenes()) {
            ServiceTypeSchedulingUnit clonedGene = originalToCloneMap.get(originalGene);

            VirtualMachineSchedulingUnit originalVirtualMachineSchedulingUnit = originalGene.getVirtualMachineSchedulingUnit();
            if(originalVirtualMachineSchedulingUnit != null) {
                VirtualMachineSchedulingUnit clonedVirtualMachineSchedulingUnit = originalToCloneVirtualMachineSchedulingMap.get(originalVirtualMachineSchedulingUnit);
                if (clonedVirtualMachineSchedulingUnit == null) {
                    clonedVirtualMachineSchedulingUnit = originalGene.getVirtualMachineSchedulingUnit().clone();
                    originalToCloneVirtualMachineSchedulingMap.put(originalVirtualMachineSchedulingUnit, clonedVirtualMachineSchedulingUnit);
                }
                clonedGene.setVirtualMachineSchedulingUnit(clonedVirtualMachineSchedulingUnit);
//                clonedVirtualMachineSchedulingUnit.getProcessStepSchedulingUnits().add(clonedProcessStepSchedulingUnit);
            }

            Container originalContainer = originalGene.getContainer();
            if(originalContainer != null) {
                Container clonedContainer = originalToCloneContainerMap.get(originalContainer);
                if (clonedContainer == null) {
                    try {
                        clonedContainer = originalGene.getContainer().clone(clonedGene.getServiceType());
                        originalToCloneContainerMap.put(originalContainer, clonedContainer);
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                    }

                }
                clonedGene.setContainer(clonedContainer);
//                clonedVirtualMachineSchedulingUnit.getProcessStepSchedulingUnits().add(clonedProcessStepSchedulingUnit);
            }
        }

        return new Chromosome2(offspring);
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (ServiceTypeSchedulingUnit gene : genes) {
            builder.append(gene.toString());
            builder.append('\n');
        }
        return builder.toString();

    }
}
