package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.OrderMaintainer;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ProcessStepSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.context.ApplicationContext;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.watchmaker.framework.operators.AbstractCrossover;

import java.util.*;

@Slf4j
@SuppressWarnings("Duplicates")
public class SpaceAwareDeploymentCrossover extends AbstractCrossover<Chromosome> {

    private OrderMaintainer orderMaintainer = new OrderMaintainer();
    private Map<String, DateTime> maxTimeAfterDeadline;
    private VMSelectionHelper vmSelectionHelper;
    private CacheVirtualMachineService virtualMachineService;

    /**
     * Single-point cross-over.
     */
    public SpaceAwareDeploymentCrossover(Map<String, DateTime> maxTimeAfterDeadline) {
        this(1, maxTimeAfterDeadline);
    }


    /**
     * Multiple-point cross-over (fixed number of points).
     *
     * @param crossoverPoints The fixed number of cross-overs applied to each
     *                        pair of parents.
     */
    public SpaceAwareDeploymentCrossover(int crossoverPoints, Map<String, DateTime> maxTimeAfterDeadline) {
        super(crossoverPoints);
        this.maxTimeAfterDeadline = maxTimeAfterDeadline;
        this.vmSelectionHelper = SpringContext.getApplicationContext().getBean(VMSelectionHelper.class);

        this.virtualMachineService = SpringContext.getApplicationContext().getBean(CacheVirtualMachineService.class);
    }


    /**
     * Multiple-point cross-over (variable number of points).
     *
     * @param crossoverPointsVariable Provides the (possibly variable) number of
     *                                cross-overs applied to each pair of parents.
     */
    public SpaceAwareDeploymentCrossover(NumberGenerator<Integer> crossoverPointsVariable, Map<String, DateTime> maxTimeAfterDeadline) {
        super(crossoverPointsVariable);
        this.maxTimeAfterDeadline = maxTimeAfterDeadline;
        this.vmSelectionHelper = SpringContext.getApplicationContext().getBean(VMSelectionHelper.class);
    }


    @Override
    protected List<Chromosome> mate(Chromosome parent1, Chromosome parent2, int numberOfCrossoverPoints, Random random) {

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(parent1, this.getClass().getSimpleName() + "_spaceAwareCrossover_1");
        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(parent2, this.getClass().getSimpleName() + "_spaceAwareCrossover_2");

        List<List<Chromosome.Gene>> clone1 = parent1.clone().getGenes();
        Chromosome offspring1Chromosome = new Chromosome(clone1);

        List<List<Chromosome.Gene>> clone2 = parent2.clone().getGenes();
        Chromosome offspring2Chromosome = new Chromosome(clone2);

        int amountOfRows = clone1.size();
        boolean rowClone1Changed = false;
        boolean rowClone2Changed = false;

        for (int i = 0; i < 100; i++) {
            int rowIndex = random.nextInt(amountOfRows);
            List<Chromosome.Gene> rowClone1 = offspring1Chromosome.getRow(rowIndex);
            List<Chromosome.Gene> rowClone2 = offspring2Chromosome.getRow(rowIndex);

            List<Chromosome.Gene> rowParent1 = parent1.getRow(rowIndex);
            List<Chromosome.Gene> rowParent2 = parent2.getRow(rowIndex);

            int bound = rowClone1.size() - 1;
            int crossoverStartIndex = 0;
            if (bound > 0) {         // the nextInt can only be performed for bound >= 1
                crossoverStartIndex = random.nextInt(bound);
            }

            ProcessStepSchedulingUnit parent2PSSchedulingUnit = rowParent2.get(crossoverStartIndex).getProcessStepSchedulingUnit();
            ProcessStepSchedulingUnit clone1PSSchedulingUnit = rowClone1.get(crossoverStartIndex).getProcessStepSchedulingUnit();

            VirtualMachineSchedulingUnit parent2VirtualMachineSchedulingUnit = parent2PSSchedulingUnit.getContainerSchedulingUnit().getScheduledOnVm();
            VirtualMachineSchedulingUnit clone1VirtualMachineSchedulingUnit = clone1PSSchedulingUnit.getContainerSchedulingUnit().getScheduledOnVm();

            if(!clone1VirtualMachineSchedulingUnit.isFixed()) {
                VMType parent2VMType = parent2VirtualMachineSchedulingUnit.getVirtualMachineInstance().getVmType();
                VMType clone1VMType = clone1VirtualMachineSchedulingUnit.getVirtualMachineInstance().getVmType();
                Long clone1VMTypeIdentifier = clone1VMType.getIdentifier();
                if (parent2VMType != clone1VMType) {
                    rowClone1Changed = performCrossover(clone1VirtualMachineSchedulingUnit, parent2VMType, clone1VMTypeIdentifier);
                }
            }

            ProcessStepSchedulingUnit parent1PSSchedulingUnit = rowParent1.get(crossoverStartIndex).getProcessStepSchedulingUnit();
            ProcessStepSchedulingUnit clone2PSSchedulingUnit = rowClone2.get(crossoverStartIndex).getProcessStepSchedulingUnit();

            VirtualMachineSchedulingUnit parent1VirtualMachineSchedulingUnit = parent1PSSchedulingUnit.getContainerSchedulingUnit().getScheduledOnVm();
            VirtualMachineSchedulingUnit clone2VirtualMachineSchedulingUnit = clone2PSSchedulingUnit.getContainerSchedulingUnit().getScheduledOnVm();

            if(!clone2VirtualMachineSchedulingUnit.isFixed()) {
                VMType parent1VMType = parent1VirtualMachineSchedulingUnit.getVirtualMachineInstance().getVmType();
                VMType clone2VMType = clone2VirtualMachineSchedulingUnit.getVirtualMachineInstance().getVmType();
                Long clone2VMTypeIdentifier = clone2VMType.getIdentifier();
                if (parent1VMType == clone2VMType) {
                    rowClone2Changed = performCrossover(clone2VirtualMachineSchedulingUnit, parent1VMType, clone2VMTypeIdentifier);
                }
            }
            if (rowClone1Changed || rowClone2Changed) {
                break;
            }
        }

        orderMaintainer.orderIsOk(offspring1Chromosome.getGenes());
        orderMaintainer.orderIsOk(offspring2Chromosome.getGenes());

        List<Chromosome> result = new ArrayList<>();
        result.add(offspring1Chromosome);
        result.add(offspring2Chromosome);

        vmSelectionHelper.mergeVirtualMachineSchedulingUnits(offspring1Chromosome);
        vmSelectionHelper.mergeVirtualMachineSchedulingUnits(offspring2Chromosome);

//        vmSelectionHelper.checkVmSizeAndSolveSpaceIssues(offspring1Chromosome);       // is done in mergeVirtualMachineSchedulingUnits
//        vmSelectionHelper.checkVmSizeAndSolveSpaceIssues(offspring2Chromosome);

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(offspring1Chromosome, this.getClass().getSimpleName() + "_spaceAwareCrossover_3");
        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(offspring2Chromosome, this.getClass().getSimpleName() + "_spaceAwareCrossover_4");

        return result;
    }

    private boolean performCrossover(VirtualMachineSchedulingUnit clone1VirtualMachineSchedulingUnit, VMType parent2VMType, Long clone1VMTypeIdentifier) {
        clone1VirtualMachineSchedulingUnit.getVirtualMachineInstance().setVmType(parent2VMType);
        if(!vmSelectionHelper.checkEnoughResourcesLeftOnVM(clone1VirtualMachineSchedulingUnit)) {
            for (VMType vmType : virtualMachineService.getVMTypes()) {
                if(vmType.getIdentifier() == clone1VMTypeIdentifier) {
                    clone1VirtualMachineSchedulingUnit.getVirtualMachineInstance().setVmType(vmType);
                    break;
                }
            }

        } else {
            return true;
        }
        return false;
    }

}
