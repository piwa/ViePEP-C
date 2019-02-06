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

        Map<VirtualMachineSchedulingUnit, VirtualMachineSchedulingUnit> originalToCloneVirtualMachineSchedulingMap = new HashMap<>();
        for (Gene originalGene : this.getFlattenChromosome()) {
            Gene clonedGene = originalToCloneMap.get(originalGene);

            originalGene.getNextGenes().stream().map(originalToCloneMap::get).forEach(clonedGene::addNextGene);
            originalGene.getPreviousGenes().stream().map(originalToCloneMap::get).forEach(clonedGene::addPreviousGene);


            ProcessStepSchedulingUnit originalProcessStepSchedulingUnit = originalGene.getProcessStepSchedulingUnit();
            ProcessStepSchedulingUnit clonedProcessStepSchedulingUnit = originalProcessStepSchedulingUnit.clone();
            clonedGene.setProcessStepSchedulingUnit(clonedProcessStepSchedulingUnit);
            clonedProcessStepSchedulingUnit.setGene(clonedGene);

            VirtualMachineSchedulingUnit originalVirtualMachineSchedulingUnit = originalProcessStepSchedulingUnit.getVirtualMachineSchedulingUnit();
            if(originalVirtualMachineSchedulingUnit != null) {
                VirtualMachineSchedulingUnit clonedVirtualMachineSchedulingUnit = originalToCloneVirtualMachineSchedulingMap.get(originalVirtualMachineSchedulingUnit);
                if (clonedVirtualMachineSchedulingUnit == null) {
                    clonedVirtualMachineSchedulingUnit = originalProcessStepSchedulingUnit.getVirtualMachineSchedulingUnit().clone();
                    originalToCloneVirtualMachineSchedulingMap.put(originalVirtualMachineSchedulingUnit, clonedVirtualMachineSchedulingUnit);
                }
                clonedProcessStepSchedulingUnit.setVirtualMachineSchedulingUnit(clonedVirtualMachineSchedulingUnit);
                clonedVirtualMachineSchedulingUnit.getProcessStepSchedulingUnits().add(clonedProcessStepSchedulingUnit);
            }
        }

        return new Chromosome(offspring);
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
            return new Gene(this.processStepSchedulingUnit.clone(), new DateTime(this.getExecutionInterval().getStartMillis()), this.isFixed());
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

            String prevGeneIds = previousGenes.stream().map(g -> g.getProcessStepSchedulingUnit().getUid().toString().substring(0,8) + ", ").collect(Collectors.joining());
            String nextGeneIds = nextGenes.stream().map(g -> g.getProcessStepSchedulingUnit().getUid().toString().substring(0,8) + ", ").collect(Collectors.joining());

            return "Gene{" +
                    "executionInterval=" + executionInterval +
                    ", fixed=" + fixed +
                    ", prevGenes=" + prevGeneIds +
                    ", nextGenes=" + nextGeneIds +
                    ", processStepSchedulingUnit=" + processStepSchedulingUnit +
                    "}, ";
        }
    }


}
