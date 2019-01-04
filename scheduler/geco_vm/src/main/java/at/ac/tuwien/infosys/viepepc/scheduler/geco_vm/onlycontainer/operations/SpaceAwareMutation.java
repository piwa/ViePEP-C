package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.operations;

import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.OrderMaintainer;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities.ContainerSchedulingUnit;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Interval;
import org.springframework.context.ApplicationContext;
import org.uncommons.maths.number.ConstantGenerator;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.maths.random.PoissonGenerator;
import org.uncommons.watchmaker.framework.EvolutionaryOperator;

import java.util.*;

@Slf4j
@SuppressWarnings("Duplicates")
public class SpaceAwareMutation implements EvolutionaryOperator<Chromosome> {

    private final NumberGenerator<Integer> mutationCountVariable;
    private final DateTime optimizationEndTime;
    private OrderMaintainer orderMaintainer = new OrderMaintainer();
    private Map<String, DateTime> maxTimeAfterDeadline;
    private OptimizationUtility optimizationUtility;
    private VMSelectionHelper vmSelectionHelper;

    /**
     * Default is one mutation per candidate.
     *
     * @param poissonGenerator
     * @param optimizationEndTime
     * @param maxTimeAfterDeadline
     */
    public SpaceAwareMutation(PoissonGenerator poissonGenerator, DateTime optimizationEndTime, Map<String, DateTime> maxTimeAfterDeadline) {
        this(1, optimizationEndTime, maxTimeAfterDeadline);
    }

    /**
     * @param mutationCount The constant number of mutations
     *                      to apply to each row in a Sudoku solution.
     */
    public SpaceAwareMutation(int mutationCount, DateTime optimizationEndTime, Map<String, DateTime> maxTimeAfterDeadline) {
        this(new ConstantGenerator<>(mutationCount), optimizationEndTime, maxTimeAfterDeadline);
        if (mutationCount < 1) {
            throw new IllegalArgumentException("Mutation count must be at least 1.");
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
    public SpaceAwareMutation(NumberGenerator<Integer> mutationCount, DateTime optimizationEndTime, Map<String, DateTime> maxTimeAfterDeadline) {
        this.mutationCountVariable = mutationCount;
        this.optimizationEndTime = optimizationEndTime;
        this.maxTimeAfterDeadline = maxTimeAfterDeadline;

        ApplicationContext context = SpringContext.getApplicationContext();
        this.optimizationUtility = context.getBean(OptimizationUtility.class);
        this.vmSelectionHelper = context.getBean(VMSelectionHelper.class);
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

                Interval oldInterval = new Interval(gene.getExecutionInterval().getStartMillis(), gene.getExecutionInterval().getEndMillis());
                Chromosome.Gene previousGene = gene.getLatestPreviousGene();
                Chromosome.Gene nextGene = gene.getEarliestNextGene();

                DateTime endTimePreviousGene = null;
                DateTime startTimeNextGene = null;
                if (previousGene != null) {
                    endTimePreviousGene = previousGene.getExecutionInterval().getEnd();
                } else if (previousGene != null && this.optimizationEndTime.isAfter(oldInterval.getStart())) {
                    endTimePreviousGene = previousGene.getExecutionInterval().getEnd();
                } else {
                    endTimePreviousGene = this.optimizationEndTime;
                }

                if (nextGene != null) {
                    startTimeNextGene = nextGene.getExecutionInterval().getStart();
                } else {
                    if (maxTimeAfterDeadline == null || maxTimeAfterDeadline.size() == 0 || maxTimeAfterDeadline.get(gene.getProcessStepSchedulingUnit().getWorkflowName()) == null) {
                        startTimeNextGene = getLastProcessStep(row).getExecutionInterval().getEnd().plusMinutes(10);
                    } else {
                        startTimeNextGene = maxTimeAfterDeadline.get(gene.getProcessStepSchedulingUnit().getWorkflowName());
                    }

                    if (gene.getExecutionInterval().getEnd().isAfter(startTimeNextGene)) {       // TODO why?
                        startTimeNextGene = gene.getExecutionInterval().getEnd();
                    }

                }

                Duration previousDuration = new Duration(endTimePreviousGene, oldInterval.getStart());
                Duration nextDuration = new Duration(oldInterval.getEnd(), startTimeNextGene);


                try {
                    int deltaTime = getRandomNumber((int) previousDuration.getMillis(), (int) nextDuration.getMillis(), random);

                    Interval newInterval = new Interval(oldInterval.getStartMillis() + deltaTime, oldInterval.getEndMillis() + deltaTime);

                    gene.setExecutionInterval(newInterval);
                    boolean result = considerFirstContainerStartTime(new Chromosome(newCandidate), gene);

                    if (!orderMaintainer.orderIsOk(newCandidate)) {
                        result = false;
                    }

                    if (result) {
                        mutationCount = mutationCount - 1;
                    } else {
                        gene.setExecutionInterval(          oldInterval);
                    }

                } catch (Exception ex) {
                    log.error("Exception try to continue. previousDuration=" + previousDuration.getMillis() + ", nextDuration=" + nextDuration, ex);
                }

            }
            counter = counter + 1;
        }

        Chromosome newChromosome = new Chromosome(newCandidate);

        vmSelectionHelper.checkVmSizeAndSolveSpaceIssues(newChromosome);

        return newChromosome;
    }

    private int getRandomNumber(int minimumValue, int maximumValue, Random random) throws Exception {
        return random.nextInt(maximumValue + 1 + minimumValue) - minimumValue;
    }

    private Chromosome.Gene getLastProcessStep(List<Chromosome.Gene> row) {

        Chromosome.Gene lastGene = null;
        for (Chromosome.Gene gene : row) {
            if (lastGene == null || lastGene.getExecutionInterval().getEnd().isBefore(gene.getExecutionInterval().getEnd())) {
                lastGene = gene;
            }
        }
        return lastGene;
    }

    private boolean considerFirstContainerStartTime(Chromosome newChromosome, Chromosome.Gene movedGene) {
        List<ContainerSchedulingUnit> containerSchedulingUnits = this.optimizationUtility.createRequiredContainerSchedulingUnits(newChromosome);
        for (ContainerSchedulingUnit containerSchedulingUnit : containerSchedulingUnits) {
            if (containerSchedulingUnit.getProcessStepGenes().contains(movedGene)) {
                DateTime deploymentStartTime = containerSchedulingUnit.getDeployStartTime();

                if (deploymentStartTime.isBefore(this.optimizationEndTime) && containerSchedulingUnit.getFirstGene() == movedGene) {
                    return false;
                } else {
                    return true;
                }
            }
        }
        return true;
    }

}
