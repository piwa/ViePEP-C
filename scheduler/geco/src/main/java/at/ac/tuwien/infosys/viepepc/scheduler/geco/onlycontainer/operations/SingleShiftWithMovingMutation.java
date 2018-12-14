package at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.operations;

import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.OrderMaintainer;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.uncommons.maths.number.ConstantGenerator;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.watchmaker.framework.EvolutionaryOperator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
public class SingleShiftWithMovingMutation implements EvolutionaryOperator<Chromosome> {

    private final NumberGenerator<Integer> mutationCountVariable;
    private final NumberGenerator<Integer> mutationDeltaTimeVariable;
    private final DateTime optimizationTime;
    private OrderMaintainer orderMaintainer = new OrderMaintainer();

    /**
     * Default is one mutation per candidate.
     */
    public SingleShiftWithMovingMutation() {
        this(1, 1, DateTime.now());
    }

    /**
     * @param mutationCount  The constant number of mutations
     *                       to apply to each row in a Sudoku solution.
     * @param mutationAmount The constant number of positions by
     *                       which a list element will be displaced as a result of mutation.
     */
    public SingleShiftWithMovingMutation(int mutationCount, int mutationAmount, DateTime optimizationTime) {
        this(new ConstantGenerator<>(mutationCount), new ConstantGenerator<>(mutationAmount), optimizationTime);
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
     * @param mutationCount A random variable that provides a number
     *                      of mutations that will be applied to each row in an individual.
     */
    public SingleShiftWithMovingMutation(NumberGenerator<Integer> mutationCount, NumberGenerator<Integer> mutationDeltaTimeVariable, DateTime optimizationTime) {
        this.mutationCountVariable = mutationCount;
        this.mutationDeltaTimeVariable = mutationDeltaTimeVariable;
        this.optimizationTime = optimizationTime;
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
        int counter = 0;
        while (mutationCount > 0 && counter < 100) {
            int rowIndex = random.nextInt(newCandidate.size());
            List<Chromosome.Gene> row = newCandidate.get(rowIndex);

            int geneIndex = random.nextInt(row.size());
            Chromosome.Gene gene = row.get(geneIndex);

            if (!gene.isFixed()) {

                int deltaTime = mutationDeltaTimeVariable.nextValue();

                Interval oldInterval = gene.getExecutionInterval();
                Interval newInterval = new Interval(oldInterval.getStartMillis() + deltaTime, oldInterval.getEndMillis() + deltaTime);

                if (geneIndex == 0 && (newInterval.getStart().isAfter(optimizationTime) || newInterval.getStart().isAfterNow())) {
                    gene.setExecutionInterval(newInterval);
                    mutationCount = mutationCount - 1;

                    orderMaintainer.checkAndMaintainOrder(row);
                }

            }
            counter = counter + 1;

        }

        Chromosome newChromosome = new Chromosome(newCandidate);

        orderMaintainer.checkAndMaintainOrder(newChromosome);

//        if(!orderMaintainer.orderIsOk(newCandidate)) {
//            log.error("Order is not ok: " + newCandidate.toString());
//        }

        return newChromosome;
    }
}
