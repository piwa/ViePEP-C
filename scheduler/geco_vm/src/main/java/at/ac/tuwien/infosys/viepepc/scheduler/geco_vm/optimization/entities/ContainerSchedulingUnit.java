package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
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
    private final boolean fixed;
    private Container container;
    private VirtualMachineSchedulingUnit scheduledOnVm;
    private Set<Chromosome.Gene> processStepGenes = new HashSet<>();

    private ContainerSchedulingUnit(UUID uid, long containerDeploymentDuration, boolean fixed) {
        this.containerDeploymentDuration = containerDeploymentDuration;
        this.uid = uid;
        this.fixed = fixed;
    }

    public ContainerSchedulingUnit(long containerDeploymentDuration, boolean fixed) {
        this(UUID.randomUUID(), containerDeploymentDuration, fixed);
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
        ContainerSchedulingUnit clone = new ContainerSchedulingUnit(this.uid, this.containerDeploymentDuration, this.fixed);
        clone.setContainer(this.container);

        return clone;
    }

    @Override
    public String toString() {
        return "ContainerSchedulingUnit{" +
                "internId=" + uid.toString().substring(0,8) +
                ", serviceAvailableTime=" + getServiceAvailableTime() +
                ", fixed=" + fixed +
                ", scheduledOnVm=" + scheduledOnVm +
                ", container=" + container +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerSchedulingUnit that = (ContainerSchedulingUnit) o;
        return uid.equals(that.uid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid);
    }

}