package at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer;

import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Getter
public class ServiceTypeSchedulingUnit {

    @Setter
    private Interval serviceAvailableTime;
    private final ServiceType serviceType;
    private final long onlyContainerDeploymentTime;
    private List<Chromosome.Gene> processSteps = new ArrayList<>();


    public void addProcessStep(Chromosome.Gene processStep) {
        processSteps.add(processStep);
    }

    public Chromosome.Gene getFirstGene() {
        Chromosome.Gene firstGene = null;
        for (Chromosome.Gene gene : processSteps) {
            if (firstGene == null || firstGene.getExecutionInterval().getStart().isAfter(gene.getExecutionInterval().getStart())) {
                firstGene = gene;
            }
        }
        return firstGene;
    }

    public DateTime getDeployStartTime() {
        return this.serviceAvailableTime.getStart().minus(onlyContainerDeploymentTime);
    }

}