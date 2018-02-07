package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic;

import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.AbstractProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import io.jenetics.*;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.engine.Limits;
import io.jenetics.util.Factory;
import io.jenetics.util.ISeq;
import io.jenetics.util.RandomRegistry;
import jersey.repackaged.com.google.common.base.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

import static io.jenetics.engine.EvolutionResult.toBestPhenotype;

@Slf4j
public class PIPPImpl extends AbstractContainerProvisioningImpl implements ProcessInstancePlacementProblem {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;

    private List<ProcessStep> processSteps;

    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        List<WorkflowElement> workflowElements = getRunningWorkflowInstancesSorted();
        processSteps = getNextProcessStepsSorted(workflowElements);
        processSteps.addAll(getAllRunningSteps(workflowElements));

        if (processSteps.size() == 0) {
            return new OptimizationResultImpl();
        }

        Factory<Genotype<AnyGene<VirtualMachine>>> genotype = Genotype.of(AnyChromosome.of(this::getRandomVirtualMachine, processSteps.size()));

        Function<Genotype<AnyGene<VirtualMachine>>, Double> fitnessFunction = new PIPPFitnessFunction(processSteps);


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


            if (!checkResourceAvailability(vm, processSteps)) {
                return false;
            }
        }

        return true;
    }

    private Map<VirtualMachine, List<ProcessStep>> createVirtualMachineListMap(Chromosome<AnyGene<VirtualMachine>> chromosome) {
        Map<VirtualMachine, List<ProcessStep>> vmToProcessMap = new HashMap<>();
        for (int i = 0; i < chromosome.length(); i++) {
            VirtualMachine vm = chromosome.getGene(i).getAllele();
            if (!vmToProcessMap.containsKey(vm)) {
                vmToProcessMap.put(vm, new ArrayList<>());
            }
            vmToProcessMap.get(vm).add(processSteps.get(i));
        }
        return vmToProcessMap;
    }


    private boolean checkResourceAvailability(VirtualMachine vm, List<ProcessStep> processStepList) {

        // Todo: use container configurations instead of service types
        double cpuRequirement = 0.0;
        double ramRequirement = 0.0;

        List<ServiceType> alreadyIncludedServiceTypes = new ArrayList<>();
        for(ProcessStep processStep : processStepList) {
            if(alreadyIncludedServiceTypes.contains(processStep.getServiceType())) {
                cpuRequirement = cpuRequirement + processStep.getServiceType().getServiceTypeResources().getCpuLoad() / 4;
                ramRequirement = ramRequirement + processStep.getServiceType().getServiceTypeResources().getMemory() / 4;
            }
            else {
                cpuRequirement = cpuRequirement + processStep.getServiceType().getServiceTypeResources().getCpuLoad();
                ramRequirement = ramRequirement + processStep.getServiceType().getServiceTypeResources().getMemory();
                alreadyIncludedServiceTypes.add(processStep.getServiceType());
            }
        }

//        double cpuRequirement = processStepList.stream().mapToDouble(ps -> ps.getServiceType().getServiceTypeResources().getCpuLoad()).sum();
//        double ramRequirement = processStepList.stream().mapToDouble(ps -> ps.getServiceType().getServiceTypeResources().getCpuLoad()).sum();

        if (vm.getVmType().getCpuPoints() >= cpuRequirement && vm.getVmType().getRamPoints() >= ramRequirement) {
            return true;
        }

        return false;
    }

    private VirtualMachine getRandomVirtualMachine() {
        Random rand = RandomRegistry.getRandom();
        return cacheVirtualMachineService.getAllVMs().get(rand.nextInt(cacheVirtualMachineService.getAllVMs().size()));
    }

    private OptimizationResult decode(Phenotype<AnyGene<VirtualMachine>, Double> result, List<ProcessStep> processSteps) {
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
