package at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.factory.DeadlineAwareFactory;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.factory.SimpleFactory;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.operations.*;
import at.ac.tuwien.infosys.viepepc.scheduler.library.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.library.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.scheduler.library.SchedulerAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.uncommons.maths.number.AdjustableNumberGenerator;
import org.uncommons.maths.random.MersenneTwisterRNG;
import org.uncommons.maths.random.PoissonGenerator;
import org.uncommons.maths.random.Probability;
import org.uncommons.watchmaker.framework.*;
import org.uncommons.watchmaker.framework.operators.EvolutionPipeline;
import org.uncommons.watchmaker.framework.selection.TournamentSelection;
import org.uncommons.watchmaker.framework.termination.ElapsedTime;
import org.uncommons.watchmaker.framework.termination.Stagnation;

import java.util.*;
import java.util.concurrent.Future;

@Slf4j
@Component
@Profile("OnlyContainerGeneticAlgorithm")
public class OnlyContainerImpl extends AbstractOnlyContainerOptimization implements SchedulerAlgorithm {


    @Value("${use.deadline.aware.factory}")
    private boolean deadlineAwareFactory = true;
    @Value("${use.time.exchange.crossover}")
    private boolean timeExchangeCrossover = false;
    @Value("${use.space.aware.crossover}")
    private boolean spaceAwareCrossover = true;
    @Value("${use.space.aware.crossover.2}")
    private boolean spaceAwareCrossover2 = true;
    @Value("${use.space.aware.mutation}")
    private boolean spaceAwareMutation = true;
    @Value("${use.single.shift.with.moving.mutation}")
    private boolean singleShiftWithMovingMutation = false;
    @Value("${use.single.shift.if.possible.mutation}")
    private boolean singleShiftIfPossibleMutation = false;
    @Value("${use.with.optimization.timeout}")
    private boolean withOptimizationTimeout = true;

    @Value("${max.optimization.duration}")
    private long maxOptimizationDuration = 60000;
    @Value("${additional.optimization.time}")
    private long additionalOptimizationTime = 5000;

    @Value("${population.size}")
    private int populationSize = 400;
    @Value("${population.elite.count}")
    private double eliteCountNumber = 0.05;
    @Value("${stagnation.generation.limit}")
    private int stagnationGenerationLimit = 15;

    @Value("${use.single.shift.with.moving.mutation.min.value}")
    private int singleShiftWithMovingMutationMin = 60000;
    @Value("${use.single.shift.with.moving.mutation.max.value}")
    private int singleShiftWithMovingMutationMax = 60000;
    @Value("${use.single.shift.if.possible.mutation.min.value}")
    private int singleShiftIfPossibleMutationMin = 60000;
    @Value("${use.single.shift.if.possible.mutation.max.value}")
    private int singleShiftIfPossibleMutationMax = 60000;

    @Value("${container.default.startup.time}")
    private long defaultContainerStartupTime;
    @Value("${container.default.deploy.time}")
    private long defaultContainerDeployTime;
    @Value("${only.container.deploy.time}")
    private long onlyContainerDeploymentTime = 40000;

    @Value("${deadline.aware.factory.allowed.penalty.points}")
    private int allowedPenaltyPoints;

    @Value("${slack.webhook}")
    private String slackWebhook;


    private CandidateFactory<Chromosome> chromosomeFactory;

    private AdjustableNumberGenerator<Probability> numberGenerator = new AdjustableNumberGenerator<>(new Probability(0.85d));

    private Map<String, DateTime> maxTimeAfterDeadline = new HashMap<>();

    @Async
    public Future<OptimizationResult> asyncOptimize(DateTime tau_t) throws ProblemNotSolvedException {
        return new AsyncResult<>(optimize(tau_t));
    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        this.optimizationTime = DateTime.now();
        List<WorkflowElement> workflowElements = getRunningWorkflowInstancesSorted();

        if (workflowElements.size() == 0) {
            return new OptimizationResult();
        }

        StopWatch stopwatch = new StopWatch();
        stopwatch.start("pre optimization tasks");


        if (withOptimizationTimeout) {
            this.optimizationTime = this.optimizationTime.plus(maxOptimizationDuration).plus(additionalOptimizationTime);
        }

        int eliteCount = (int) Math.round(populationSize * eliteCountNumber);
        SelectionStrategy<Object> selectionStrategy = new TournamentSelection(numberGenerator);

        if (deadlineAwareFactory) {
            chromosomeFactory = new DeadlineAwareFactory(workflowElements, this.optimizationTime, defaultContainerDeployTime, defaultContainerStartupTime, withOptimizationTimeout, optimizationUtility, onlyContainerDeploymentTime, allowedPenaltyPoints, slackWebhook);
            maxTimeAfterDeadline = ((DeadlineAwareFactory) chromosomeFactory).getMaxTimeAfterDeadline();
        } else {
            chromosomeFactory = new SimpleFactory(workflowElements, this.optimizationTime, defaultContainerDeployTime, defaultContainerStartupTime, withOptimizationTimeout);
        }


        Random rng = new MersenneTwisterRNG();
        List<EvolutionaryOperator<Chromosome>> operators = new ArrayList<>(2);

        if (singleShiftWithMovingMutation) {
            operators.add(new SingleShiftWithMovingMutation(new PoissonGenerator(4, rng), new DiscreteUniformRangeGenerator(singleShiftWithMovingMutationMin, singleShiftWithMovingMutationMax, rng), optimizationTime));
        }
        if (singleShiftIfPossibleMutation) {
            operators.add(new SingleShiftIfPossibleMutation(new PoissonGenerator(4, rng), new DiscreteUniformRangeGenerator(singleShiftIfPossibleMutationMin, singleShiftIfPossibleMutationMax, rng), optimizationTime));
        }
        if (spaceAwareMutation) {
            operators.add(new SpaceAwareMutation(new PoissonGenerator(4, rng), optimizationTime, maxTimeAfterDeadline, optimizationUtility, onlyContainerDeploymentTime));
        }
        if (timeExchangeCrossover) {
            operators.add(new TimeExchangeCrossover());
        }
        if (spaceAwareCrossover) {
            operators.add(new SpaceAwareCrossover());
        }
        if (spaceAwareCrossover2) {
            operators.add(new SpaceAwareCrossover2(maxTimeAfterDeadline, optimizationTime, optimizationUtility));
        }


        EvolutionaryOperator<Chromosome> pipeline = new EvolutionPipeline<>(operators);
        EvolutionEngine<Chromosome> engine = new GenerationalEvolutionEngine<>(chromosomeFactory, pipeline, fitnessFunction, selectionStrategy, rng);

        stopwatch.stop();
        log.debug("optimization preparation time=" + stopwatch.getTotalTimeMillis());

        stopwatch = new StopWatch();
        stopwatch.start("optimization time");
        Chromosome winner = null;
        if (withOptimizationTimeout) {
            winner = engine.evolve(populationSize, eliteCount, new ElapsedTime(maxOptimizationDuration));
        } else {
            winner = engine.evolve(populationSize, eliteCount, new Stagnation(stagnationGenerationLimit, false));
        }
        stopwatch.stop();
        log.debug("optimization time=" + stopwatch.getTotalTimeMillis());

        stopwatch = new StopWatch();
        stopwatch.start();
        OptimizationResult optimizationResult = createOptimizationResult(winner, workflowElements);
        stopwatch.stop();
        log.debug("optimization post time=" + stopwatch.getTotalTimeMillis());
        return optimizationResult;

    }


    @Override
    public void initializeParameters() {

    }
}
