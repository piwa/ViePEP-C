package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ActionExecutor;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.*;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.factory.DeadlineAwareFactoryStartTime;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.factory.DeadlineAwareFactoryVM;
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
import org.uncommons.watchmaker.framework.selection.TournamentSelection;
import org.uncommons.watchmaker.framework.termination.ElapsedTime;

import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Slf4j
@Component
@Profile("GeCo_VM")
@SuppressWarnings("Duplicates")
public class GeCoVM extends AbstractOnlyContainerOptimization implements SchedulerAlgorithm {

    @Autowired
    private VMSelectionHelper vmSelectionHelper;
    @Autowired
    private ActionExecutor actionExecutor;

    @Value("${virtual.machine.default.deploy.time}")
    private long virtualMachineDeploymentTime;
    @Value("${container.default.deploy.time}")
    private long containerDeploymentTime;

    @Value("${max.optimization.duration}")
    private long maxOptimizationDuration = 60000;
    @Value("${additional.optimization.time}")
    private long additionalOptimizationTime = 5000;

    @Value("${population.size}")
    private int populationSize = 400;
    @Value("${population.elite.count}")
    private double eliteCountNumber = 0.05;

    private EvolutionLogger evolutionLogger = new EvolutionLogger();
    private EvolutionLogger2 evolutionLogger2 = new EvolutionLogger2();

    @Autowired
    private DeadlineAwareFactoryStartTime chromosomeFactoryStartTime;
    @Autowired
    private DeadlineAwareFactoryVM chromosomeFactoryVM;

    private AdjustableNumberGenerator<Probability> numberGenerator = new AdjustableNumberGenerator<>(new Probability(0.85d));

    @Async
    public Future<OptimizationResult> asyncOptimize(DateTime tau_t) throws ProblemNotSolvedException {
        return new AsyncResult<>(optimize(tau_t));
    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        List<WorkflowElement> workflowElements = getRunningWorkflowInstancesSorted();

        if (workflowElements.size() == 0) {
            return new OptimizationResult();
        }

        this.optimizationEndTime = DateTime.now().plus(maxOptimizationDuration).plus(additionalOptimizationTime);

        StopWatch stopwatch = new StopWatch();
        stopwatch.start();

        actionExecutor.pauseTermination();

        log.info("StartTime optimization");
        Chromosome tempResult = optimizeStartTime(workflowElements);
        log.info("Deployment optimization");
        List<ServiceTypeSchedulingUnit> requiredServiceTypeList = optimizationUtility.getRequiredServiceTypes(tempResult, true);
        requiredServiceTypeList = optimizeAssignedVM(requiredServiceTypeList).getFlattenChromosome();

        stopwatch.stop();
        log.debug("optimization time=" + stopwatch.getTotalTimeMillis());

        stopwatch = new StopWatch();
        stopwatch.start();

        OptimizationResult optimizationResult = createOptimizationResult(tempResult, requiredServiceTypeList);
        stopwatch.stop();
        log.debug("optimization post time=" + stopwatch.getTotalTimeMillis());

        actionExecutor.unpauseTermination();
//        log.error("optimization done");
        return optimizationResult;


    }

    private Chromosome optimizeStartTime (List<WorkflowElement> workflowElements)  {

        StopWatch stopwatch = new StopWatch();
        stopwatch.start("pre optimization tasks");

        SelectionStrategy<Object> selectionStrategy = new TournamentSelection(numberGenerator);
//        SelectionStrategy<Object> selectionStrategy = new RankSelection();
//        SelectionStrategy<Object> selectionStrategy = new TruncationSelection(0.85d);

        vmSelectionHelper.setOptimizationEndTime(optimizationEndTime);

        chromosomeFactoryStartTime.initialize(workflowElements, this.optimizationEndTime);
        Map<String, DateTime> maxTimeAfterDeadline = chromosomeFactoryStartTime.getMaxTimeAfterDeadline();

        Random rng = new MersenneTwisterRNG();
        List<EvolutionaryOperator<Chromosome>> operators = new ArrayList<>();


        operators.add(new SpaceAwareCrossover(maxTimeAfterDeadline));
        operators.add(new SpaceAwareMutation(new PoissonGenerator(4, rng), optimizationEndTime, maxTimeAfterDeadline));

        int eliteCount = (int) Math.round(populationSize * eliteCountNumber);
        this.fitnessFunctionStartTime.setOptimizationEndTime(this.optimizationEndTime);

        EvolutionaryOperator<Chromosome> pipeline = new EvolutionPipeline<>(operators);
        EvolutionEngine<Chromosome> engine = new GenerationalEvolutionEngine<>(chromosomeFactoryStartTime, pipeline, fitnessFunctionStartTime, selectionStrategy, rng);

        engine.addEvolutionObserver(evolutionLogger);

        stopwatch.stop();
        log.debug("optimization preparation time=" + stopwatch.getTotalTimeMillis());

        stopwatch = new StopWatch();
        stopwatch.start("optimization time");
        return engine.evolve(populationSize, eliteCount, new ElapsedTime(maxOptimizationDuration / 2));
    }

    private Chromosome2 optimizeAssignedVM(List<ServiceTypeSchedulingUnit> requiredServiceTypeList)  {

        StopWatch stopwatch = new StopWatch();
        stopwatch.start("pre optimization tasks");

        SelectionStrategy<Object> selectionStrategy = new TournamentSelection(numberGenerator);
//        SelectionStrategy<Object> selectionStrategy = new RankSelection();
//        SelectionStrategy<Object> selectionStrategy = new TruncationSelection(0.85d);

        vmSelectionHelper.setOptimizationEndTime(optimizationEndTime);

        chromosomeFactoryVM.initialize(requiredServiceTypeList);


        Random rng = new MersenneTwisterRNG();
        List<EvolutionaryOperator<Chromosome2>> operators = new ArrayList<>();


        operators.add(new SpaceAwareDeploymentMutation(new PoissonGenerator(4, rng), optimizationEndTime));
//        operators.add(new SpaceAwareDeploymentCrossover(maxTimeAfterDeadline));


        int eliteCount = (int) Math.round(populationSize * eliteCountNumber);
        this.fitnessFunctionVM.setOptimizationEndTime(this.optimizationEndTime);

        EvolutionaryOperator<Chromosome2> pipeline = new EvolutionPipeline<>(operators);
        EvolutionEngine<Chromosome2> engine = new GenerationalEvolutionEngine<>(chromosomeFactoryVM, pipeline, fitnessFunctionVM, selectionStrategy, rng);

        engine.addEvolutionObserver(evolutionLogger2);

        stopwatch.stop();
        log.debug("optimization preparation time=" + stopwatch.getTotalTimeMillis());

        stopwatch = new StopWatch();
        stopwatch.start("optimization time");
        return engine.evolve(populationSize, eliteCount, new ElapsedTime(maxOptimizationDuration / 2));
    }


    @Override
    public void initializeParameters() {

    }


}
