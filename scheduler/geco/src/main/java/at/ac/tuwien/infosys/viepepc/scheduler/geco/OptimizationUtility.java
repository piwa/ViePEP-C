package at.ac.tuwien.infosys.viepepc.scheduler.geco;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.ServiceTypeSchedulingUnit;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OptimizationUtility {


    @Autowired
    private ContainerImageRegistryReader containerImageRegistryReader;
    @Autowired
    protected WorkflowUtilities workflowUtilities;
    @Value("${only.container.deploy.time}")
    private long onlyContainerDeployTime;

    public Container getContainer(ServiceType serviceType, int amount) throws ContainerImageNotFoundException {

        double cpuLoad = serviceType.getServiceTypeResources().getCpuLoad() + serviceType.getServiceTypeResources().getCpuLoad() * (amount - 1) * 2 / 3;
        double ram = serviceType.getServiceTypeResources().getMemory() + serviceType.getServiceTypeResources().getMemory() * (amount - 1) * 2 / 3;

        ContainerConfiguration bestContainerConfig = new ContainerConfiguration();
        bestContainerConfig.setId(0L);
        bestContainerConfig.setName(String.valueOf(cpuLoad) + "_" + String.valueOf(ram));
        bestContainerConfig.setCores(cpuLoad / 100);
        bestContainerConfig.setRam(ram);
        bestContainerConfig.setDisc(100);

        ContainerImage containerImage = containerImageRegistryReader.findContainerImage(serviceType);
        Container container = new Container();
        container.setContainerConfiguration(bestContainerConfig);
        container.setContainerImage(containerImage);

        return container;

    }


    public List<ServiceTypeSchedulingUnit> getRequiredServiceTypes(Chromosome chromosome) {
        List<ServiceTypeSchedulingUnit> requiredServiceTypeList = new ArrayList<>();
        getRequiredServiceTypesAndLastElements(chromosome, requiredServiceTypeList, new HashMap<>());
        return requiredServiceTypeList;
    }

    /**
     * returns a list of service types (can be mapped to containers) that have to be deployed to execute a list of process steps.
     *
     * @param chromosome
     * @param requiredServiceTypeList
     * @param lastElements
     */
    public void getRequiredServiceTypesAndLastElements(Chromosome chromosome, List<ServiceTypeSchedulingUnit> requiredServiceTypeList, Map<String, Chromosome.Gene> lastElements) {
        Map<ServiceType, List<ServiceTypeSchedulingUnit>> requiredServiceTypeMap = new HashMap<>();
        List<List<Chromosome.Gene>> genes = chromosome.getGenes();

        for (List<Chromosome.Gene> row : genes) {           // one process

            for (Chromosome.Gene gene : row) {

                if (gene.getProcessStep().isLastElement()) {
                    Chromosome.Gene lastGene = lastElements.get(gene.getProcessStep().getWorkflowName());
                    if (lastGene == null || gene.getExecutionInterval().getEnd().isAfter(lastGene.getExecutionInterval().getEnd())) {
                        lastElements.put(gene.getProcessStep().getWorkflowName(), gene);
                    }

                }

                if (!requiredServiceTypeMap.containsKey(gene.getProcessStep().getServiceType())) {
                    requiredServiceTypeMap.put(gene.getProcessStep().getServiceType(), new ArrayList<>());
                }

                boolean overlapFound = false;
                List<ServiceTypeSchedulingUnit> requiredServiceTypes = requiredServiceTypeMap.get(gene.getProcessStep().getServiceType());
                for (ServiceTypeSchedulingUnit requiredServiceType : requiredServiceTypes) {
                    Interval overlap = requiredServiceType.getServiceAvailableTime().overlap(gene.getExecutionInterval());
                    if (overlap != null) {
//                    if((requiredServiceType.getServiceAvailableTime().getStart().isBefore(gene.getExecutionInterval().getStart()) && requiredServiceType.getServiceAvailableTime().getEnd().isAfter(gene.getExecutionInterval().getEnd())) ||
//                            (gene.getExecutionInterval().getStart().isBefore(requiredServiceType.getServiceAvailableTime().getEnd()) && gene.getExecutionInterval().getEnd().isAfter(requiredServiceType.getServiceAvailableTime().getEnd()))) {

                        DateTime newStartTime;
                        DateTime newEndTime;

                        Interval deploymentInterval = requiredServiceType.getServiceAvailableTime();
                        Interval geneInterval = gene.getExecutionInterval();
                        if (deploymentInterval.getStart().isBefore(geneInterval.getStart())) {
                            newStartTime = deploymentInterval.getStart();
                        } else {
                            newStartTime = geneInterval.getStart();
                        }

                        if (deploymentInterval.getEnd().isAfter(geneInterval.getEnd())) {
                            newEndTime = deploymentInterval.getEnd();
                        } else {
                            newEndTime = geneInterval.getEnd();
                        }

                        requiredServiceType.setServiceAvailableTime(new Interval(newStartTime, newEndTime));
                        requiredServiceType.getProcessSteps().add(gene);

                        overlapFound = true;
                        break;
                    }
                }

                if (!overlapFound) {
                    ServiceTypeSchedulingUnit newServiceTypeSchedulingUnit = new ServiceTypeSchedulingUnit(gene.getProcessStep().getServiceType(), this.onlyContainerDeployTime);
                    newServiceTypeSchedulingUnit.setServiceAvailableTime(gene.getExecutionInterval());
                    newServiceTypeSchedulingUnit.addProcessStep(gene);

                    requiredServiceTypeMap.get(gene.getProcessStep().getServiceType()).add(newServiceTypeSchedulingUnit);
                }
            }
        }

        requiredServiceTypeMap.forEach((k, v) -> requiredServiceTypeList.addAll(v));

    }


}
