package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer;

import org.joda.time.Interval;
import org.uncommons.maths.number.ConstantGenerator;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.watchmaker.framework.EvolutionaryOperator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SingleShiftMutation implements EvolutionaryOperator<Chromosome> {

    private final NumberGenerator<Integer> mutationCountVariable;
    private final NumberGenerator<Integer> mutationDeltaTimeVariable;

    /**
     * Default is one mutation per candidate.
     */
    public SingleShiftMutation() {
        this(1, 1);
    }

    /**
     * @param mutationCount  The constant number of mutations
     *                       to apply to each row in a Sudoku solution.
     * @param mutationAmount The constant number of positions by
     *                       which a list element will be displaced as a result of mutation.
     */
    public SingleShiftMutation(int mutationCount, int mutationAmount) {
        this(new ConstantGenerator<>(mutationCount), new ConstantGenerator<>(mutationAmount));
        if (mutationCount < 1) {
            throw new IllegalArgumentException("Mutation count must be at least 1.");
        } else if (mutationAmount < 1) {
            throw new IllegalArgumentException("Mutation amount must be at least 1.");
        }
    }

    /**
     * Typically the mutation count will be from a Poisson distribution.
     * The mutation amount can be from any discrete probability distribution
     * and can include negative values.
     *
     * @param mutationCount  A random variable that provides a number
     *                       of mutations that will be applied to each row in an individual.
     */
    public SingleShiftMutation(NumberGenerator<Integer> mutationCount, NumberGenerator<Integer> mutationDeltaTimeVariable) {
        this.mutationCountVariable = mutationCount;
        this.mutationDeltaTimeVariable = mutationDeltaTimeVariable;
    }

    @Override
    public List<Chromosome> apply(List<Chromosome> selectedCandidates, Random random) {

        List<Chromosome> mutatedCandidates = new ArrayList<>();
        for (Chromosome candidate : selectedCandidates) {
            mutatedCandidates.add(mutate(candidate, random));
        }

        return mutatedCandidates;

    }

    private Chromosome mutate(Chromosome candidate, Random random) {


        List<List<Chromosome.Gene>> newCandidate = new ArrayList<>();
        Chromosome.cloneGenes(candidate, newCandidate);

        int mutationCount = Math.abs(mutationCountVariable.nextValue());
        while (mutationCount > 0)
        {
            int rowIndex = random.nextInt(candidate.getGenes().size());
            List<Chromosome.Gene> row = candidate.getRow(rowIndex);

            int geneIndex = random.nextInt(row.size());
            Chromosome.Gene gene = row.get(geneIndex);

            if(!gene.isFixed()) {

                int deltaTime = mutationDeltaTimeVariable.nextValue();

                Interval oldInterval = gene.getExecutionInterval();
                Interval newInterval = new Interval(oldInterval.getStartMillis() + deltaTime, oldInterval.getEndMillis() + deltaTime);

                Interval overlapNextGene = null;
                if(geneIndex < row.size() - 1) {
                    Chromosome.Gene nextGene = row.get(geneIndex + 1);
                    overlapNextGene = newInterval.overlap(nextGene.getExecutionInterval());
                }

                Interval overlapPreviousGene = null;
                if(geneIndex > 0) {
                    Chromosome.Gene previousGene = row.get(geneIndex - 1);
                    overlapPreviousGene = newInterval.overlap(previousGene.getExecutionInterval());
                }

                if(overlapNextGene == null && overlapPreviousGene == null) {
                    gene.setExecutionInterval(newInterval);
                    mutationCount = mutationCount - 1;
                }

            }

        }

        return new Chromosome(newCandidate);
    }
}
