package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.OrderMaintainer;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ContainerSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Interval;
import org.springframework.context.ApplicationContext;
import org.uncommons.maths.number.ConstantGenerator;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.maths.random.PoissonGenerator;
import org.uncommons.watchmaker.framework.EvolutionaryOperator;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("Duplicates")
public class SpaceAwareDeploymentMutation implements EvolutionaryOperator<Chromosome> {

    private final NumberGenerator<Integer> mutationCountVariable;
    private final DateTime optimizationEndTime;
    private OrderMaintainer orderMaintainer = new OrderMaintainer();
    private VMSelectionHelper vmSelectionHelper;

    /**
     * Default is one mutation per candidate.
     *
     * @param poissonGenerator
     * @param optimizationEndTime
     */
    public SpaceAwareDeploymentMutation(PoissonGenerator poissonGenerator, DateTime optimizationEndTime) {
        this(1, optimizationEndTime);
    }

    /**
     * @param mutationCount The constant number of mutations
     *                      to apply to each row in a Sudoku solution.
     */
    public SpaceAwareDeploymentMutation(int mutationCount, DateTime optimizationEndTime) {
        this(new ConstantGenerator<>(mutationCount), optimizationEndTime);
        if (mutationCount < 1) {
            throw new IllegalArgumentException("Mutation count must be at least 1.");
        }
    }

    /**
     * Typically the mutation count will be from a Poisson distribution.
     * The mutation amount can be from any discrete probability distribution
     * and can include negative values.
     *
     * @param mutationCount A random variable that provides a number
     *                      of mutations that will be applied to each row in an individual.
     */
    public SpaceAwareDeploymentMutation(NumberGenerator<Integer> mutationCount, DateTime optimizationEndTime) {
        this.mutationCountVariable = mutationCount;
        this.optimizationEndTime = optimizationEndTime;

        ApplicationContext context = SpringContext.getApplicationContext();
        this.vmSelectionHelper = context.getBean(VMSelectionHelper.class);
    }

    @Override
    public List<Chromosome> apply(List<Chromosome> selectedCandidates, Random random) {
        List<Chromosome> mutatedCandidates = new ArrayList<>();
        for (Chromosome candidate : selectedCandidates) {
            mutatedCandidates.add(mutate(candidate, random));
        }

        return mutatedCandidates;
    }

    private Chromosome mutate(Chromosome candidate, Random random) {
        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(candidate);

        Chromosome newCandidate = candidate.clone();

        Set<VirtualMachineInstance> alreadyScheduledVirtualMachines = new HashSet<>();

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate);

        int mutationCount = Math.abs(mutationCountVariable.nextValue());
        int counter = 0;
        while (mutationCount > 0 && counter < 100) {
            int rowIndex = random.nextInt(newCandidate.getGenes().size());
            List<Chromosome.Gene> row = newCandidate.getGenes().get(rowIndex);

            int geneIndex = random.nextInt(row.size());
            Chromosome.Gene gene = row.get(geneIndex);

            if (!gene.isFixed()) {

                alreadyScheduledVirtualMachines.clear();
                for (Chromosome.Gene g : newCandidate.getFlattenChromosome()) {
                    alreadyScheduledVirtualMachines.add(g.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachineInstance());
                }

                ContainerSchedulingUnit containerSchedulingUnit = gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit();
                VirtualMachineSchedulingUnit oldVirtualMachineSchedulingUnit = containerSchedulingUnit.getScheduledOnVm();

                VirtualMachineSchedulingUnit newVirtualMachineSchedulingUnit = vmSelectionHelper.createVMSchedulingUnit(alreadyScheduledVirtualMachines);

                oldVirtualMachineSchedulingUnit.getScheduledContainers().remove(containerSchedulingUnit);
                newVirtualMachineSchedulingUnit.getScheduledContainers().add(containerSchedulingUnit);
                containerSchedulingUnit.setScheduledOnVm(newVirtualMachineSchedulingUnit);

                boolean result;
                if (!orderMaintainer.orderIsOk(newCandidate.getGenes())) {
                    result = false;
                }
                else {
                    result = considerFirstContainerStartTime(newCandidate, gene);
                }

                if (result) {
                    mutationCount = mutationCount - 1;
                } else {
                    containerSchedulingUnit.setScheduledOnVm(oldVirtualMachineSchedulingUnit);
                    oldVirtualMachineSchedulingUnit.getScheduledContainers().add(containerSchedulingUnit);
                    newVirtualMachineSchedulingUnit.getScheduledContainers().remove(containerSchedulingUnit);
                }

            }
            counter = counter + 1;
        }

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate);

        vmSelectionHelper.checkVmSizeAndSolveSpaceIssues(newCandidate);
        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate);

        vmSelectionHelper.mergeVirtualMachineSchedulingUnits(newCandidate);
        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate);

        return newCandidate;
    }


    private boolean considerFirstContainerStartTime(Chromosome newChromosome, Chromosome.Gene movedGene) {
        List<ContainerSchedulingUnit> containerSchedulingUnits = newChromosome.getFlattenChromosome().stream().map(gene -> gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit()).collect(Collectors.toList());
        for (ContainerSchedulingUnit containerSchedulingUnit : containerSchedulingUnits) {
            if (containerSchedulingUnit.getProcessStepGenes().contains(movedGene)) {

                VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = containerSchedulingUnit.getScheduledOnVm();
                VirtualMachineStatus virtualMachineStatus = virtualMachineSchedulingUnit.getVirtualMachineInstance().getVirtualMachineStatus();
                DateTime deploymentStartTime = containerSchedulingUnit.getDeployStartTime();        // TODO is it ok not to consider the vm?
                if ((virtualMachineStatus.equals(VirtualMachineStatus.UNUSED) || virtualMachineStatus.equals(VirtualMachineStatus.SCHEDULED)) &&
                        virtualMachineSchedulingUnit.getDeploymentStartTime().isBefore(this.optimizationEndTime)) {
                    return false;
                } else if (deploymentStartTime.isBefore(this.optimizationEndTime) && containerSchedulingUnit.getFirstGene() == movedGene) {
                    return false;
                } else {
                    return true;
                }
            }
        }
        return true;
    }

}
