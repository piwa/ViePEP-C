package at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.operations;

import at.ac.tuwien.infosys.viepepc.scheduler.geco.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.OrderMaintainer;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.ServiceTypeSchedulingUnit;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.watchmaker.framework.operators.AbstractCrossover;

import java.util.*;

@Slf4j
public class SpaceAwareCrossover2 extends AbstractCrossover<Chromosome> {

    private OrderMaintainer orderMaintainer = new OrderMaintainer();
    private Map<String, DateTime> maxTimeAfterDeadline;
    private DateTime optimizationTime;
    private OptimizationUtility optimizationUtility;

    /**
     * Single-point cross-over.
     */
    public SpaceAwareCrossover2(Map<String, DateTime> maxTimeAfterDeadline, DateTime optimizationTime, OptimizationUtility optimizationUtility) {
        this(1, maxTimeAfterDeadline, optimizationTime, optimizationUtility);
    }


    /**
     * Multiple-point cross-over (fixed number of points).
     *
     * @param crossoverPoints The fixed number of cross-overs applied to each
     *                        pair of parents.
     */
    public SpaceAwareCrossover2(int crossoverPoints, Map<String, DateTime> maxTimeAfterDeadline, DateTime optimizationTime, OptimizationUtility optimizationUtility) {
        super(crossoverPoints);
        this.maxTimeAfterDeadline = maxTimeAfterDeadline;
        this.optimizationTime = optimizationTime;
        this.optimizationUtility = optimizationUtility;
    }


    /**
     * Multiple-point cross-over (variable number of points).
     *
     * @param crossoverPointsVariable Provides the (possibly variable) number of
     *                                cross-overs applied to each pair of parents.
     */
    public SpaceAwareCrossover2(NumberGenerator<Integer> crossoverPointsVariable, Map<String, DateTime> maxTimeAfterDeadline, DateTime optimizationTime, OptimizationUtility optimizationUtility) {
        super(crossoverPointsVariable);
        this.maxTimeAfterDeadline = maxTimeAfterDeadline;
        this.optimizationTime = optimizationTime;
        this.optimizationUtility = optimizationUtility;
    }


    @Override
    protected List<Chromosome> mate(Chromosome parent1, Chromosome parent2, int numberOfCrossoverPoints, Random random) {

        List<List<Chromosome.Gene>> clone1 = new ArrayList<>();
        Chromosome.cloneGenes(parent1, clone1);
        Chromosome offspring1Chromosome = new Chromosome(clone1);

        List<List<Chromosome.Gene>> clone2 = new ArrayList<>();
        Chromosome.cloneGenes(parent2, clone2);
        Chromosome offspring2Chromosome = new Chromosome(clone2);

        int amountOfRows = clone1.size();
        boolean rowClone1Changed = false;
        boolean rowClone2Changed = false;

        Map<String, Chromosome.Gene> processStepNameToCloneMap1 = fillMap(offspring1Chromosome);
        Map<String, Chromosome.Gene> processStepNameToCloneMap2 = fillMap(offspring2Chromosome);

//        for (int i = 0; i < numberOfCrossoverPoints; i++)
        for (int i = 0; i < 100; i++) {
            int rowIndex = random.nextInt(amountOfRows);
            List<Chromosome.Gene> rowClone1 = offspring1Chromosome.getRow(rowIndex);
            List<Chromosome.Gene> rowClone2 = offspring2Chromosome.getRow(rowIndex);

            List<Chromosome.Gene> rowParent1 = parent1.getRow(rowIndex);
            List<Chromosome.Gene> rowParent2 = parent2.getRow(rowIndex);

            int bound = rowClone1.size() - 1;
            int crossoverStartIndex = 0;
            if (bound > 0) {         // the nextInt can only be performed for bound >= 1
                crossoverStartIndex = random.nextInt(bound);
            }

            // crossover from rowParent2 to rowClone1 possible
            Chromosome.Gene parent2Gene = rowParent2.get(crossoverStartIndex);
            Chromosome.Gene clone1PreviousGene = rowClone1.get(crossoverStartIndex).getLatestPreviousGene();
            DateTime maxDeadlineExtensionClone1 = maxTimeAfterDeadline.get(parent2Gene.getProcessStep().getWorkflowName());

            rowClone1Changed = performCrossover(rowClone1Changed, processStepNameToCloneMap1, rowParent2, rowParent1, parent2Gene, clone1PreviousGene, maxDeadlineExtensionClone1);
//            if(!considerFirstContainerStartTime(offspring1Chromosome)) {
//                clone1 = new ArrayList<>();
//                Chromosome.cloneGenes(parent1, clone1);
//                offspring1Chromosome = new Chromosome(clone1);
//                rowClone1Changed = false;
//            }

            // crossover from rowParent1 to rowClone2 possible
            Chromosome.Gene parent1Gene = rowParent1.get(crossoverStartIndex);
            Chromosome.Gene clone2PreviousGene = rowClone2.get(crossoverStartIndex).getLatestPreviousGene();
            DateTime maxDeadlineExtensionClone2 = maxTimeAfterDeadline.get(parent1Gene.getProcessStep().getWorkflowName());


            rowClone2Changed = performCrossover(rowClone2Changed, processStepNameToCloneMap2, rowParent1, rowParent2, parent1Gene, clone2PreviousGene, maxDeadlineExtensionClone2);
//            if(!considerFirstContainerStartTime(offspring2Chromosome)) {
//                clone2 = new ArrayList<>();
//                Chromosome.cloneGenes(parent2, clone2);
//                offspring2Chromosome = new Chromosome(clone2);
//                rowClone2Changed = false;
//            }

            if (rowClone1Changed || rowClone2Changed) {
                break;
            }
        }

        orderMaintainer.orderIsOk(offspring1Chromosome.getGenes());
        orderMaintainer.orderIsOk(offspring2Chromosome.getGenes());

        List<Chromosome> result = new ArrayList<>();
//        if (rowClone1Changed) {
        result.add(offspring1Chromosome);
//        }
//        if (rowClone2Changed) {
        result.add(offspring2Chromosome);
//        }
        return result;
    }

    private boolean considerFirstContainerStartTime(Chromosome newChromosome) {
        List<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnits = this.optimizationUtility.getRequiredServiceTypes(newChromosome);
        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : serviceTypeSchedulingUnits) {
            DateTime deploymentStartTime = serviceTypeSchedulingUnit.getDeployStartTime();
            if (deploymentStartTime.isBefore(this.optimizationTime)) {
                return false;
            }
        }
        return true;
    }


    private boolean performCrossover(boolean rowClone2Changed, Map<String, Chromosome.Gene> processStepNameToCloneMap2, List<Chromosome.Gene> rowParent1, List<Chromosome.Gene> rowParent2, Chromosome.Gene parent1Gene, Chromosome.Gene clone2PreviousGene, DateTime maxDeadlineExtensionClone2) {
        if (clone2PreviousGene == null || (checkIfCrossoverPossible(clone2PreviousGene, parent1Gene.getLatestPreviousGene().getNextGenes()) && checkIfInDeadline(rowParent2, maxDeadlineExtensionClone2))) {

            Set<Chromosome.Gene> currentParentGenes = null;
            if (parent1Gene.getLatestPreviousGene() == null) {
                currentParentGenes = findStartGene(rowParent1);
            } else {
                currentParentGenes = parent1Gene.getLatestPreviousGene().getNextGenes();
            }

            for (Chromosome.Gene currentParentGene : currentParentGenes) {
                performCrossoverRec(currentParentGene, processStepNameToCloneMap2.get(currentParentGene.getProcessStep().getName()), processStepNameToCloneMap2);
            }

            rowClone2Changed = true;
//                if(!orderMaintainer.rowOrderIsOk(rowClone2)) {
//                    log.error("problem");
//                }
        }
        return rowClone2Changed;
    }

    private Set<Chromosome.Gene> findStartGene(List<Chromosome.Gene> rowParent2) {
        Set<Chromosome.Gene> startGenes = new HashSet<>();
        for (Chromosome.Gene gene : rowParent2) {
            if (gene.getPreviousGenes() == null || gene.getPreviousGenes().isEmpty()) {
                startGenes.add(gene);
            }
        }

        return startGenes;
    }

    private Map<String, Chromosome.Gene> fillMap(Chromosome chromosome) {

        Map<String, Chromosome.Gene> map = new HashMap<>();

        chromosome.getGenes().forEach(row -> row.forEach(gene -> map.put(gene.getProcessStep().getName(), gene)));

        return map;
    }

    private void performCrossoverRec(Chromosome.Gene parent2Gene, Chromosome.Gene currentClone, Map<String, Chromosome.Gene> processStepNameToCloneMap) {
        Interval newInterval = parent2Gene.getExecutionInterval();
        currentClone.setExecutionInterval(new Interval(newInterval.getStart().getMillis(), newInterval.getEnd().getMillis()));

        for (Chromosome.Gene nextGene : parent2Gene.getNextGenes()) {
            if (nextGene != null) {
                performCrossoverRec(nextGene, processStepNameToCloneMap.get(nextGene.getProcessStep().getName()), processStepNameToCloneMap);
            }
        }
    }

    private boolean checkIfCrossoverPossible(Chromosome.Gene clone1PreviousGene, Set<Chromosome.Gene> parent2Genes) {

        for (Chromosome.Gene parent2Gene : parent2Genes) {
            if (clone1PreviousGene.getExecutionInterval().getEnd().isAfter(parent2Gene.getExecutionInterval().getStart())) {
                return false;
            }
        }
        return true;

    }

    private boolean checkIfInDeadline(List<Chromosome.Gene> row, DateTime maxDeadlineExtension) {

        Chromosome.Gene lastGene = null;

        for (Chromosome.Gene gene : row) {
            if (lastGene == null || gene.getExecutionInterval().getEnd().isAfter(lastGene.getExecutionInterval().getEnd())) {
                lastGene = gene;
            }
        }

        return lastGene.getExecutionInterval().getEnd().isBefore(maxDeadlineExtension);

    }

}
