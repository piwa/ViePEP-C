package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ContainerSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ProcessStepSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
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
    @Autowired
    protected VMSelectionHelper vmSelectionHelper;

    @Value("${container.default.deploy.time}")
    private long containerDeploymentTime;
    private boolean performChecks = true;

    public void checkContainerSchedulingUnits(Chromosome chromosome, String position) {

        if(performChecks) {
            List<ContainerSchedulingUnit> containerSchedulingUnits1 = new ArrayList<>();
            List<ContainerSchedulingUnit> containerSchedulingUnits2 = new ArrayList<>();
            for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
                containerSchedulingUnits1.add(gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit());
                containerSchedulingUnits2.addAll(gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm().getScheduledContainers());
            }
            for (ContainerSchedulingUnit containerSchedulingUnit : containerSchedulingUnits2) {
                if (!containerSchedulingUnits1.contains(containerSchedulingUnit)) {
                    log.error("A container is on a VM but not used by a Gene at=" + position);
                }
            }


            for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
                List<ContainerSchedulingUnit> list = new ArrayList<>(gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm().getScheduledContainers());
                Set<ContainerSchedulingUnit> set = new HashSet<>(list);
                if (set.size() < list.size()) {
                    log.error("Something is wrong at=" + position);
                }
            }

            Set<ContainerSchedulingUnit> set1 = new HashSet<>();
            Set<ContainerSchedulingUnit> set2 = new HashSet<>();
            for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
                set1.add(gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit());
                set2.addAll(gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm().getScheduledContainers());
            }
            if (!set1.equals(set2)) {
                log.error("Missmatch of gene container and VM container at=" + position);
            }

            for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
                if (gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm().getScheduledContainers().size() == 0) {
                    log.error("No container on VM at=" + position);
                }
            }

            for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
                ContainerSchedulingUnit containerSchedulingUnit = gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit();
                Set<ContainerSchedulingUnit> containerSchedulingUnitFromVM = containerSchedulingUnit.getScheduledOnVm().getScheduledContainers();
                if (!containerSchedulingUnitFromVM.contains(containerSchedulingUnit)) {
                    log.error("Container is not on VM at=" + position);
                }
            }

            Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = chromosome.getFlattenChromosome().stream().map(gene -> gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm()).collect(Collectors.toSet());
            for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : virtualMachineSchedulingUnits) {
                if (!vmSelectionHelper.checkEnoughResourcesLeftOnVM(virtualMachineSchedulingUnit)) {
                    log.error("not enough space on at=" + position + ", VM=" + virtualMachineSchedulingUnit);
                }
            }
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
            requiredContainerSchedulingMap.putIfAbsent(gene.getProcessStepSchedulingUnit().getProcessStep().getServiceType(), new ArrayList<>());

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
                ContainerSchedulingUnit newContainerSchedulingUnit = new ContainerSchedulingUnit(containerDeploymentTime, false);
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
