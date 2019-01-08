package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ContainerSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ProcessStepSchedulingUnit;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.Interval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

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

    public void checkContainerSchedulingUnits(Chromosome chromosome) {

        List<ContainerSchedulingUnit> containerSchedulingUnits1 = new ArrayList<>();
        Set<ContainerSchedulingUnit> containerSchedulingUnits2 = new HashSet<>();
        for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
            containerSchedulingUnits1.add(gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit());
            containerSchedulingUnits2.addAll(gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm().getScheduledContainers());
        }
        if(containerSchedulingUnits1.size() != containerSchedulingUnits2.size()) {
            log.info("Problem 1");
        }
    }

    public Container getContainer(ServiceType serviceType, int amount) throws ContainerImageNotFoundException {

        double cpuLoad = serviceType.getServiceTypeResources().getCpuLoad() + serviceType.getServiceTypeResources().getCpuLoad() * (amount - 1) * 2 / 3;
        double ram = serviceType.getServiceTypeResources().getMemory() + serviceType.getServiceTypeResources().getMemory() * (amount - 1) * 2 / 3;

        ContainerConfiguration bestContainerConfig = new ContainerConfiguration();
        bestContainerConfig.setId(0L);
        bestContainerConfig.setName(cpuLoad + "_" + ram);
        bestContainerConfig.setCores(cpuLoad / 100);
        bestContainerConfig.setRam(ram);
        bestContainerConfig.setDisc(100);

        ContainerImage containerImage = containerImageRegistryReader.findContainerImage(serviceType);
        Container container = new Container();
        container.setContainerConfiguration(bestContainerConfig);
        container.setContainerImage(containerImage);

        return container;

    }

//    public List<ContainerSchedulingUnit> createRequiredContainerSchedulingUnits(Chromosome chromosome) {
//        return createRequiredContainerSchedulingUnits(chromosome, null);
//    }

    public List<ContainerSchedulingUnit> createRequiredContainerSchedulingUnits(Chromosome chromosome, Map<ProcessStepSchedulingUnit, ContainerSchedulingUnit> fixedContainerSchedulingUnitMap) {
        List<ContainerSchedulingUnit> containerSchedulingUnits = createRequiredContainerSchedulingUnits(chromosome);
        Collection<ContainerSchedulingUnit> fixedContainerSchedulingUnits = fixedContainerSchedulingUnitMap.values();

        containerSchedulingUnits.forEach(schedulingUnit -> {
            if (!fixedContainerSchedulingUnits.contains(schedulingUnit)) {
                try {
                    Container container = getContainer(schedulingUnit.getProcessStepGenes().get(0).getProcessStepSchedulingUnit().getProcessStep().getServiceType(), schedulingUnit.getProcessStepGenes().size());
                    schedulingUnit.setContainer(container);
                    schedulingUnit.getProcessStepGenes().forEach(gene -> gene.getProcessStepSchedulingUnit().setContainerSchedulingUnit(schedulingUnit));
                } catch (ContainerImageNotFoundException e) {
                    log.error("Exception", e);
                }
            }
        });
        return containerSchedulingUnits;
    }

    /**
     * returns a list of service types (can be mapped to containers) that have to be deployed to execute a list of process steps.
     *
     * @param chromosome
     */
    public List<ContainerSchedulingUnit> createRequiredContainerSchedulingUnits(Chromosome chromosome) {
        Map<ServiceType, List<ContainerSchedulingUnit>> requiredContainerSchedulingMap = new HashMap<>();

        chromosome.getGenes().stream().flatMap(Collection::stream).filter(gene -> !gene.isFixed()).forEach(gene -> {
            if (!requiredContainerSchedulingMap.containsKey(gene.getProcessStepSchedulingUnit().getProcessStep().getServiceType())) {
                requiredContainerSchedulingMap.put(gene.getProcessStepSchedulingUnit().getProcessStep().getServiceType(), new ArrayList<>());
            }

            boolean overlapFound = false;
            List<ContainerSchedulingUnit> requiredServiceTypes = requiredContainerSchedulingMap.get(gene.getProcessStepSchedulingUnit().getProcessStep().getServiceType());
            for (ContainerSchedulingUnit containerSchedulingUnit : requiredServiceTypes) {
                Interval overlap = containerSchedulingUnit.getServiceAvailableTime().overlap(gene.getExecutionInterval());
                if (overlap != null) {

                    if (!containerSchedulingUnit.getProcessStepGenes().contains(gene)) {
                        containerSchedulingUnit.getProcessStepGenes().add(gene);
                    }
                    overlapFound = true;
                    break;
                }
            }

            if (!overlapFound) {
                ContainerSchedulingUnit newContainerSchedulingUnit = new ContainerSchedulingUnit(containerDeploymentTime);
                newContainerSchedulingUnit.getProcessStepGenes().add(gene);

                requiredContainerSchedulingMap.get(gene.getProcessStepSchedulingUnit().getProcessStep().getServiceType()).add(newContainerSchedulingUnit);
            }
        });

        return requiredContainerSchedulingMap.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    public Map<String, Chromosome.Gene> getLastElements(Chromosome chromosome) {
        Map<String, Chromosome.Gene> lastElements = new HashMap<>();

        chromosome.getGenes().stream().flatMap(Collection::stream).filter(gene -> gene.getProcessStepSchedulingUnit().getProcessStep().isLastElement()).forEach(gene -> {
            Chromosome.Gene lastGene = lastElements.get(gene.getProcessStepSchedulingUnit().getWorkflowName());
            if (lastGene == null || gene.getExecutionInterval().getEnd().isAfter(lastGene.getExecutionInterval().getEnd())) {
                lastElements.put(gene.getProcessStepSchedulingUnit().getWorkflowName(), gene);
            }
        });

        return lastElements;
    }

}
