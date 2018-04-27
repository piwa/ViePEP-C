package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
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
import org.springframework.beans.factory.annotation.Autowired;
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

@Component
@Slf4j
public class OnlyContainerImpl extends AbstractHeuristicImpl implements ProcessInstancePlacementProblem {

    @Autowired
    private FitnessFunction fitnessFunction;
    @Autowired
    private OptimizationUtility optimizationUtility;

    private AdjustableNumberGenerator<Probability> numberGenerator = new AdjustableNumberGenerator<>(new Probability(0.85d));
    private int populationSize = 500;
    private int eliteCount = (int) Math.round(populationSize * 0.05);
    private DateTime optimizationTime;

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        List<WorkflowElement> workflowElements = getRunningWorkflowInstancesSorted();
        List<ProcessStep> runningProcesses = getAllRunningSteps(workflowElements);
        this.optimizationTime = DateTime.now();

        SelectionStrategy<Object> selectionStrategy = new TournamentSelection(numberGenerator);

        Random rng = new MersenneTwisterRNG();
        List<EvolutionaryOperator<Chromosome>> operators = new ArrayList<>(2);
        operators.add(new TimeExchangeCrossover());
        operators.add(new SingleShiftMutation(new PoissonGenerator(2, rng), new DiscreteUniformRangeGenerator(-10000, 10000, rng)));
        EvolutionaryOperator<Chromosome> pipeline = new EvolutionPipeline<>(operators);

        EvolutionEngine<Chromosome> engine = new GenerationalEvolutionEngine<>(new Factory(workflowElements, runningProcesses, this.optimizationTime),
                pipeline,
                fitnessFunction,
                selectionStrategy,
                rng);

        Chromosome winner = engine.evolve(populationSize, eliteCount, new Stagnation(20, false));

        return createOptimizationResult(winner);
    }

    private OptimizationResult createOptimizationResult(Chromosome winner) {
        OptimizationResult optimizationResult = new OptimizationResultImpl(optimizationTime) ;


        List<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnitList = optimizationUtility.getRequiredServiceTypes(winner);


        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : serviceTypeSchedulingUnitList) {

            try {

                Container container = optimizationUtility.getContainer(serviceTypeSchedulingUnit.getServiceType());

                for (ProcessStep processStep : serviceTypeSchedulingUnit.getProcessSteps()) {
                    processStep.setScheduledForExecution(true, serviceTypeSchedulingUnit.getDeploymentInterval().getStart(), container);
                    optimizationResult.addProcessStep(processStep);
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
