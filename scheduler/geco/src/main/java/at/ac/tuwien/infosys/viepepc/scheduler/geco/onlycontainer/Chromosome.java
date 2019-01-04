package at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.*;

@Slf4j
public class Chromosome {

    @Getter
    private final List<List<Gene>> genes;

    public Chromosome(List<List<Chromosome.Gene>> genes) {
        this.genes = genes;
    }

    public Interval getInterval(int process, int step) {
        return genes.get(process).get(step).getExecutionInterval();
    }

    public List<Chromosome.Gene> getRow(int row) {
        return genes.get(row);
    }

    public int getRowAmount() {
        return genes.size();
    }

    public static void cloneGenes(Chromosome chromosome, List<List<Chromosome.Gene>> offspring) {

        Map<Gene, Gene> originalToCloneMap = new HashMap<>();

        for (List<Chromosome.Gene> subChromosome : chromosome.getGenes()) {
            List<Chromosome.Gene> newSubChromosome = new ArrayList<>();
            for (Chromosome.Gene gene : subChromosome) {
                Gene clonedGene = Chromosome.Gene.clone(gene);
                originalToCloneMap.put(gene, clonedGene);
                newSubChromosome.add(clonedGene);
            }
            offspring.add(newSubChromosome);
        }

        for (List<Chromosome.Gene> subChromosome : chromosome.getGenes()) {
            for (Chromosome.Gene originalGene : subChromosome) {
                Chromosome.Gene clonedGene = originalToCloneMap.get(originalGene);
                Set<Chromosome.Gene> originalNextGenes = originalGene.getNextGenes();
                Set<Chromosome.Gene> originalPreviousGenes = originalGene.getPreviousGenes();

                originalNextGenes.stream().map(originalToCloneMap::get).forEach(clonedGene::addNextGene);

                originalPreviousGenes.stream().map(originalToCloneMap::get).forEach(clonedGene::addPreviousGene);

            }
        }


        return;
    }


    public static void moveGeneAndNextGenesByFixedTime(Chromosome.Gene currentGene, long deltaTime) {
        currentGene.moveIntervalPlus(deltaTime);

        currentGene.getNextGenes().forEach(gene -> {
            if (gene != null) {
                moveNextGenesByFixedTime(gene, deltaTime);
            }
        });
    }

    private static void moveNextGenesByFixedTime(Chromosome.Gene currentGene, long deltaTime) {
        if (currentGene.getLatestPreviousGene() == null || currentGene.getExecutionInterval().getStart().isBefore(currentGene.getLatestPreviousGene().getExecutionInterval().getStart())) {
            currentGene.moveIntervalPlus(deltaTime);
        }
        currentGene.getNextGenes().forEach(gene -> {
            if (gene != null) {
                moveNextGenesByFixedTime(gene, deltaTime);
            }
        });
    }

    //    public static void moveGeneAndNextGenesIfNeeded(Chromosome.Gene currentGene, long deltaTime) {
//        currentGene.moveIntervalPlus(deltaTime);
//
//        currentGene.getNextGenes().forEach(gene -> {
//            if(gene != null) {
//                moveNextGenesIfNeeded(gene);
//            }
//        });
//    }

//    private static void moveNextGenesIfNeeded(Chromosome.Gene currentGene) {
//        if(currentGene.getExecutionInterval().getStart().isBefore(currentGene.getLatestPreviousGene().getExecutionInterval().getStart())) {
//            long deltaTime = new Duration(currentGene.getExecutionInterval().getStart(), currentGene.getLatestPreviousGene().getExecutionInterval().getStart()).getMillis();
//            currentGene.moveIntervalPlus(deltaTime);
//        }
//        currentGene.getNextGenes().forEach(gene -> {
//            if(gene != null) {
//                moveNextGenesIfNeeded(gene);
//            }
//        });
//    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (List<Chromosome.Gene> row : genes) {
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
        private ProcessStep processStep;
        private Set<Chromosome.Gene> previousGenes = new HashSet<>();
        private Set<Gene> nextGenes = new HashSet<>();


        public Gene(ProcessStep processStep, DateTime startTime, boolean fixed) {
            this.fixed = fixed;
            this.processStep = processStep;
            this.executionInterval = new Interval(startTime, startTime.plus(processStep.getExecutionTime()));
        }

        public void moveIntervalPlus(long delta) {
            executionInterval = new Interval(executionInterval.getStart().plus(delta + 1000), executionInterval.getEnd().plus(delta + 1000));
        }

        public void moveIntervalMinus(long delta) {
            executionInterval = new Interval(executionInterval.getStart().minus(delta + 1000), executionInterval.getEnd().minus(delta + 1000));
        }

        public static Gene clone(Gene gene) {
            Gene clonedGene = new Gene(gene.getProcessStep(), gene.getExecutionInterval().getStart(), gene.isFixed());
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

        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("{");
            builder.append("processStepSchedulingUnit=" + getProcessStep().getName() + ", ");
            builder.append("serviceType=" + getProcessStep().getServiceType().getName() + ", ");
            builder.append("start=" + getExecutionInterval().getStart().toString() + ", ");
            builder.append("end=" + getExecutionInterval().getEnd().toString() + ", ");
            builder.append("fixed=" + isFixed());
            builder.append("} ");
            return builder.toString();
        }
    }


}
