package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities;

import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("Duplicates")
public class Chromosome {

    @Getter
    private final List<List<Gene>> genes;

    public Chromosome(List<List<Gene>> genes) {
        this.genes = genes;
    }

    public Interval getInterval(int process, int step) {
        return genes.get(process).get(step).getExecutionInterval();
    }

    public List<Gene> getRow(int row) {
        return genes.get(row);
    }


    public List<Gene> getFlattenChromosome() {
        return genes.stream().flatMap(List::stream).collect(Collectors.toList());
    }

    public int getRowAmount() {
        return genes.size();
    }

    public Chromosome clone() {
        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(this, this.getClass().getSimpleName() + "_clone_1");
        List<List<Gene>> offspring = new ArrayList<>();

        Map<Gene, Gene> originalToCloneMap = new HashMap<>();

        for (List<Gene> subChromosome : this.getGenes()) {
            List<Gene> newSubChromosome = new ArrayList<>();
            for (Gene gene : subChromosome) {
                Gene clonedGene = gene.clone();
                originalToCloneMap.put(gene, clonedGene);
                newSubChromosome.add(clonedGene);
            }
            offspring.add(newSubChromosome);
        }

//        Map<ProcessStepSchedulingUnit, ProcessStepSchedulingUnit> originalToCloneProcessStepSchedulingMap = new HashMap<>();
        Map<ContainerSchedulingUnit, ContainerSchedulingUnit> originalToCloneContainerSchedulingMap = new HashMap<>();
        Map<VirtualMachineSchedulingUnit, VirtualMachineSchedulingUnit> originalToCloneVirtualMachineSchedulingMap = new HashMap<>();
        for (Gene originalGene : this.getFlattenChromosome()) {
            Gene clonedGene = originalToCloneMap.get(originalGene);

            originalGene.getNextGenes().stream().map(originalToCloneMap::get).forEach(clonedGene::addNextGene);
            originalGene.getPreviousGenes().stream().map(originalToCloneMap::get).forEach(clonedGene::addPreviousGene);


            ContainerSchedulingUnit originalContainerSchedulingUnit = originalGene.getProcessStepSchedulingUnit().getContainerSchedulingUnit();

            ProcessStepSchedulingUnit clonedProcessStepSchedulingUnit = clonedGene.getProcessStepSchedulingUnit();
            VirtualMachineSchedulingUnit clonedVirtualMachineSchedulingUnit = originalToCloneVirtualMachineSchedulingMap.get(originalContainerSchedulingUnit.getScheduledOnVm());
            ContainerSchedulingUnit clonedContainerSchedulingUnit = originalToCloneContainerSchedulingMap.get(originalContainerSchedulingUnit);

            if(clonedVirtualMachineSchedulingUnit == null) {
                clonedVirtualMachineSchedulingUnit = originalContainerSchedulingUnit.getScheduledOnVm().clone();
                originalToCloneVirtualMachineSchedulingMap.put(originalContainerSchedulingUnit.getScheduledOnVm(), clonedVirtualMachineSchedulingUnit);
            }
            if(clonedContainerSchedulingUnit == null) {
                clonedContainerSchedulingUnit = originalContainerSchedulingUnit.clone();
                originalToCloneContainerSchedulingMap.put(originalContainerSchedulingUnit, clonedContainerSchedulingUnit);
            }

//            clonedGene.setProcessStepSchedulingUnit(clonedProcessStepSchedulingUnit);
            clonedProcessStepSchedulingUnit.setContainerSchedulingUnit(clonedContainerSchedulingUnit);
            clonedContainerSchedulingUnit.setScheduledOnVm(clonedVirtualMachineSchedulingUnit);
            clonedContainerSchedulingUnit.getProcessStepGenes().add(clonedGene);
            clonedVirtualMachineSchedulingUnit.getScheduledContainers().add(clonedContainerSchedulingUnit);
        }

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(this, this.getClass().getSimpleName() + "_clone_2");
        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(new Chromosome(offspring), this.getClass().getSimpleName() + "_clone_3");
        return new Chromosome(offspring);
    }


    public static void moveGeneAndNextGenesByFixedTime(Gene currentGene, long deltaTime) {
        currentGene.moveIntervalPlus(deltaTime);

        currentGene.getNextGenes().forEach(gene -> {
            if (gene != null) {
                moveNextGenesByFixedTime(gene, deltaTime);
            }
        });
    }

    private static void moveNextGenesByFixedTime(Gene currentGene, long deltaTime) {
        if (currentGene.getLatestPreviousGene() == null || currentGene.getExecutionInterval().getStart().isBefore(currentGene.getLatestPreviousGene().getExecutionInterval().getStart())) {
            currentGene.moveIntervalPlus(deltaTime);
        }
        currentGene.getNextGenes().forEach(gene -> {
            if (gene != null) {
                moveNextGenesByFixedTime(gene, deltaTime);
            }
        });
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (List<Gene> row : genes) {
            for (Gene cell : row) {
                builder.append(cell.toString());
            }
            builder.append('\n');
        }
        return builder.toString();

    }

    public String toString(int rowIndex) {
        StringBuilder builder = new StringBuilder();
        for (Gene cell : getRow(rowIndex)) {
            builder.append(cell.toString());
        }

        return builder.toString();
    }

    @Getter
    @Setter
    public static class Gene {

        private Interval executionInterval;

        private boolean fixed;
        private ProcessStepSchedulingUnit processStepSchedulingUnit;
        private Set<Gene> previousGenes = new HashSet<>();
        private Set<Gene> nextGenes = new HashSet<>();


        public Gene(ProcessStepSchedulingUnit processStepSchedulingUnit, DateTime startTime, boolean fixed) {
            this.fixed = fixed;
            this.processStepSchedulingUnit = processStepSchedulingUnit;
            this.executionInterval = new Interval(startTime, startTime.plus(processStepSchedulingUnit.getProcessStep().getServiceType().getServiceTypeResources().getMakeSpan()));
        }

        public void moveIntervalPlus(long delta) {
            executionInterval = new Interval(executionInterval.getStart().plus(delta + 1000), executionInterval.getEnd().plus(delta + 1000));
        }

        public void moveIntervalMinus(long delta) {
            executionInterval = new Interval(executionInterval.getStart().minus(delta + 1000), executionInterval.getEnd().minus(delta + 1000));
        }

        public Gene clone() {
            Gene clonedGene = new Gene(this.processStepSchedulingUnit.clone(), new DateTime(this.getExecutionInterval().getStartMillis()), this.isFixed());
            return clonedGene;
        }

        public void addNextGene(Gene nextGene) {
            if (this.nextGenes == null) {
                this.nextGenes = new HashSet<>();
            }
            this.nextGenes.add(nextGene);
        }

        public void addPreviousGene(Gene previousGene) {
            if (this.previousGenes == null) {
                this.previousGenes = new HashSet<>();
            }
            this.previousGenes.add(previousGene);
        }

        public Gene getLatestPreviousGene() {
            Gene returnGene = null;

            for (Gene previousGene : this.previousGenes) {
                if (returnGene == null || returnGene.getExecutionInterval().getEnd().isBefore(previousGene.getExecutionInterval().getEnd())) {
                    returnGene = previousGene;
                }
            }

            return returnGene;
        }

        public Gene getEarliestNextGene() {
            Gene returnGene = null;

            for (Gene nextGene : this.nextGenes) {
                if (returnGene == null || returnGene.getExecutionInterval().getStart().isAfter(nextGene.getExecutionInterval().getStart())) {
                    returnGene = nextGene;
                }
            }

            return returnGene;
        }

        @Override
        public String toString() {
            return "Gene{" +
                    "executionInterval=" + executionInterval +
                    ", fixed=" + fixed +
                    ", processStepSchedulingUnit=" + processStepSchedulingUnit +
                    "}, ";
        }
    }


}
