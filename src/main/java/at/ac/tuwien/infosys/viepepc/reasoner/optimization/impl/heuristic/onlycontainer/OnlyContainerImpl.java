package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.withvm.AbstractHeuristicImpl;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.uncommons.maths.number.AdjustableNumberGenerator;
import org.uncommons.maths.random.MersenneTwisterRNG;
import org.uncommons.maths.random.PoissonGenerator;
import org.uncommons.maths.random.Probability;
import org.uncommons.watchmaker.framework.EvolutionEngine;
import org.uncommons.watchmaker.framework.EvolutionaryOperator;
import org.uncommons.watchmaker.framework.GenerationalEvolutionEngine;
import org.uncommons.watchmaker.framework.SelectionStrategy;
import org.uncommons.watchmaker.framework.operators.EvolutionPipeline;
import org.uncommons.watchmaker.framework.selection.TournamentSelection;
import org.uncommons.watchmaker.framework.termination.Stagnation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
public class OnlyContainerImpl extends AbstractHeuristicImpl implements ProcessInstancePlacementProblem {

    @Autowired
    private FitnessFunction fitnessFunction;
    @Autowired
    private OptimizationUtility optimizationUtility;

    @Value("${container.default.startup.time}")
    private long defaultContainerStartupTime;
    @Value("${container.default.deploy.time}")
    private long defaultContainerDeployTime;

    private AdjustableNumberGenerator<Probability> numberGenerator = new AdjustableNumberGenerator<>(new Probability(0.85d));
    private int populationSize = 500;
    private int eliteCount = (int) Math.round(populationSize * 0.05);
    private DateTime optimizationTime;

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        List<WorkflowElement> workflowElements = getRunningWorkflowInstancesSorted();
        this.optimizationTime = DateTime.now();

        if (workflowElements.size() == 0) {
            return new OptimizationResultImpl();
        }

        SelectionStrategy<Object> selectionStrategy = new TournamentSelection(numberGenerator);

        Random rng = new MersenneTwisterRNG();
        List<EvolutionaryOperator<Chromosome>> operators = new ArrayList<>(2);
//        operators.add(new TimeExchangeCrossover());
        operators.add(new SingleShiftMutation(new PoissonGenerator(2, rng), new DiscreteUniformRangeGenerator(10000, 10000, rng), optimizationTime));
        EvolutionaryOperator<Chromosome> pipeline = new EvolutionPipeline<>(operators);

        EvolutionEngine<Chromosome> engine = new GenerationalEvolutionEngine<>(new Factory(workflowElements, this.optimizationTime, defaultContainerDeployTime, defaultContainerStartupTime),
                pipeline,
                fitnessFunction,
                selectionStrategy,
                rng);

        Chromosome winner = engine.evolve(populationSize, eliteCount, new Stagnation(10, false));

        return createOptimizationResult(winner, workflowElements);
    }

    private OptimizationResult createOptimizationResult(Chromosome winner, List<WorkflowElement> workflowElements) {
        OptimizationResult optimizationResult = new OptimizationResultImpl() ;

        List<Element> flattenWorkflowList = new ArrayList<>();
        for (WorkflowElement workflowElement : workflowElements) {
            placementHelper.getFlattenWorkflow(flattenWorkflowList, workflowElement);
        }


        List<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnitList = optimizationUtility.getRequiredServiceTypes(winner);
        Duration duration = new Duration(optimizationTime, DateTime.now());

        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : serviceTypeSchedulingUnitList) {

            try {

                Container container = optimizationUtility.getContainer(serviceTypeSchedulingUnit.getServiceType());
                container.setBareMetal(true);

                for (ProcessStep processStep : serviceTypeSchedulingUnit.getProcessSteps()) {
                    if(processStep.getStartDate() != null && processStep.getScheduledAtContainer() != null && (processStep.getScheduledAtContainer().isRunning() == true || processStep.getScheduledAtContainer().isDeploying() == true)) {

                    }
                    else {
                        DateTime scheduledStartTime = serviceTypeSchedulingUnit.getDeploymentInterval().getStart();
                        scheduledStartTime = scheduledStartTime.plus(duration);

                        ProcessStep realProcessStep = null;

                        for (Element element : flattenWorkflowList) {
                            if(element instanceof ProcessStep && ((ProcessStep) element).getInternId().equals(processStep.getInternId())) {
                                realProcessStep = (ProcessStep) element;
                            }
                        }

                        if(realProcessStep == null) {
                            log.error("Big Problem");
                        }
                        else {
                            boolean alreadyDeploying = false;
                            if(realProcessStep.getScheduledAtContainer() != null && (realProcessStep.getScheduledAtContainer().isDeploying() || realProcessStep.getScheduledAtContainer().isRunning())) {
                                alreadyDeploying = true;
                            }

                            if(realProcessStep.getStartDate() == null && !alreadyDeploying) {
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
