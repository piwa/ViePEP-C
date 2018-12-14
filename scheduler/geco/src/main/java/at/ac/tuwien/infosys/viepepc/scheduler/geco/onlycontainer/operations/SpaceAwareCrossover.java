package at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.operations;

import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.OrderMaintainer;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.Interval;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.watchmaker.framework.operators.AbstractCrossover;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Slf4j
public class SpaceAwareCrossover extends AbstractCrossover<Chromosome> {

    private OrderMaintainer orderMaintainer = new OrderMaintainer();

    /**
     * Single-point cross-over.
     */
    public SpaceAwareCrossover() {
        this(1);
    }


    /**
     * Multiple-point cross-over (fixed number of points).
     *
     * @param crossoverPoints The fixed number of cross-overs applied to each
     *                        pair of parents.
     */
    public SpaceAwareCrossover(int crossoverPoints) {
        super(crossoverPoints);
    }


    /**
     * Multiple-point cross-over (variable number of points).
     *
     * @param crossoverPointsVariable Provides the (possibly variable) number of
     *                                cross-overs applied to each pair of parents.
     */
    public SpaceAwareCrossover(NumberGenerator<Integer> crossoverPointsVariable) {
        super(crossoverPointsVariable);
    }


    @Override
    protected List<Chromosome> mate(Chromosome parent1, Chromosome parent2, int numberOfCrossoverPoints, Random random) {

        List<List<Chromosome.Gene>> clone1 = new ArrayList<>();
        Chromosome.cloneGenes(parent1, clone1);
        Chromosome offspring1Chromosome = new Chromosome(clone1);

        List<List<Chromosome.Gene>> clone2 = new ArrayList<>();
        Chromosome.cloneGenes(parent2, clone2);
        Chromosome offspring2Chromosome = new Chromosome(clone2);

        for (int i = 0; i < numberOfCrossoverPoints; i++) {
            int rowIndex = random.nextInt(clone1.size());
            List<Chromosome.Gene> rowClone1 = offspring1Chromosome.getRow(rowIndex);
            List<Chromosome.Gene> rowClone2 = offspring2Chromosome.getRow(rowIndex);

            List<Chromosome.Gene> rowParent1 = parent1.getRow(rowIndex);
            List<Chromosome.Gene> rowParent2 = parent2.getRow(rowIndex);

            int bound = rowClone1.size() - 1;
            int crossoverStartIndex = 0;
            if (bound > 0) {         // the nextInt can only be performed for bound >= 1
                crossoverStartIndex = random.nextInt(bound);
            }


            for (int j = crossoverStartIndex; j < rowClone1.size(); j++) {

                Chromosome.Gene parent2Gene = rowParent2.get(j);
                Chromosome.Gene clone1PreviousGene = rowClone1.get(j).getLatestPreviousGene();

                if (clone1PreviousGene == null || checkIfCrossoverPossible(clone1PreviousGene, parent2Gene.getLatestPreviousGene().getNextGenes())) {
//                if(clone1PreviousGene == null || clone1PreviousGene.getExecutionInterval().getEnd().isBefore(parent2Gene.getExecutionInterval().getStart())) {
                    Interval newInterval = rowParent2.get(j).getExecutionInterval();
                    rowClone1.get(j).setExecutionInterval(new Interval(newInterval.getStart().getMillis(), newInterval.getEnd().getMillis()));
                }

                Chromosome.Gene parent1Gene = rowParent1.get(j);
                Chromosome.Gene clone2PreviousGene = rowClone2.get(j).getLatestPreviousGene();


                if (clone2PreviousGene == null || checkIfCrossoverPossible(clone2PreviousGene, parent1Gene.getLatestPreviousGene().getNextGenes())) {
//                if(clone2PreviousGene == null || clone2PreviousGene.getExecutionInterval().getEnd().isBefore(parent1Gene.getExecutionInterval().getStart())) {
                    Interval newInterval = rowParent1.get(j).getExecutionInterval();
                    rowClone2.get(j).setExecutionInterval(new Interval(newInterval.getStart().getMillis(), newInterval.getEnd().getMillis()));
                }
            }

        }

        orderMaintainer.orderIsOk(offspring1Chromosome.getGenes());
        orderMaintainer.orderIsOk(offspring2Chromosome.getGenes());

        List<Chromosome> result = new ArrayList<>(2);
        result.add(offspring1Chromosome);
        result.add(offspring2Chromosome);
        return result;
    }

    private boolean checkIfCrossoverPossible(Chromosome.Gene clone1PreviousGene, Set<Chromosome.Gene> parent2Genes) {

        for (Chromosome.Gene parent2Gene : parent2Genes) {
            if (clone1PreviousGene.getExecutionInterval().getEnd().isAfter(parent2Gene.getExecutionInterval().getStart())) {
                return false;
            }
        }
        return true;

    }
}
