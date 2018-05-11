package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.factory.DeadlineAwareFactory;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.factory.SimpleFactory;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.operations.*;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.withvm.AbstractHeuristicImpl;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

@Slf4j
public class OnlyContainerImpl extends AbstractHeuristicImpl implements ProcessInstancePlacementProblem {

    @Autowired
    private FitnessFunction fitnessFunction;
    @Autowired
    private OptimizationUtility optimizationUtility;

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
    private long additionalOptimizationTime = 20000;


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

    private CandidateFactory<Chromosome> chromosomeFactory;

    private AdjustableNumberGenerator<Probability> numberGenerator = new AdjustableNumberGenerator<>(new Probability(0.85d));
    private DateTime optimizationTime;
    private Map<String, DateTime> maxTimeAfterDeadline = new HashMap<>();


    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        List<WorkflowElement> workflowElements = getRunningWorkflowInstancesSorted();

        if (workflowElements.size() == 0) {
            return new OptimizationResultImpl();
        }

        this.optimizationTime = DateTime.now();

        if(withOptimizationTimeout) {
            this.optimizationTime = this.optimizationTime.plus(maxOptimizationDuration).plus(additionalOptimizationTime);
        }

        int eliteCount = (int) Math.round(populationSize * eliteCountNumber);
        SelectionStrategy<Object> selectionStrategy = new TournamentSelection(numberGenerator);

        if(deadlineAwareFactory) {
            chromosomeFactory = new DeadlineAwareFactory(workflowElements, this.optimizationTime, defaultContainerDeployTime, defaultContainerStartupTime, withOptimizationTimeout);
            maxTimeAfterDeadline = ((DeadlineAwareFactory) chromosomeFactory).getMaxTimeAfterDeadline();
        }
        else {
            chromosomeFactory = new SimpleFactory(workflowElements, this.optimizationTime, defaultContainerDeployTime, defaultContainerStartupTime, withOptimizationTimeout);
        }


        Random rng = new MersenneTwisterRNG();
        List<EvolutionaryOperator<Chromosome>> operators = new ArrayList<>(2);

        if(timeExchangeCrossover) {
            operators.add(new TimeExchangeCrossover());
        }
        if(spaceAwareCrossover) {
            operators.add(new SpaceAwareCrossover());
        }
        if(spaceAwareCrossover2) {
            operators.add(new SpaceAwareCrossover2(maxTimeAfterDeadline, optimizationTime));
        }
        if(singleShiftWithMovingMutation) {
            operators.add(new SingleShiftWithMovingMutation(new PoissonGenerator(4, rng), new DiscreteUniformRangeGenerator(singleShiftWithMovingMutationMin, singleShiftWithMovingMutationMax, rng), optimizationTime));
        }
        if(singleShiftIfPossibleMutation) {
            operators.add(new SingleShiftIfPossibleMutation(new PoissonGenerator(4, rng), new DiscreteUniformRangeGenerator(singleShiftIfPossibleMutationMin, singleShiftIfPossibleMutationMax, rng), optimizationTime));
        }
        if(spaceAwareMutation) {
            operators.add(new SpaceAwareMutation(new PoissonGenerator(4, rng), optimizationTime, maxTimeAfterDeadline));
        }


        EvolutionaryOperator<Chromosome> pipeline = new EvolutionPipeline<>(operators);
        EvolutionEngine<Chromosome> engine = new GenerationalEvolutionEngine<>(chromosomeFactory, pipeline, fitnessFunction, selectionStrategy, rng);

        Chromosome winner = null;
        if(withOptimizationTimeout) {
            winner = engine.evolve(populationSize, eliteCount, new ElapsedTime(maxOptimizationDuration));
        }
        else {
            winner = engine.evolve(populationSize, eliteCount, new Stagnation(stagnationGenerationLimit, false));
        }

        return createOptimizationResult(winner, workflowElements);
    }

    private OptimizationResult createOptimizationResult(Chromosome winner, List<WorkflowElement> workflowElements) {
        OptimizationResult optimizationResult = new OptimizationResultImpl() ;

        List<Element> flattenWorkflowList = new ArrayList<>();
        for (WorkflowElement workflowElement : workflowElements) {
            placementHelper.getFlattenWorkflow(flattenWorkflowList, workflowElement);
        }

        fitnessFunction.getFitness(winner, null);
        StringBuilder builder = new StringBuilder();
        builder.append("Optimization Result:\n--------------------------- Winner Chromosome ---------------------------- \n").append(winner.toString()).append("\n");
        builder.append("----------------------------- Winner Fitness -----------------------------\n");
        builder.append("Leasing=").append(fitnessFunction.getLeasingCost()).append("\n");
        builder.append("Penalty=").append(fitnessFunction.getPenaltyCost()).append("\n");
        builder.append("Early Enactment=").append(fitnessFunction.getEarlyEnactmentCost()).append("\n");
        builder.append("Total Fitness=").append(fitnessFunction.getLeasingCost() + fitnessFunction.getPenaltyCost() + fitnessFunction.getEarlyEnactmentCost()).append("\n");
        log.info(builder.toString());

        List<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnitList = optimizationUtility.getRequiredServiceTypes(winner);
        Duration duration = new Duration(optimizationTime, DateTime.now());

        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : serviceTypeSchedulingUnitList) {

            try {

                Container container = optimizationUtility.getContainer(serviceTypeSchedulingUnit.getServiceType());
                container.setBareMetal(true);

                for (Chromosome.Gene processStepGene : serviceTypeSchedulingUnit.getProcessSteps()) {
                    if(!processStepGene.isFixed()) {
                        ProcessStep processStep = processStepGene.getProcessStep();
                        if (processStep.getStartDate() != null && processStep.getScheduledAtContainer() != null && (processStep.getScheduledAtContainer().isRunning() == true || processStep.getScheduledAtContainer().isDeploying() == true)) {

                        } else {
                            DateTime scheduledStartTime = processStepGene.getExecutionInterval().getStart();

//                        if(withOptimizationTimeout) {
//                            scheduledStartTime = scheduledStartTime.plus(duration);
//                        }

                            ProcessStep realProcessStep = null;

                            for (Element element : flattenWorkflowList) {
                                if (element instanceof ProcessStep && ((ProcessStep) element).getInternId().equals(processStep.getInternId())) {
                                    realProcessStep = (ProcessStep) element;
                                }
                            }

                            boolean alreadyDeploying = false;
                            if (realProcessStep.getScheduledAtContainer() != null && (realProcessStep.getScheduledAtContainer().isDeploying() || realProcessStep.getScheduledAtContainer().isRunning())) {
                                alreadyDeploying = true;
                            }

                            if (realProcessStep.getStartDate() == null && !alreadyDeploying) {
                                realProcessStep.setScheduledForExecution(true, scheduledStartTime, container);
                                optimizationResult.addProcessStep(realProcessStep);
                            }
                        }
                    }
                }

            } catch (ContainerImageNotFoundException | ContainerConfigurationNotFoundException e) {
                log.error("Exception", e);
            }

        }

        return optimizationResult;
    }


    @Override
    public void initializeParameters() {

    }
}
