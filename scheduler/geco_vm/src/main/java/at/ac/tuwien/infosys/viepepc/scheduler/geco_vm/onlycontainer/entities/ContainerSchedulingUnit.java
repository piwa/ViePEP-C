package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.Chromosome;
import lombok.Data;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.ArrayList;
import java.util.List;

@Data
@SuppressWarnings("Duplicates")
public class ContainerSchedulingUnit {


    private Interval serviceAvailableTime;
    private final long containerDeploymentTime;
    private ContainerConfiguration containerConfiguration;
    private VirtualMachineSchedulingUnit scheduledOnVm;
    private List<Chromosome.Gene> processStepGenes = new ArrayList<>();


    public void addProcessStep(Chromosome.Gene processStep) {
        processStepGenes.add(processStep);
    }

    public Chromosome.Gene getFirstGene() {
        Chromosome.Gene firstGene = null;
        for (Chromosome.Gene gene : processStepGenes) {
            if (firstGene == null || firstGene.getExecutionInterval().getStart().isAfter(gene.getExecutionInterval().getStart())) {
                firstGene = gene;
            }
        }
        return firstGene;
    }

    public DateTime getDeployStartTime() {
        return this.serviceAvailableTime.getStart().minus(containerDeploymentTime);
    }

}