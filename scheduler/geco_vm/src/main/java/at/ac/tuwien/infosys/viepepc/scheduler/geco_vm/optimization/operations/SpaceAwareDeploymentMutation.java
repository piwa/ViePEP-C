package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.OrderMaintainer;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ContainerSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VMTypeNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
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
        this(poissonGenerator.nextValue(), optimizationEndTime);
    }

    /**
     * @param mutationCount The constant number of mutations
     *                      to apply to each row in a Sudoku solution.
     */
    public SpaceAwareDeploymentMutation(int mutationCount, DateTime optimizationEndTime) {
        this(new ConstantGenerator<>(mutationCount), optimizationEndTime);
//        if (mutationCount < 1) {
//            throw new IllegalArgumentException("Mutation count must be at least 1.");
//        }
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
        if(mutationCount.nextValue() < 1) {
            mutationCount = new ConstantGenerator<>(1);
        }
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

        List<ContainerSchedulingUnit> containerSchedulingUnits = new ArrayList<>(newCandidate.getFlattenChromosome().stream()
                .map(gene -> gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit()).filter(unit -> !unit.isFixed()).collect(Collectors.toSet()));

        if(containerSchedulingUnits.size() == 0) {
            return newCandidate;
        }

        int mutationCount = Math.abs(mutationCountVariable.nextValue());
        int counter = 0;
        while (mutationCount > 0 && counter < 100) {
            int index = random.nextInt(containerSchedulingUnits.size());
            ContainerSchedulingUnit containerSchedulingUnit = containerSchedulingUnits.get(index);

            VirtualMachineSchedulingUnit oldVirtualMachineSchedulingUnit = containerSchedulingUnit.getScheduledOnVm();

            Set<VirtualMachineSchedulingUnit> alreadyScheduledVirtualMachines = newCandidate.getFlattenChromosome().stream().map(g -> g.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm()).collect(Collectors.toSet());

            SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_3");
            VirtualMachineSchedulingUnit newVirtualMachineSchedulingUnit = vmSelectionHelper.createVMSchedulingUnit(alreadyScheduledVirtualMachines, containerSchedulingUnit.getScheduledOnVm().getScheduledContainers(), random);

            if (oldVirtualMachineSchedulingUnit != newVirtualMachineSchedulingUnit) {

                oldVirtualMachineSchedulingUnit.getScheduledContainers().remove(containerSchedulingUnit);
                newVirtualMachineSchedulingUnit.getScheduledContainers().add(containerSchedulingUnit);
                containerSchedulingUnit.setScheduledOnVm(newVirtualMachineSchedulingUnit);

                boolean orderIsOk = orderMaintainer.orderIsOk(newCandidate.getGenes());
                boolean enoughTimeToDeploy = considerFirstContainerStartTime(containerSchedulingUnit);
                boolean enoughSpace = vmSelectionHelper.checkEnoughResourcesLeftOnVM(newVirtualMachineSchedulingUnit);

                if (orderIsOk && enoughTimeToDeploy && !enoughSpace) {
                    try {
                        vmSelectionHelper.resizeVM(newVirtualMachineSchedulingUnit, new ArrayList<>());
                        enoughSpace = true;
                    } catch (VMTypeNotFoundException e) {
                        log.error("could not resize VM");
                        enoughSpace = false;
                    }
                }

                if (orderIsOk && enoughSpace && enoughTimeToDeploy) {
                    SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_5");
                    mutationCount = mutationCount - 1;
                } else {
                    newVirtualMachineSchedulingUnit.getScheduledContainers().remove(containerSchedulingUnit);
                    oldVirtualMachineSchedulingUnit.getScheduledContainers().add(containerSchedulingUnit);
                    containerSchedulingUnit.setScheduledOnVm(oldVirtualMachineSchedulingUnit);
                    SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_7");
                }

            }
            counter = counter + 1;
        }

        vmSelectionHelper.mergeVirtualMachineSchedulingUnits(newCandidate);
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
