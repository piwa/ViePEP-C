package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.Chromosome;
import lombok.Data;
import lombok.ToString;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.*;
import java.util.stream.Collectors;

@Data
@SuppressWarnings("Duplicates")
public class ContainerSchedulingUnit {

    private final UUID uid;
    private final long containerDeploymentDuration;
    private Container container;
    private VirtualMachineSchedulingUnit scheduledOnVm;
    private List<Chromosome.Gene> processStepGenes = new ArrayList<>();

    public ContainerSchedulingUnit(long containerDeploymentDuration) {
        this.containerDeploymentDuration = containerDeploymentDuration;
        this.uid = UUID.randomUUID();
    }

    //    public void addProcessStep(Chromosome.Gene processStep) {
//        processStepGenes.add(processStep);
//    }

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

        if(intervals.size() == 0) {
            return null;
        } else if(intervals.size() == 1) {
            return intervals.get(0);
        }
        Collections.sort(intervals, new IntervalStartComparator());

        DateTime start = intervals.get(0).getStart();
        DateTime end = intervals.get(intervals.size()-1).getEnd();

        return new Interval(start, end);
    }

    public DateTime getDeployStartTime() {
        return this.getServiceAvailableTime().getStart().minus(containerDeploymentDuration);
    }

    public Interval getResourceRequirementDuration() {
        return getServiceAvailableTime().withStart(this.getServiceAvailableTime().getStart().minus(containerDeploymentDuration));
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