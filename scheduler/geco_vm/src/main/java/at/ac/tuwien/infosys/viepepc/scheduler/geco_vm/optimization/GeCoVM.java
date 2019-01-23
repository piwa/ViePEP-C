package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ActionExecutor;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.factory.DeadlineAwareFactory;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations.*;
import at.ac.tuwien.infosys.viepepc.scheduler.library.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.library.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.scheduler.library.SchedulerAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.uncommons.watchmaker.framework.selection.RankSelection;
import org.uncommons.watchmaker.framework.selection.TournamentSelection;
import org.uncommons.watchmaker.framework.selection.TruncationSelection;
import org.uncommons.watchmaker.framework.termination.ElapsedTime;

import java.util.*;
import java.util.concurrent.Future;

@Slf4j
@Component
@Profile("GeCo_VM")
@SuppressWarnings("Duplicates")
public class GeCoVM extends AbstractOnlyContainerOptimization implements SchedulerAlgorithm {

    @Autowired
    private VMSelectionHelper vmSelectionHelper;
    @Autowired
    private ActionExecutor actionExecutor;

    @Value("${max.optimization.duration}")
    private long maxOptimizationDuration = 60000;
    @Value("${additional.optimization.time}")
    private long additionalOptimizationTime = 5000;

    @Value("${population.size}")
    private int populationSize = 400;
    @Value("${population.elite.count}")
    private double eliteCountNumber = 0.05;
//    @Value("${stagnation.generation.limit}")
//    private int stagnationGenerationLimit = 15;

//    @Value("${use.single.shift.with.moving.mutation.min.value}")
//    private int singleShiftWithMovingMutationMin = 60000;
//    @Value("${use.single.shift.with.moving.mutation.max.value}")
//    private int singleShiftWithMovingMutationMax = 60000;
//    @Value("${use.single.shift.if.possible.mutation.min.value}")
//    private int singleShiftIfPossibleMutationMin = 60000;
//    @Value("${use.single.shift.if.possible.mutation.max.value}")
//    private int singleShiftIfPossibleMutationMax = 60000;

//    @Value("${container.default.deploy.time}")
//    private long containerDeploymentTime;

    @Autowired
    private DeadlineAwareFactory chromosomeFactory;

    private AdjustableNumberGenerator<Probability> numberGenerator = new AdjustableNumberGenerator<>(new Probability(0.85d));

    @Async
    public Future<OptimizationResult> asyncOptimize(DateTime tau_t) throws ProblemNotSolvedException {
        return new AsyncResult<>(optimize(tau_t));
    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        this.optimizationEndTime = DateTime.now();
        List<WorkflowElement> workflowElements = getRunningWorkflowInstancesSorted();

        if (workflowElements.size() == 0) {
            return new OptimizationResult();
        }

        StopWatch stopwatch = new StopWatch();
        stopwatch.start("pre optimization tasks");


        this.optimizationEndTime = this.optimizationEndTime.plus(maxOptimizationDuration).plus(additionalOptimizationTime);


        SelectionStrategy<Object> selectionStrategy = new TournamentSelection(numberGenerator);
//        SelectionStrategy<Object> selectionStrategy = new RankSelection();
//        SelectionStrategy<Object> selectionStrategy = new TruncationSelection(0.85d);

        vmSelectionHelper.setOptimizationEndTime(optimizationEndTime);

        chromosomeFactory.initialize(workflowElements, this.optimizationEndTime);
        Map<String, DateTime> maxTimeAfterDeadline = chromosomeFactory.getMaxTimeAfterDeadline();

        Random rng = new MersenneTwisterRNG();
        List<EvolutionaryOperator<Chromosome>> operators = new ArrayList<>();

        operators.add(new SpaceAwareMutation(1, optimizationEndTime, maxTimeAfterDeadline));
        operators.add(new SpaceAwareCrossover(maxTimeAfterDeadline));
        operators.add(new SpaceAwareDeploymentMutation(1, optimizationEndTime));
        operators.add(new SpaceAwareVMSizeMutation(new PoissonGenerator(4, rng), optimizationEndTime));
        operators.add(new SpaceAwareDeploymentCrossover(maxTimeAfterDeadline));


        int eliteCount = (int) Math.round(populationSize * eliteCountNumber);
        this.fitnessFunction.setOptimizationEndTime(this.optimizationEndTime);

        EvolutionaryOperator<Chromosome> pipeline = new EvolutionPipeline<>(operators);
        EvolutionEngine<Chromosome> engine = new GenerationalEvolutionEngine<>(chromosomeFactory, pipeline, fitnessFunction, selectionStrategy, rng);
        EvolutionLogger evolutionLogger = new EvolutionLogger();
        engine.addEvolutionObserver(evolutionLogger);

        stopwatch.stop();
        log.debug("optimization preparation time=" + stopwatch.getTotalTimeMillis());

        actionExecutor.pauseTermination();

        stopwatch = new StopWatch();
        stopwatch.start("optimization time");
        Chromosome winner = engine.evolve(populationSize, eliteCount, new ElapsedTime(maxOptimizationDuration));

        stopwatch.stop();
        log.debug("optimization time=" + stopwatch.getTotalTimeMillis());

        stopwatch = new StopWatch();
        stopwatch.start();
        OptimizationResult optimizationResult = createOptimizationResult(winner, workflowElements, evolutionLogger);
        stopwatch.stop();
        log.debug("optimization post time=" + stopwatch.getTotalTimeMillis());

        actionExecutor.unpauseTermination();

        return optimizationResult;

    }


    @Override
    public void initializeParameters() {

    }
}
