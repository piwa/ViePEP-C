package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Getter
public class ServiceTypeSchedulingUnit {

    @Setter private Interval serviceAvailableTime;
    private final ServiceType serviceType;
    private final long containerDeploymentDuration;
    @Setter private VirtualMachineSchedulingUnit virtualMachineSchedulingUnit;
    private final boolean isFixed;
    @Setter private Container container;
    private List<Chromosome.Gene> genes = new ArrayList<>();


    public void addProcessStep(Chromosome.Gene processStep) {
        genes.add(processStep);
    }

    public Chromosome.Gene getFirstGene() {
        Chromosome.Gene firstGene = null;
        for (Chromosome.Gene gene : genes) {
            if (firstGene == null || firstGene.getExecutionInterval().getStart().isAfter(gene.getExecutionInterval().getStart())) {
                firstGene = gene;
            }
        }
        return firstGene;
    }

    public DateTime getDeployStartTime() {
        return this.serviceAvailableTime.getStart().minus(containerDeploymentDuration);
    }

    public Interval getCloudResourceUsage() {
        return this.serviceAvailableTime.withStart(this.getServiceAvailableTime().getStart().minus(containerDeploymentDuration));
    }

    public ServiceTypeSchedulingUnit clone() {
        ServiceTypeSchedulingUnit clone = new ServiceTypeSchedulingUnit(serviceType, containerDeploymentDuration, isFixed);
        clone.setServiceAvailableTime(new Interval(this.serviceAvailableTime));
//        if(this.virtualMachineSchedulingUnit != null) {
//            clone.setVirtualMachineSchedulingUnit(this.virtualMachineSchedulingUnit.clone());
//        }
//        if(this.container != null) {
//            try {
//                clone.setContainer(this.container.clone(serviceType));
//            } catch (CloneNotSupportedException e) {
//                e.printStackTrace();
//            }
//        }
        for (Chromosome.Gene gene : genes) {
            clone.getGenes().add(gene.clone());
        }
        return clone;
    }

}