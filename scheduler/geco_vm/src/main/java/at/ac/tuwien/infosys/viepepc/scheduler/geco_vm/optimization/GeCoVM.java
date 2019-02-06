package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ActionExecutor;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.*;
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
import org.uncommons.watchmaker.framework.selection.TournamentSelection;
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

    @Autowired
    private DeadlineAwareFactory chromosomeFactory;

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

        StopWatch stopwatch = new StopWatch();
        stopwatch.start("pre optimization tasks");


        this.optimizationEndTime = DateTime.now().plus(maxOptimizationDuration).plus(additionalOptimizationTime);


        SelectionStrategy<Object> selectionStrategy = new TournamentSelection(numberGenerator);
//        SelectionStrategy<Object> selectionStrategy = new RankSelection();
//        SelectionStrategy<Object> selectionStrategy = new TruncationSelection(0.85d);

        vmSelectionHelper.setOptimizationEndTime(optimizationEndTime);

        chromosomeFactory.initialize(workflowElements, this.optimizationEndTime);
        Map<String, DateTime> maxTimeAfterDeadline = chromosomeFactory.getMaxTimeAfterDeadline();

        Random rng = new MersenneTwisterRNG();
        List<EvolutionaryOperator<Chromosome>> operators = new ArrayList<>();


//        operators.add(new SpaceAwareCrossover(maxTimeAfterDeadline));
        operators.add(new SpaceAwareMutation(new PoissonGenerator(4, rng), optimizationEndTime, maxTimeAfterDeadline));

//        operators.add(new SpaceAwareDeploymentMutation(new PoissonGenerator(4, rng), optimizationEndTime));
//        operators.add(new SpaceAwareDeploymentCrossover(maxTimeAfterDeadline));
//        operators.add(new SpaceAwareVMSizeMutation(new PoissonGenerator(4, rng)));

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

        List<ServiceTypeSchedulingUnit> requiredServiceTypeList = optimizationUtility.getRequiredServiceTypes(winner, true);
        List<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnits = vmSelectionHelper.setVMsForServiceSchedulingUnit(requiredServiceTypeList);

        OptimizationResult optimizationResult = createOptimizationResult(winner, serviceTypeSchedulingUnits, evolutionLogger);
        stopwatch.stop();
        log.debug("optimization post time=" + stopwatch.getTotalTimeMillis());

        actionExecutor.unpauseTermination();

        return optimizationResult;

    }


    @Override
    public void initializeParameters() {

    }


}
