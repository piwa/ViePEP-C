package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import io.jenetics.*;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.engine.Limits;
import io.jenetics.util.Factory;
import io.jenetics.util.RandomRegistry;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class PIPPImpl extends AbstractHeuristicImpl implements ProcessInstancePlacementProblem {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private PIPPFitnessFunction fitnessFunction;
    @Autowired
    private ValidityChecker validityChecker;
    @Autowired
    private PIPPCoder pippCoder;

    private List<ProcessStep> alreadyScheduledProcessSteps;
    private List<ProcessStep> notScheduledProcessSteps;

    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        DateTime now = DateTime.now();
        List<WorkflowElement> workflowElements = getRunningWorkflowInstancesSorted();
        List<ProcessStep> allProcessSteps = getNextProcessStepsSorted(workflowElements);
        allProcessSteps.addAll(getAllRunningSteps(workflowElements));

        alreadyScheduledProcessSteps = allProcessSteps.stream().filter(ps -> ps.isScheduled()).collect(Collectors.toList());
        notScheduledProcessSteps = allProcessSteps.stream().filter(ps -> !ps.isScheduled()).collect(Collectors.toList());

        if (notScheduledProcessSteps.size() == 0) {
            return new OptimizationResultImpl();
        }

        fitnessFunction.setEpocheTime(now);
        fitnessFunction.setAlreadyScheduledProcessSteps(alreadyScheduledProcessSteps);
        fitnessFunction.setNotScheduledProcessSteps(notScheduledProcessSteps);
        validityChecker.setAlreadyScheduledProcessSteps(alreadyScheduledProcessSteps);
        validityChecker.setNotScheduledProcessSteps(notScheduledProcessSteps);

        Factory<Genotype<AnyGene<VirtualMachine>>> genotype = Genotype.of(AnyChromosome.of(this::getRandomVirtualMachine, a -> true, validityChecker::isValidNewInstance, notScheduledProcessSteps.size()));

//        Function<Genotype<AnyGene<VirtualMachine>>, Double> fitnessFunction = new PIPPFitnessFunction(processSteps, now);


        Engine<AnyGene<VirtualMachine>, Double> engine = Engine.builder(fitnessFunction, genotype)
                .optimize(Optimize.MINIMUM)
                .maximalPhenotypeAge(11)
                .populationSize(100)
                .genotypeValidator(validityChecker::isValid)
//                .alterers(new SwapMutator<>(0.2), new PartiallyMatchedCrossover<>(0.35))
                .build();


        final EvolutionStatistics<Double, ?> statistics = EvolutionStatistics.ofNumber();

//        final Phenotype<AnyGene<VirtualMachine>, Double> best = engine.stream()
//                .limit(Limits.bySteadyFitness(16))
//                .limit(50)
//                .peek(statistics)
//                .collect(toBestPhenotype());

        final EvolutionResult<AnyGene<VirtualMachine>, Double> evaluationResult = engine.stream()
                .limit(Limits.bySteadyFitness(16))
                .limit(50)
                .peek(statistics)
                .collect(EvolutionResult.toBestEvolutionResult());
        Phenotype<AnyGene<VirtualMachine>, Double> best = evaluationResult.getBestPhenotype();
        log.error("number of invalidate results: " + evaluationResult.getInvalidCount());

        if(!best.isValid()) {
            log.error("warum?");
        }

        log.info("\n" + statistics);

        return pippCoder.decode(best, notScheduledProcessSteps);
    }

    private VirtualMachine getRandomVirtualMachine() {
        // TODO if ps already has an assigned vm return that one
        Random rand = RandomRegistry.getRandom();
        return cacheVirtualMachineService.getAllVMs().get(rand.nextInt(cacheVirtualMachineService.getAllVMs().size()));
    }

}
