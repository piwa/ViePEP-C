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
import org.joda.time.Interval;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.watchmaker.framework.operators.AbstractCrossover;

import java.util.*;
import java.util.stream.Collectors;

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

        Chromosome offspring1Chromosome = parent1.clone();
        Chromosome offspring2Chromosome = parent2.clone();

        List<VirtualMachineSchedulingUnit> clone1VirtualMachinesTemp = offspring1Chromosome.getFlattenChromosome().stream().map(gene -> gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit()).collect(Collectors.toList());
        List<VirtualMachineSchedulingUnit> clone2VirtualMachinesTemp = offspring2Chromosome.getFlattenChromosome().stream().map(gene -> gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit()).collect(Collectors.toList());
        Map<UUID, VirtualMachineSchedulingUnit> clone1VirtualMachines = new HashMap<>();
        Map<UUID, VirtualMachineSchedulingUnit> clone2VirtualMachines = new HashMap<>();
        clone1VirtualMachinesTemp.forEach(element -> clone1VirtualMachines.put(element.getUid(), element));
        clone2VirtualMachinesTemp.forEach(element -> clone2VirtualMachines.put(element.getUid(), element));

        Map<UUID, Chromosome.Gene> processStepNameToCloneMap1 = fillMap(offspring1Chromosome);
        Map<UUID, Chromosome.Gene> processStepNameToCloneMap2 = fillMap(offspring2Chromosome);

        int amountOfRows = offspring1Chromosome.getGenes().size();
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

            Chromosome.Gene parent2Gene = rowParent2.get(crossoverStartIndex);
            Chromosome.Gene clone1Gene = rowClone1.get(crossoverStartIndex);
            VirtualMachineSchedulingUnit parent2VirtualMachineSchedulingUnit = parent2Gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit();
            VirtualMachineSchedulingUnit clone1VirtualMachineSchedulingUnit = clone1Gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit();
            if (!clone1VirtualMachineSchedulingUnit.isFixed() && parent2VirtualMachineSchedulingUnit.getUid() != clone1VirtualMachineSchedulingUnit.getUid()) {
                rowClone1Changed = performCrossover(parent2Gene, clone1VirtualMachines, rowParent2, clone1Gene.getLatestPreviousGene(), processStepNameToCloneMap1);
            }

            Chromosome.Gene parent1Gene = rowParent1.get(crossoverStartIndex);
            Chromosome.Gene clone2Gene = rowClone2.get(crossoverStartIndex);
            VirtualMachineSchedulingUnit parent1VirtualMachineSchedulingUnit = parent1Gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit();
            VirtualMachineSchedulingUnit clone2VirtualMachineSchedulingUnit = clone2Gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit();
            if (!clone2VirtualMachineSchedulingUnit.isFixed() && parent1VirtualMachineSchedulingUnit.getUid() != clone2VirtualMachineSchedulingUnit.getUid()) {
                rowClone2Changed = performCrossover(parent1Gene, clone2VirtualMachines, rowParent1, clone2Gene.getLatestPreviousGene(), processStepNameToCloneMap2);
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

        vmSelectionHelper.checkVmSizeAndSolveSpaceIssues(offspring1Chromosome);
        vmSelectionHelper.checkVmSizeAndSolveSpaceIssues(offspring2Chromosome);

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(offspring1Chromosome, this.getClass().getSimpleName() + "_spaceAwareCrossover_3");
        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(offspring2Chromosome, this.getClass().getSimpleName() + "_spaceAwareCrossover_4");

        return result;
    }

    private boolean performCrossover(Chromosome.Gene parentGene, Map<UUID, VirtualMachineSchedulingUnit> cloneVirtualMachines, List<Chromosome.Gene> rowParent, Chromosome.Gene clonePreviousGene, Map<UUID, Chromosome.Gene> processStepNameToCloneMap) {
        boolean rowCloneChanged = false;
        if (clonePreviousGene == null || checkIfCrossoverPossible(clonePreviousGene, parentGene.getLatestPreviousGene().getNextGenes())) {

            Set<Chromosome.Gene> currentParentGenes = null;
            if (parentGene.getLatestPreviousGene() == null) {
                currentParentGenes = findStartGene(rowParent);
            } else {
                currentParentGenes = parentGene.getLatestPreviousGene().getNextGenes();
            }

            for (Chromosome.Gene currentParentGene : currentParentGenes) {
                performCrossoverRec(currentParentGene, processStepNameToCloneMap.get(currentParentGene.getProcessStepSchedulingUnit().getUid()), processStepNameToCloneMap, cloneVirtualMachines);
            }

            rowCloneChanged = true;
        }
        return rowCloneChanged;
    }

    private void performCrossoverRec(Chromosome.Gene parentGene, Chromosome.Gene currentClone, Map<UUID, Chromosome.Gene> processStepNameToCloneMap, Map<UUID, VirtualMachineSchedulingUnit> cloneVirtualMachines) {
        VirtualMachineSchedulingUnit parentVirtualMachine = parentGene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit();
        VirtualMachineSchedulingUnit newVirtualMachineSchedulingUnit = cloneVirtualMachines.get(parentVirtualMachine.getUid());
        VirtualMachineSchedulingUnit oldVirtualMachineSchedulingUnit = currentClone.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit();

        if (newVirtualMachineSchedulingUnit != null && !newVirtualMachineSchedulingUnit.getUid().equals(oldVirtualMachineSchedulingUnit.getUid())) {
            oldVirtualMachineSchedulingUnit.getProcessStepSchedulingUnits().remove(currentClone.getProcessStepSchedulingUnit());
            newVirtualMachineSchedulingUnit.getProcessStepSchedulingUnits().add(currentClone.getProcessStepSchedulingUnit());
            currentClone.getProcessStepSchedulingUnit().setVirtualMachineSchedulingUnit(newVirtualMachineSchedulingUnit);
        }


        for (Chromosome.Gene nextGene : parentGene.getNextGenes()) {
            if (nextGene != null) {
                performCrossoverRec(nextGene, processStepNameToCloneMap.get(nextGene.getProcessStepSchedulingUnit().getUid()), processStepNameToCloneMap, cloneVirtualMachines);
            }
        }
    }

    private Set<Chromosome.Gene> findStartGene(List<Chromosome.Gene> rowParent2) {
        Set<Chromosome.Gene> startGenes = new HashSet<>();
        for (Chromosome.Gene gene : rowParent2) {
            if (gene.getPreviousGenes() == null || gene.getPreviousGenes().isEmpty()) {
                startGenes.add(gene);
            }
        }

        return startGenes;
    }

    private Map<UUID, Chromosome.Gene> fillMap(Chromosome chromosome) {
        Map<UUID, Chromosome.Gene> map = new HashMap<>();
        chromosome.getFlattenChromosome().forEach(gene -> map.put(gene.getProcessStepSchedulingUnit().getUid(), gene));
        return map;
    }

    private boolean checkIfCrossoverPossible(Chromosome.Gene clonePreviousGene, Set<Chromosome.Gene> parentGenes) {

        for (Chromosome.Gene parentGene : parentGenes) {
            if (clonePreviousGene.getExecutionInterval().getEnd().isAfter(parentGene.getExecutionInterval().getStart())) {
                return false;
            }
        }
        return true;
    }

}
