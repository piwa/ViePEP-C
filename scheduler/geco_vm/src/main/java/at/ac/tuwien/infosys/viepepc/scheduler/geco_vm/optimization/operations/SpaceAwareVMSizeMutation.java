package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.OrderMaintainer;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VMTypeNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.context.ApplicationContext;
import org.uncommons.maths.number.ConstantGenerator;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.maths.random.PoissonGenerator;
import org.uncommons.watchmaker.framework.EvolutionaryOperator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("Duplicates")
public class SpaceAwareVMSizeMutation implements EvolutionaryOperator<Chromosome> {

    private final NumberGenerator<Integer> mutationCountVariable;
    private VMSelectionHelper vmSelectionHelper;
    private CacheVirtualMachineService cacheVirtualMachineService;

    /**
     * Default is one mutation per candidate.
     *
     * @param poissonGenerator
     */
    public SpaceAwareVMSizeMutation(PoissonGenerator poissonGenerator) {
        this(poissonGenerator.nextValue());
    }

    /**
     * @param mutationCount The constant number of mutations
     *                      to apply to each row in a Sudoku solution.
     */
    public SpaceAwareVMSizeMutation(int mutationCount) {
        this(new ConstantGenerator<>(mutationCount));
    }

    /**
     * Typically the mutation count will be from a Poisson distribution.
     * The mutation amount can be from any discrete probability distribution
     * and can include negative values.
     *
     * @param mutationCount A random variable that provides a number
     *                      of mutations that will be applied to each row in an individual.
     */
    public SpaceAwareVMSizeMutation(NumberGenerator<Integer> mutationCount) {
        if (mutationCount.nextValue() < 1) {
            mutationCount = new ConstantGenerator<>(1);
        }
        this.mutationCountVariable = mutationCount;
//        this.optimizationEndTime = optimizationEndTime;

        ApplicationContext context = SpringContext.getApplicationContext();
        this.vmSelectionHelper = context.getBean(VMSelectionHelper.class);
        this.cacheVirtualMachineService = context.getBean(CacheVirtualMachineService.class);
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
        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(candidate, this.getClass().getSimpleName() + "_spaceAwareVMSizeMutation_1");

        Chromosome newCandidate = candidate.clone();

        List<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = newCandidate.getFlattenChromosome().stream()
                .map(gene -> gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit()).filter(unit -> !unit.isFixed()).distinct().collect(Collectors.toList());

        if (virtualMachineSchedulingUnits.size() == 0) {
            SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareVMSizeMutation_6");
            return newCandidate;
        }

        int mutationCount = Math.abs(mutationCountVariable.nextValue());
        int counter = 0;
        while (mutationCount > 0 && counter < 100) {
            int index = random.nextInt(virtualMachineSchedulingUnits.size());
            VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = virtualMachineSchedulingUnits.get(index);

            try {
//                if (random.nextBoolean()) {
                    vmSelectionHelper.resizeVM(virtualMachineSchedulingUnit);
//                } else {
//                    makeItBigger(virtualMachineSchedulingUnit);
//                }
                mutationCount = mutationCount - 1;
            } catch (VMTypeNotFoundException e) {
                log.error("could not resize VM");
            }

            counter = counter + 1;
        }

//        vmSelectionHelper.mergeVirtualMachineSchedulingUnits(newCandidate);

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareVMSizeMutation_6");

        return newCandidate;
    }

    private void makeItBigger(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit) {
        List<VMType> allVMTypes = cacheVirtualMachineService.getVMTypes();
//        allVMTypes.sort(Comparator.comparing(VMType::getCores).thenComparing(VMType::getRamPoints));
        allVMTypes.sort(Comparator.comparing(VMType::getCores));

        for (int i = 0; i < allVMTypes.size(); i++) {
            VMType vmType = allVMTypes.get(i);
            if (vmType == virtualMachineSchedulingUnit.getVmType() && i + 1 < allVMTypes.size()) {
                virtualMachineSchedulingUnit.setVmType(allVMTypes.get(i + 1));
                return;
            }
        }
    }
}
