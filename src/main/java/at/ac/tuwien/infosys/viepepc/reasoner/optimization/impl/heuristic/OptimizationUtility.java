package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.ServiceTypeSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import com.spotify.docker.client.messages.ContainerConfig;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class OptimizationUtility {

    @Autowired
    private CacheContainerService cacheContainerService;
    @Autowired
    private ContainerImageRegistryReader containerImageRegistryReader;
    @Autowired
    protected PlacementHelper placementHelper;
    @Value("${only.container.deploy.time}")
    private long onlyContainerDeployTime;


    public ContainerConfiguration getContainerConfiguration(ServiceType serviceType) throws ContainerConfigurationNotFoundException {
        ContainerConfiguration containerConfiguration = null;
        for (ContainerConfiguration tempContainerConfig : cacheContainerService.getContainerConfigurations(serviceType)) {
            if (containerConfiguration == null) {
                containerConfiguration = tempContainerConfig;
            }
            else if (containerConfiguration.getCPUPoints() > tempContainerConfig.getCPUPoints() || containerConfiguration.getRam() > tempContainerConfig.getRam()) {
                containerConfiguration = tempContainerConfig;
            }
        }
        if(containerConfiguration == null) {
            throw new ContainerConfigurationNotFoundException();
        }
        return containerConfiguration;
    }

    public Container getContainer(ServiceType serviceType) throws ContainerImageNotFoundException, ContainerConfigurationNotFoundException {
        ContainerConfiguration containerConfiguration = getContainerConfiguration(serviceType);

        ContainerImage containerImage = containerImageRegistryReader.findContainerImage(serviceType);

        Container container = new Container();
        container.setContainerConfiguration(containerConfiguration);
        container.setContainerImage(containerImage);

        return container;
    }

    public ContainerConfiguration getContainerConfiguration(ServiceType serviceType, int amount) throws ContainerConfigurationNotFoundException {
        ContainerConfiguration containerConfiguration = null;
        for (ContainerConfiguration tempContainerConfig : cacheContainerService.getContainerConfigurations(serviceType)) {
            if (containerConfiguration == null) {
                containerConfiguration = tempContainerConfig;
            }
            else if (containerConfiguration.getCPUPoints() > tempContainerConfig.getCPUPoints() || containerConfiguration.getRam() > tempContainerConfig.getRam()) {
                containerConfiguration = tempContainerConfig;
            }
        }
        if(containerConfiguration == null) {
            throw new ContainerConfigurationNotFoundException();
        }
        return containerConfiguration;
    }

    public List<Container> getContainer(ServiceType serviceType, int amount) throws ContainerImageNotFoundException, ContainerConfigurationNotFoundException {

        List<ContainerAndServiceAmount> containerAndServiceAmountList = new ArrayList<>();

        int multpilier = amount;
        int tempAmount = amount;
        while(tempAmount > 0) {
            double cpuLoad = serviceType.getServiceTypeResources().getCpuLoad() * multpilier;
            double ram = serviceType.getServiceTypeResources().getCpuLoad() * multpilier;

            try {
                ContainerConfiguration containerConfiguration = cacheContainerService.getBestContainerConfigurations(cpuLoad, ram);

                ContainerImage containerImage = containerImageRegistryReader.findContainerImage(serviceType);
                Container container = new Container();
                container.setContainerConfiguration(containerConfiguration);
                container.setContainerImage(containerImage);

                ContainerAndServiceAmount containerConfigurationAmount = new ContainerAndServiceAmount(multpilier, container);
                containerAndServiceAmountList.add(containerConfigurationAmount);

                tempAmount = tempAmount - multpilier;
                multpilier = tempAmount;

            } catch (ContainerConfigurationNotFoundException ex) {
                multpilier = multpilier - 1;
            }
        }


        List<Container> serviceContainerList = new ArrayList<>();
        for(int i = 0; i < amount; i++) {
            ContainerAndServiceAmount containerAndServiceAmount = containerAndServiceAmountList.get(0);

            int maxServiceAmount = containerAndServiceAmount.getAmount();
            maxServiceAmount = maxServiceAmount - 1;
            if(maxServiceAmount == 0) {
                containerAndServiceAmountList.remove(0);
            }
            else {
                containerAndServiceAmount.setAmount(maxServiceAmount);
            }
            serviceContainerList.add(containerAndServiceAmount.getContainer());

        }

        return serviceContainerList;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private class ContainerAndServiceAmount {
        private int amount;
        private final Container container;
    }

    public List<ServiceTypeSchedulingUnit> getRequiredServiceTypes(Chromosome chromosome) {
        List<ServiceTypeSchedulingUnit> requiredServiceTypeList = new ArrayList<>();
        getRequiredServiceTypesAndLastElements(chromosome, requiredServiceTypeList, new HashMap<>());
        return requiredServiceTypeList;
    }

    /**
     * returns a list of service types (can be mapped to containers) that have to be deployed to execute a list of process steps.
     * @param chromosome
     * @param requiredServiceTypeList
     * @param lastElements
     */
    public void getRequiredServiceTypesAndLastElements(Chromosome chromosome, List<ServiceTypeSchedulingUnit> requiredServiceTypeList, Map<String, Chromosome.Gene> lastElements) {
        Map<ServiceType, List<ServiceTypeSchedulingUnit>> requiredServiceTypeMap = new HashMap<>();
        List<List<Chromosome.Gene>> genes = chromosome.getGenes();

        for (List<Chromosome.Gene> row : genes) {           // one process

            for (Chromosome.Gene gene : row) {

                if(gene.getProcessStep().isLastElement()) {
                    Chromosome.Gene lastGene = lastElements.get(gene.getProcessStep().getWorkflowName());
                    if(lastGene == null || gene.getExecutionInterval().getEnd().isAfter(lastGene.getExecutionInterval().getEnd())) {
                        lastElements.put(gene.getProcessStep().getWorkflowName(), gene);
                    }

                }

                if (!requiredServiceTypeMap.containsKey(gene.getProcessStep().getServiceType())) {
                    requiredServiceTypeMap.put(gene.getProcessStep().getServiceType(), new ArrayList<>());
                }

                boolean overlapFound = false;
                List<ServiceTypeSchedulingUnit> requiredServiceTypes = requiredServiceTypeMap.get(gene.getProcessStep().getServiceType());
                for (ServiceTypeSchedulingUnit requiredServiceType : requiredServiceTypes) {
//                    Interval overlap = requiredServiceType.getServiceAvailableTime().overlap(gene.getExecutionInterval());
//                    if (overlap != null) {
                    if((requiredServiceType.getServiceAvailableTime().getStart().isBefore(gene.getExecutionInterval().getStart()) && requiredServiceType.getServiceAvailableTime().getEnd().isAfter(gene.getExecutionInterval().getEnd())) ||
                            (gene.getExecutionInterval().getStart().isBefore(requiredServiceType.getServiceAvailableTime().getEnd()) && gene.getExecutionInterval().getEnd().isAfter(requiredServiceType.getServiceAvailableTime().getEnd()))) {

                        DateTime newStartTime;
                        DateTime newEndTime;

                        Interval deploymentInterval = requiredServiceType.getServiceAvailableTime();
                        Interval geneInterval = gene.getExecutionInterval();
                        if(deploymentInterval.getStart().isBefore(geneInterval.getStart())) {
                            newStartTime = deploymentInterval.getStart();
                        }
                        else {
                            newStartTime = geneInterval.getStart();
                        }

                        if(deploymentInterval.getEnd().isAfter(geneInterval.getEnd())) {
                            newEndTime = deploymentInterval.getEnd();
                        }
                        else {
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
