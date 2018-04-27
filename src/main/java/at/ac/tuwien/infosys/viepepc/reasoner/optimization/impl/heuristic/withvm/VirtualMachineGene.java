package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.withvm;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import io.jenetics.Gene;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;
import io.jenetics.util.RandomRegistry;

import java.util.List;
import java.util.Random;

public class VirtualMachineGene implements Gene<VirtualMachine, VirtualMachineGene> {

    private List<VirtualMachine> allVirtualMachines;
    private VirtualMachine virtualMachine;

    private VirtualMachineGene(List<VirtualMachine> allVirtualMachines) {
        this.allVirtualMachines = allVirtualMachines;
        this.virtualMachine = getRandomVirtualMachine();
    }

    private VirtualMachineGene(VirtualMachine virtualMachine) {
        this.virtualMachine = virtualMachine;
    }

    public static VirtualMachineGene of(VirtualMachine virtualMachine) {
        return new VirtualMachineGene(virtualMachine);
    }

    public static ISeq<VirtualMachineGene> seq(List<VirtualMachine> allVirtualMachines, int length) {
        return MSeq.<VirtualMachineGene>ofLength(length).fill(() -> new VirtualMachineGene(allVirtualMachines)).toISeq();
    }

    @Override
    public VirtualMachine getAllele() {
        return virtualMachine;
    }

    @Override
    public VirtualMachineGene newInstance() {
        return new VirtualMachineGene(getRandomVirtualMachine());
    }

    @Override
    public VirtualMachineGene newInstance(VirtualMachine virtualMachine) {
        return new VirtualMachineGene(virtualMachine);
    }

    @Override
    public boolean isValid() {
        return allVirtualMachines.contains(this.virtualMachine);
    }

    private VirtualMachine getRandomVirtualMachine() {
        Random rand = RandomRegistry.getRandom();
        return allVirtualMachines.get(rand.nextInt(allVirtualMachines.size()));
    }
}