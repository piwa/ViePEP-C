package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations;

import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome2;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ServiceTypeSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.extern.slf4j.Slf4j;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.watchmaker.framework.operators.AbstractCrossover;

import java.util.*;

@Slf4j
@SuppressWarnings("Duplicates")
public class SpaceAwareDeploymentCrossover extends AbstractCrossover<Chromosome2> {

        private VMSelectionHelper vmSelectionHelper;

    /**
     * Single-point cross-over.
     */
    public SpaceAwareDeploymentCrossover() {
        this(1);
    }


    /**
     * Multiple-point cross-over (fixed number of points).
     *
     * @param crossoverPoints The fixed number of cross-overs applied to each
     *                        pair of parents.
     */
    public SpaceAwareDeploymentCrossover(int crossoverPoints) {
        super(crossoverPoints);
        this.vmSelectionHelper = SpringContext.getApplicationContext().getBean(VMSelectionHelper.class);
    }


    /**
     * Multiple-point cross-over (variable number of points).
     *
     * @param crossoverPointsVariable Provides the (possibly variable) number of
     *                                cross-overs applied to each pair of parents.
     */
    public SpaceAwareDeploymentCrossover(NumberGenerator<Integer> crossoverPointsVariable) {
        super(crossoverPointsVariable);
        this.vmSelectionHelper = SpringContext.getApplicationContext().getBean(VMSelectionHelper.class);
    }


    @Override
    protected List<Chromosome2> mate(Chromosome2 parent1, Chromosome2 parent2, int numberOfCrossoverPoints, Random random) {

        Chromosome2 parentClone1 = parent1.clone();
        Chromosome2 parentClone2 = parent2.clone();

        int geneIndex = random.nextInt(parentClone1.getGenes().size());

        boolean rowClone1Changed = false;
        boolean rowClone2Changed = false;

        Chromosome2 newChromosome1 = null;
        Chromosome2 newChromosome2 = null;

        for (int i = 0; i < 100; i++) {

            newChromosome1 = null;
            newChromosome2 = null;

            newChromosome1 = performCrossover(parentClone1, parentClone2, geneIndex);
            rowClone1Changed = checkIfCrossoverIsOk(newChromosome1);

            newChromosome2 = performCrossover(parentClone2, parentClone1, geneIndex);
            rowClone2Changed = checkIfCrossoverIsOk(newChromosome2);

            if(rowClone1Changed || rowClone2Changed) {
                break;
            }
        }

        List<Chromosome2> result = new ArrayList<>();
        if(rowClone1Changed) {
            result.add(newChromosome1);
        }
        if(rowClone2Changed) {
            result.add(newChromosome2);
        }

        return result;
    }

    private boolean checkIfCrossoverIsOk(Chromosome2 newChromosome) {
        Set<VirtualMachineSchedulingUnit> scheduledVirtualMachines = new HashSet<>();
        for (ServiceTypeSchedulingUnit typeSchedulingUnit : newChromosome.getFlattenChromosome()) {
            VirtualMachineSchedulingUnit machineSchedulingUnit = typeSchedulingUnit.getVirtualMachineSchedulingUnit();
            if (machineSchedulingUnit != null) {
                machineSchedulingUnit.getServiceTypeSchedulingUnits().add(typeSchedulingUnit);
                scheduledVirtualMachines.add(machineSchedulingUnit);
            }
        }

        for (VirtualMachineSchedulingUnit scheduledVirtualMachine : scheduledVirtualMachines) {
            boolean result = vmSelectionHelper.checkIfVirtualMachineIsBigEnough(scheduledVirtualMachine);
            if(!result) {
                return false;
            }
        }
        return true;
    }

    private Chromosome2 performCrossover(Chromosome2 parentChromosome1, Chromosome2 parentChromosome2, int crossoverPoint) {

        Chromosome2 offspringChromosome = new Chromosome2(new ArrayList<>());
        for(int i = 0; i < crossoverPoint; i ++) {
            offspringChromosome.getGenes().add(parentChromosome1.getGenes().get(i));
        }
        for(int i = crossoverPoint; i < parentChromosome2.getGenes().size(); i ++) {
            offspringChromosome.getGenes().add(parentChromosome2.getGenes().get(i));
        }

        return offspringChromosome;
    }

}
