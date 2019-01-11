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
        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(candidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_1");

        Chromosome newCandidate = candidate.clone();

        vmSelectionHelper.mergeVirtualMachineSchedulingUnits(newCandidate);
        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_2");

        List<ContainerSchedulingUnit> containerSchedulingUnits = newCandidate.getFlattenChromosome().stream().map(gene -> gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit()).collect(Collectors.toList());

        int mutationCount = Math.abs(mutationCountVariable.nextValue());
        int counter = 0;
        while (mutationCount > 0 && counter < 100) {
            int index = random.nextInt(containerSchedulingUnits.size());
            ContainerSchedulingUnit containerSchedulingUnit = containerSchedulingUnits.get(index);

            VirtualMachineSchedulingUnit oldVirtualMachineSchedulingUnit = containerSchedulingUnit.getScheduledOnVm();

            if (!containerSchedulingUnit.isFixed()) {

                Set<VirtualMachineSchedulingUnit> alreadyScheduledVirtualMachines = new HashSet<>();
                for (Chromosome.Gene g : newCandidate.getFlattenChromosome()) {
                    alreadyScheduledVirtualMachines.add(g.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm());
                }

                SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_3");
                VirtualMachineSchedulingUnit newVirtualMachineSchedulingUnit = vmSelectionHelper.createVMSchedulingUnit(alreadyScheduledVirtualMachines, containerSchedulingUnit.getScheduledOnVm().getScheduledContainers());


                if(oldVirtualMachineSchedulingUnit != newVirtualMachineSchedulingUnit) {

                    oldVirtualMachineSchedulingUnit.getScheduledContainers().remove(containerSchedulingUnit);
                    newVirtualMachineSchedulingUnit.getScheduledContainers().add(containerSchedulingUnit);
                    containerSchedulingUnit.setScheduledOnVm(newVirtualMachineSchedulingUnit);
//                    SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_4");

                    boolean orderIsOk = orderMaintainer.orderIsOk(newCandidate.getGenes());
                    boolean enoughTimeToDeploy = considerFirstContainerStartTime(containerSchedulingUnit);
                    boolean enoughSpace = vmSelectionHelper.checkEnoughResourcesLeftOnVM(newVirtualMachineSchedulingUnit);

                    if (orderIsOk && enoughSpace && enoughTimeToDeploy) {
                        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_5");
                        mutationCount = mutationCount - 1;
                    } else {
//                        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_6");

                        newVirtualMachineSchedulingUnit.getScheduledContainers().remove(containerSchedulingUnit);
                        oldVirtualMachineSchedulingUnit.getScheduledContainers().add(containerSchedulingUnit);
                        containerSchedulingUnit.setScheduledOnVm(oldVirtualMachineSchedulingUnit);
                        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_7");

                    }
                }
            }
            counter = counter + 1;
        }

        vmSelectionHelper.mergeVirtualMachineSchedulingUnits(newCandidate);
        vmSelectionHelper.checkVmSizeAndSolveSpaceIssues(newCandidate);

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_6");

        return newCandidate;
    }


    private boolean considerFirstContainerStartTime(ContainerSchedulingUnit containerSchedulingUnit) {
        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = containerSchedulingUnit.getScheduledOnVm();
        VirtualMachineStatus virtualMachineStatus = virtualMachineSchedulingUnit.getVirtualMachineInstance().getVirtualMachineStatus();
        DateTime deploymentStartTime = containerSchedulingUnit.getDeployStartTime();        // TODO is it ok not to consider the vm?
        if ((virtualMachineStatus.equals(VirtualMachineStatus.UNUSED) || virtualMachineStatus.equals(VirtualMachineStatus.SCHEDULED)) && virtualMachineSchedulingUnit.getDeploymentStartTime().isBefore(this.optimizationEndTime)) {
            return false;
        } else if (deploymentStartTime.isBefore(this.optimizationEndTime)) {
            return false;
        } else {
            return true;
        }
    }

}
