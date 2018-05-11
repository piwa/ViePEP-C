package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.operations;

import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.OrderMaintainer;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Interval;
import org.uncommons.maths.number.ConstantGenerator;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.maths.random.PoissonGenerator;
import org.uncommons.watchmaker.framework.EvolutionaryOperator;

import javax.print.CancelablePrintJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
public class SpaceAwareMutation implements EvolutionaryOperator<Chromosome> {

    private final NumberGenerator<Integer> mutationCountVariable;
    private final DateTime optimizationTime;
    private OrderMaintainer orderMaintainer = new OrderMaintainer();
    private Map<String, DateTime> maxTimeAfterDeadline;

    /**
     * Default is one mutation per candidate.
     * @param poissonGenerator
     * @param optimizationTime
     * @param maxTimeAfterDeadline
     */
    public SpaceAwareMutation(PoissonGenerator poissonGenerator, DateTime optimizationTime, Map<String, DateTime> maxTimeAfterDeadline) {
        this(1, optimizationTime, maxTimeAfterDeadline);
    }

    /**
     * @param mutationCount  The constant number of mutations
     *                       to apply to each row in a Sudoku solution.
     */
    public SpaceAwareMutation(int mutationCount, DateTime optimizationTime, Map<String, DateTime> maxTimeAfterDeadline) {
        this(new ConstantGenerator<>(mutationCount), optimizationTime, maxTimeAfterDeadline);
        if (mutationCount < 1) {
            throw new IllegalArgumentException("Mutation count must be at least 1.");
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
    public SpaceAwareMutation(NumberGenerator<Integer> mutationCount, DateTime optimizationTime, Map<String, DateTime> maxTimeAfterDeadline) {
        this.mutationCountVariable = mutationCount;
        this.optimizationTime = optimizationTime;
        this.maxTimeAfterDeadline = maxTimeAfterDeadline;
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
        while (mutationCount > 0 && counter < 100)
        {
            int rowIndex = random.nextInt(newCandidate.size());
            List<Chromosome.Gene> row = newCandidate.get(rowIndex);

            int geneIndex = random.nextInt(row.size());
            Chromosome.Gene gene = row.get(geneIndex);

            if(!gene.isFixed()) {

                Interval oldInterval = gene.getExecutionInterval();
                Chromosome.Gene previousGene = gene.getLatestPreviousGene();
                Chromosome.Gene nextGene = gene.getEarliestNextGene();

                DateTime endTimePreviousGene = null;
                DateTime startTimeNextGene = null;
                if(previousGene != null) {
                    endTimePreviousGene = previousGene.getExecutionInterval().getEnd();
                }
                else if(previousGene != null && this.optimizationTime.isAfter(oldInterval.getStart())){
                    endTimePreviousGene = previousGene.getExecutionInterval().getEnd();
                }
                else {
                    endTimePreviousGene = this.optimizationTime;
                }
                if(nextGene != null) {
                    startTimeNextGene = nextGene.getExecutionInterval().getStart();
                }
                else {
                    if(maxTimeAfterDeadline == null || maxTimeAfterDeadline.size() == 0 || maxTimeAfterDeadline.get(gene.getProcessStep().getWorkflowName()) == null) {
                        startTimeNextGene = this.optimizationTime.plusMinutes(10);
                    }
                    else {
                        startTimeNextGene = maxTimeAfterDeadline.get(gene.getProcessStep().getWorkflowName());
                    }

                    if(gene.getExecutionInterval().getEnd().isAfter(startTimeNextGene)) {       // TODO why?
//                        log.error("Deadline aware mutation is over deadline: " + candidate.toString(rowIndex) + ", startTimeNextGene=" + startTimeNextGene.toString());
                        startTimeNextGene = gene.getExecutionInterval().getEnd();
                    }

                }

                Duration previousDuration = new Duration(endTimePreviousGene, oldInterval.getStart());
                Duration nextDuration = new Duration(oldInterval.getEnd(), startTimeNextGene);


                int deltaTime = getRandomNumber((int)previousDuration.getMillis(), (int)nextDuration.getMillis(), random);


                Interval newInterval = new Interval(oldInterval.getStartMillis() + deltaTime, oldInterval.getEndMillis() + deltaTime);


                gene.setExecutionInterval(newInterval);
                mutationCount = mutationCount - 1;

            }
            counter = counter + 1;
        }

        Chromosome newChromosome = new Chromosome(newCandidate);
//        orderMaintainer.checkAndMaintainOrder(newChromosome);

//        if(!orderMaintainer.orderIsOk(newCandidate)) {
//            log.error("Order is not ok: " + newCandidate.toString());
//        }

        return newChromosome;
    }

    private int getRandomNumber(int minimumValue, int maximumValue, Random random) {
        return random.nextInt(maximumValue + 1 + minimumValue) - minimumValue;
    }
}
