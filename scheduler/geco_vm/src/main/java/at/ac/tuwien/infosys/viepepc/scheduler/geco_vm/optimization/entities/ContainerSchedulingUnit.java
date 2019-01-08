package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.Chromosome;
import lombok.Data;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.*;
import java.util.stream.Collectors;

@Data
@SuppressWarnings("Duplicates")
public class ContainerSchedulingUnit implements Cloneable {

    private final UUID uid;
    private final long containerDeploymentDuration;
    private Container container;
    private VirtualMachineSchedulingUnit scheduledOnVm;
    private List<Chromosome.Gene> processStepGenes = new ArrayList<>();

    private ContainerSchedulingUnit(UUID uid, long containerDeploymentDuration) {
        this.containerDeploymentDuration = containerDeploymentDuration;
        this.uid = uid;
    }

    public ContainerSchedulingUnit(long containerDeploymentDuration) {
        this(UUID.randomUUID(), containerDeploymentDuration);
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

    public Interval getServiceAvailableTime() {
        List<Interval> intervals = processStepGenes.stream().map(Chromosome.Gene::getExecutionInterval).collect(Collectors.toList());
        return IntervalMergeHelper.mergeIntervals(intervals);
    }

    public DateTime getDeployStartTime() {
        return this.getServiceAvailableTime().getStart().minus(containerDeploymentDuration);
    }

    public Interval getCloudResourceUsage() {
        return getServiceAvailableTime().withStart(this.getServiceAvailableTime().getStart().minus(containerDeploymentDuration));
    }

    @Override
    public ContainerSchedulingUnit clone()  {
        ContainerSchedulingUnit clone = new ContainerSchedulingUnit(this.uid, this.containerDeploymentDuration);
        clone.setContainer(this.container);
        clone.setScheduledOnVm(this.scheduledOnVm);
        clone.setProcessStepGenes(new ArrayList<>(this.processStepGenes));
        return clone;
    }

    @Override
    public String toString() {
        return "ContainerSchedulingUnit{" +
                "serviceAvailableTime=" + getServiceAvailableTime() +
                ", container=" + container +
                ", scheduledOnVm=" + scheduledOnVm +
                '}';
    }
}