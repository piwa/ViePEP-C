package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer;

import org.joda.time.Interval;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.watchmaker.framework.operators.AbstractCrossover;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TimeExchangeCrossover extends AbstractCrossover<Chromosome> {

    /**
     * Single-point cross-over.
     */
    public TimeExchangeCrossover() {
        this(1);
    }


    /**
     * Multiple-point cross-over (fixed number of points).
     *
     * @param crossoverPoints The fixed number of cross-overs applied to each
     *                        pair of parents.
     */
    public TimeExchangeCrossover(int crossoverPoints) {
        super(crossoverPoints);
    }


    /**
     * Multiple-point cross-over (variable number of points).
     *
     * @param crossoverPointsVariable Provides the (possibly variable) number of
     *                                cross-overs applied to each pair of parents.
     */
    public TimeExchangeCrossover(NumberGenerator<Integer> crossoverPointsVariable) {
        super(crossoverPointsVariable);
    }


    @Override
    protected List<Chromosome> mate(Chromosome parent1, Chromosome parent2, int numberOfCrossoverPoints, Random random) {

        List<List<Chromosome.Gene>> offspring1 = new ArrayList<>();
        Chromosome.cloneGenes(parent1, offspring1);
        Chromosome offspring1Chromosome = new Chromosome(offspring1);

        List<List<Chromosome.Gene>> offspring2 = new ArrayList<>();
        Chromosome.cloneGenes(parent2, offspring2);
        Chromosome offspring2Chromosome = new Chromosome(offspring2);

        for (int i = 0; i < numberOfCrossoverPoints; i++)
        {
            int rowIndex = random.nextInt(parent1.getRowAmount());
            List<Chromosome.Gene> rowOffspring1 = offspring1Chromosome.getRow(rowIndex);
            List<Chromosome.Gene> rowOffspring2 = offspring2Chromosome.getRow(rowIndex);

            int bound = rowOffspring1.size() - 2;
            if(bound <= 0 ){
                bound = 1;
            }
            int crossoverStartIndex = random.nextInt(bound);
            int crossoverEndIndex = (crossoverStartIndex + random.nextInt(rowOffspring1.size()));

            for(int j = crossoverStartIndex; j < crossoverEndIndex; j++) {

                Chromosome.Gene tempGene = Chromosome.Gene.clone(rowOffspring1.get(j));
                rowOffspring1.add(j, rowOffspring2.get(j));
                rowOffspring2.add(j, tempGene);
            }



            Interval overlapNextGene = null;
            if(crossoverEndIndex < rowOffspring1.size() - 1) {
                Chromosome.Gene lastCrossoverGene = rowOffspring1.get(crossoverEndIndex);
                Chromosome.Gene nextGene = rowOffspring1.get(crossoverEndIndex + 1);
                overlapNextGene = lastCrossoverGene.getExecutionInterval().overlap(nextGene.getExecutionInterval());

                if(overlapNextGene != null) {
                    Chromosome.moveAllGenesOfARow(rowOffspring1, crossoverEndIndex + 1, overlapNextGene.toDurationMillis(), true);
                }
            }

            Interval overlapPreviousGene = null;
            if(crossoverStartIndex > 0) {
                Chromosome.Gene lastCrossoverGene = rowOffspring1.get(crossoverEndIndex);
                Chromosome.Gene previousGene = rowOffspring1.get(crossoverStartIndex - 1);
                overlapPreviousGene = lastCrossoverGene.getExecutionInterval().overlap(previousGene.getExecutionInterval());

                if(overlapPreviousGene != null) {
                    Chromosome.moveAllGenesOfARow(rowOffspring2, crossoverStartIndex, overlapPreviousGene.toDurationMillis(), true);
                }
            }

        }

        List<Chromosome> result = new ArrayList<>(2);
        result.add(offspring1Chromosome);
        result.add(offspring2Chromosome);
        return result;
    }

}
