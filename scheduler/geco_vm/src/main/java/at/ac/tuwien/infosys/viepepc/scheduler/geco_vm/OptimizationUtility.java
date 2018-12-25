package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities.ContainerSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities.ProcessStepSchedulingUnit;
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
@SuppressWarnings("Duplicates")
public class OptimizationUtility {


    @Autowired
    private ContainerImageRegistryReader containerImageRegistryReader;
    @Autowired
    protected WorkflowUtilities workflowUtilities;
    @Value("${container.default.deploy.time}")
    private long containerDeploymentTime;

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

    public List<ContainerSchedulingUnit> getContainerSchedulingUnit(Chromosome chromosome) {
        return getContainerSchedulingUnit(chromosome, null);
    }

    public List<ContainerSchedulingUnit> getContainerSchedulingUnit(Chromosome chromosome, Map<ProcessStepSchedulingUnit, ContainerSchedulingUnit> fixedContainerSchedulingUnitMap) {
        List<ContainerSchedulingUnit> containerSchedulingUnits = new ArrayList<>();
        getRequiredServiceTypesAndLastElements(chromosome, containerSchedulingUnits, new HashMap<>(), fixedContainerSchedulingUnitMap);

        containerSchedulingUnits.forEach(schedulingUnit -> {
            try {
                Container container = getContainer(schedulingUnit.getProcessStepGenes().get(0).getProcessStep().getServiceType(), schedulingUnit.getProcessStepGenes().size());
                schedulingUnit.setContainerConfiguration(container.getContainerConfiguration());
                schedulingUnit.getProcessStepGenes().forEach(gene -> gene.getProcessStep().setContainerSchedulingUnit(schedulingUnit));
            } catch (ContainerImageNotFoundException e) {
                log.error("Exception", e);
            }

        });
        return containerSchedulingUnits;
    }

    public void getRequiredServiceTypesAndLastElements(Chromosome chromosome, List<ContainerSchedulingUnit> requiredServiceTypeList, Map<String, Chromosome.Gene> lastElements) {
        getRequiredServiceTypesAndLastElements(chromosome, requiredServiceTypeList, lastElements, null);
    }

    /**
     * returns a list of service types (can be mapped to containers) that have to be deployed to execute a list of process steps.
     *
     * @param chromosome
     * @param requiredServiceTypeList
     */
    public void getRequiredServiceTypesAndLastElements(Chromosome chromosome, List<ContainerSchedulingUnit> requiredServiceTypeList, Map<String, Chromosome.Gene> lastElements, Map<ProcessStepSchedulingUnit, ContainerSchedulingUnit> fixedContainerSchedulingUnitMap) {
        Map<ServiceType, List<ContainerSchedulingUnit>> requiredServiceTypeMap = new HashMap<>();

        if(fixedContainerSchedulingUnitMap != null) {
            fixedContainerSchedulingUnitMap.forEach((processStepSchedulingUnit, containerSchedulingUnit) -> {
                ServiceType serviceType = processStepSchedulingUnit.getServiceType();
                if (!requiredServiceTypeMap.containsKey(serviceType)) {
                    requiredServiceTypeMap.put(serviceType, new ArrayList<>());
                }
                requiredServiceTypeMap.get(serviceType).add(containerSchedulingUnit);
            });
        }

        for (List<Chromosome.Gene> row : chromosome.getGenes()) {           // one process

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
                List<ContainerSchedulingUnit> requiredServiceTypes = requiredServiceTypeMap.get(gene.getProcessStep().getServiceType());
                for (ContainerSchedulingUnit requiredServiceType : requiredServiceTypes) {
                    Interval overlap = requiredServiceType.getServiceAvailableTime().overlap(gene.getExecutionInterval());
                    if (overlap != null) {

                        Interval deploymentInterval = requiredServiceType.getServiceAvailableTime();
                        Interval geneInterval = gene.getExecutionInterval();
                        DateTime newStartTime = new DateTime(Math.min(deploymentInterval.getStartMillis(), geneInterval.getStartMillis()));
                        DateTime newEndTime = new DateTime(Math.max(deploymentInterval.getEndMillis(), geneInterval.getEndMillis()));

                        requiredServiceType.setServiceAvailableTime(new Interval(newStartTime, newEndTime));
                        requiredServiceType.getProcessStepGenes().add(gene);

                        overlapFound = true;
                        break;
                    }
                }

                if (!overlapFound) {
                    ContainerSchedulingUnit newContainerSchedulingUnit = new ContainerSchedulingUnit(containerDeploymentTime);
                    newContainerSchedulingUnit.setServiceAvailableTime(gene.getExecutionInterval());
                    newContainerSchedulingUnit.addProcessStep(gene);

                    requiredServiceTypeMap.get(gene.getProcessStep().getServiceType()).add(newContainerSchedulingUnit);
                }
            }
        }

        requiredServiceTypeMap.forEach((k, v) -> requiredServiceTypeList.addAll(v));

    }

    public void addContainerToOverlappingMap(Map<Interval, List<Container>> overlappingContainer, ContainerSchedulingUnit filteredSchedulingUnit, Container container) {
        boolean overlapFound = false;
        if (!overlappingContainer.isEmpty()) {
            Interval interval = null;
            List<Container> containers = new ArrayList<>();
            Interval newInterval = null;
            for (Map.Entry<Interval, List<Container>> entry : overlappingContainer.entrySet()) {
                interval = entry.getKey();
                containers = entry.getValue();

                if (filteredSchedulingUnit.getServiceAvailableTime().overlap(interval) != null) {
                    containers.add(container);
                    newInterval = new Interval(interval);
                    newInterval = newInterval.withStartMillis(Math.min(interval.getStartMillis(), filteredSchedulingUnit.getServiceAvailableTime().getStartMillis()));
                    newInterval = newInterval.withEndMillis(Math.max(interval.getEndMillis(), filteredSchedulingUnit.getServiceAvailableTime().getEndMillis()));

                    overlapFound = true;
                    break;
                }
            }
            if(overlapFound) {
                overlappingContainer.put(newInterval, containers);
                overlappingContainer.remove(interval);
            }
        }

        if (!overlapFound) {
            overlappingContainer.put(filteredSchedulingUnit.getServiceAvailableTime(), new ArrayList<>());
            overlappingContainer.get(filteredSchedulingUnit.getServiceAvailableTime()).add(container);
        }
    }

}
