package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.FitnessFunction;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.ServiceTypeSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
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

    public List<ServiceTypeSchedulingUnit> getRequiredServiceTypes(Chromosome chromosome) {
        List<ServiceTypeSchedulingUnit> requiredServiceTypeList = new ArrayList<>();
        getRequiredServiceTypesAndLastElements(chromosome, requiredServiceTypeList, new ArrayList<>());
        return requiredServiceTypeList;
    }

    /**
     * returns a list of service types (can be mapped to containers) that have to be deployed to execute a list of process steps.
     * @param chromosome
     * @param requiredServiceTypeList
     * @param lastElements
     */
    public void getRequiredServiceTypesAndLastElements(Chromosome chromosome, List<ServiceTypeSchedulingUnit> requiredServiceTypeList, List<Chromosome.Gene> lastElements) {
        Map<ServiceType, List<ServiceTypeSchedulingUnit>> requiredServiceTypeMap = new HashMap<>();
        List<List<Chromosome.Gene>> genes = chromosome.getGenes();

        for (List<Chromosome.Gene> row : genes) {           // one process

            for (Chromosome.Gene gene : row) {

                if(gene.getProcessStep().isLastElement()) {
                    lastElements.add(gene);
                }

                if (!requiredServiceTypeMap.containsKey(gene.getProcessStep().getServiceType())) {
                    requiredServiceTypeMap.put(gene.getProcessStep().getServiceType(), new ArrayList<>());
                }

                boolean overlapFound = false;
                List<ServiceTypeSchedulingUnit> requiredServiceTypes = requiredServiceTypeMap.get(gene.getProcessStep().getServiceType());
                for (ServiceTypeSchedulingUnit requiredServiceType : requiredServiceTypes) {
                    Interval overlap = requiredServiceType.getDeploymentInterval().overlap(gene.getExecutionInterval());

                    if (overlap != null) {

                        DateTime newStartTime;
                        DateTime newEndTime;

                        Interval deploymentInterval = requiredServiceType.getDeploymentInterval();
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

                        requiredServiceType.setDeploymentInterval(new Interval(newStartTime, newEndTime));
                        requiredServiceType.getProcessSteps().add(gene.getProcessStep());

                        overlapFound = true;
                        break;
                    }
                }

                if (!overlapFound) {
                    ServiceTypeSchedulingUnit newServiceTypeSchedulingUnit = new ServiceTypeSchedulingUnit(gene.getProcessStep().getServiceType());
                    newServiceTypeSchedulingUnit.setDeploymentInterval(gene.getExecutionInterval());
                    newServiceTypeSchedulingUnit.addProcessStep(gene.getProcessStep());

                    requiredServiceTypeMap.get(gene.getProcessStep().getServiceType()).add(newServiceTypeSchedulingUnit);
                }
            }
        }

        requiredServiceTypeMap.forEach((k, v) -> requiredServiceTypeList.addAll(v));

    }
}
