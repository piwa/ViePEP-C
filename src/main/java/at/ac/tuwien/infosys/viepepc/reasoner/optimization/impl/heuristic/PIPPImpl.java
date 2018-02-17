package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import io.jenetics.*;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.engine.Limits;
import io.jenetics.util.Factory;
import io.jenetics.util.RandomRegistry;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Function;

import static io.jenetics.engine.EvolutionResult.toBestPhenotype;

@Slf4j
public class PIPPImpl extends AbstractHeuristicImpl implements ProcessInstancePlacementProblem {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;

    private List<ProcessStep> processSteps;

    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        DateTime now = DateTime.now();
        List<WorkflowElement> workflowElements = getRunningWorkflowInstancesSorted();
        processSteps = getNextProcessStepsSorted(workflowElements);
        processSteps.addAll(getAllRunningSteps(workflowElements));

        if (processSteps.size() == 0) {
            return new OptimizationResultImpl();
        }

        Factory<Genotype<AnyGene<VirtualMachine>>> genotype = Genotype.of(AnyChromosome.of(this::getRandomVirtualMachine, processSteps.size()));

        Function<Genotype<AnyGene<VirtualMachine>>, Double> fitnessFunction = new PIPPFitnessFunction(processSteps, now);


        Engine<AnyGene<VirtualMachine>, Double> engine = Engine.builder(fitnessFunction, genotype)
                .optimize(Optimize.MINIMUM)
                .maximalPhenotypeAge(11)
                .populationSize(100)
                .genotypeValidator(this::isValid)
//                .alterers(new SwapMutator<>(0.2), new PartiallyMatchedCrossover<>(0.35))
                .build();


        final EvolutionStatistics<Double, ?> statistics = EvolutionStatistics.ofNumber();

        final Phenotype<AnyGene<VirtualMachine>, Double> best = engine.stream()
                .limit(Limits.bySteadyFitness(16))
                .limit(50)
                .peek(statistics)
                .collect(toBestPhenotype());

        log.info("\n" + statistics);
//        System.out.println(best);

        return decode(best, processSteps);
    }

    private boolean isValid(Genotype<AnyGene<VirtualMachine>> genotype) {
        Chromosome<AnyGene<VirtualMachine>> chromosome = genotype.getChromosome();

        Map<VirtualMachine, List<ProcessStep>> vmToProcessMap = createVirtualMachineListMap(chromosome);

        for (Map.Entry<VirtualMachine, List<ProcessStep>> entry : vmToProcessMap.entrySet()) {
            VirtualMachine vm = entry.getKey();
            List<ProcessStep> processSteps = entry.getValue();


            try {
                if (!checkResourceAvailability(vm, processSteps)) {
                    return false;
                }
            } catch (ContainerConfigurationNotFoundException e) {
                log.error("Container Configuration not found");
                return false;
            }
        }

        return true;
    }




    private boolean checkResourceAvailability(VirtualMachine vm, List<ProcessStep> processStepList) throws ContainerConfigurationNotFoundException {

        double cpuRequirement = 0.0;
        double ramRequirement = 0.0;

        List<ContainerConfiguration> containerConfigurations = getContainerConfigurations(processStepList);
        for(ContainerConfiguration config : containerConfigurations) {
            cpuRequirement = cpuRequirement + config.getCPUPoints();
            ramRequirement = ramRequirement + config.getRam();
        }

        if (vm.getVmType().getCpuPoints() >= cpuRequirement && vm.getVmType().getRamPoints() >= ramRequirement) {
            return true;
        }

        return false;
    }


    private VirtualMachine getRandomVirtualMachine() {
        Random rand = RandomRegistry.getRandom();
        return cacheVirtualMachineService.getAllVMs().get(rand.nextInt(cacheVirtualMachineService.getAllVMs().size()));
    }

    private OptimizationResult decode(@NotNull Phenotype<AnyGene<VirtualMachine>, Double> result, @NotNull List<ProcessStep> processSteps) {
        OptimizationResult optimizationResult = new OptimizationResultImpl();

        StringBuilder stringBuilder = new StringBuilder("[");
        Chromosome<AnyGene<VirtualMachine>> resultChromosome = result.getGenotype().getChromosome();
        for (int i = 0; i < processSteps.size(); i++) {
            try {
                VirtualMachine vm = resultChromosome.getGene(i).getAllele();
                ProcessStep processStep = processSteps.get(i);
                if (processStep.getScheduledAtContainer() == null) {
                    processStep.setScheduledAtContainer(getContainer(processStep));
                }
                processStep.getScheduledAtContainer().setVirtualMachine(vm);

                stringBuilder.append(processStep.getName()).append("->").append(vm.getName()).append(",");

                optimizationResult.addProcessStep(processStep);
            } catch (ContainerImageNotFoundException | ContainerConfigurationNotFoundException e) {
                log.error("Exception", e);
            }
        }

        stringBuilder.deleteCharAt(stringBuilder.lastIndexOf(","));
        stringBuilder.append("]");
        log.info(stringBuilder.toString() + " --> " + result.getFitness());

        return optimizationResult;
    }
}
