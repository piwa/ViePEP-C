package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ProcessStepSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ServiceTypeSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
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
    @Value("${perform.correctness.checks}")
    private boolean performChecks = true;


    public Container getContainer(ServiceType serviceType, int amount) throws ContainerImageNotFoundException {

        double cpuLoad = serviceType.getServiceTypeResources().getCpuLoad() + serviceType.getServiceTypeResources().getCpuLoad() * (amount - 1) * 2 / 3;
        double ram = serviceType.getServiceTypeResources().getMemory() + serviceType.getServiceTypeResources().getMemory() * (amount - 1);// * 2 / 3;

        ContainerConfiguration bestContainerConfig = new ContainerConfiguration();
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

    public Container resizeContainer(Container container, ServiceType serviceType, int amount) {
        double cpuLoad = serviceType.getServiceTypeResources().getCpuLoad() + serviceType.getServiceTypeResources().getCpuLoad() * (amount - 1) * 2 / 3;
        double ram = serviceType.getServiceTypeResources().getMemory() + serviceType.getServiceTypeResources().getMemory() * (amount - 1);// * 2 / 3;

        ContainerConfiguration bestContainerConfig = new ContainerConfiguration();
        bestContainerConfig.setName(cpuLoad + "_" + ram);
        bestContainerConfig.setCores(cpuLoad / 100);
        bestContainerConfig.setRam(ram);
        bestContainerConfig.setDisc(100);

        container.setContainerConfiguration(bestContainerConfig);
        return container;
    }

    /**
     * returns a list of service types (can be mapped to containers) that have to be deployed to execute a list of process steps.
     *
     * @param chromosome
     */
    public List<ServiceTypeSchedulingUnit> getRequiredServiceTypesVMSeparation(Chromosome chromosome) {

        List<ServiceTypeSchedulingUnit> returnList = new ArrayList<>();
        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = chromosome.getFlattenChromosome().stream().map(gene -> gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit()).filter(Objects::nonNull).collect(Collectors.toSet());
        for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : virtualMachineSchedulingUnits) {
            returnList.addAll(getRequiredServiceTypesOneVM(virtualMachineSchedulingUnit));
        }

        return returnList;
    }

    public List<ServiceTypeSchedulingUnit> getRequiredServiceTypesOneVM(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit) {
        return getRequiredServiceTypesOneVM(virtualMachineSchedulingUnit, new ArrayList<>());
    }

    @NotNull
    public List<ServiceTypeSchedulingUnit> getRequiredServiceTypesOneVM(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, List<ProcessStepSchedulingUnit> additionalProcessSteps) {
        Set<ProcessStepSchedulingUnit> processStepSchedulingUnitSet = new HashSet<>(virtualMachineSchedulingUnit.getProcessStepSchedulingUnits());
        processStepSchedulingUnitSet.addAll(additionalProcessSteps);
        List<ProcessStepSchedulingUnit> processStepSchedulingUnits = new ArrayList<>(processStepSchedulingUnitSet);
        return getRequiredServiceTypes(processStepSchedulingUnits, false);
    }

    public List<ServiceTypeSchedulingUnit> getRequiredServiceTypes(Chromosome chromosome, boolean withContainerResize) {
        Set<ProcessStepSchedulingUnit> processStepSchedulingUnitSet = chromosome.getFlattenChromosome().stream().map(Chromosome.Gene::getProcessStepSchedulingUnit).collect(Collectors.toSet());
        List<ProcessStepSchedulingUnit> processStepSchedulingUnits = new ArrayList<>(processStepSchedulingUnitSet);
        return getRequiredServiceTypes(processStepSchedulingUnits, withContainerResize);
    }

    private List<ServiceTypeSchedulingUnit> getRequiredServiceTypes(List<ProcessStepSchedulingUnit> processStepSchedulingUnits, boolean withContainerResize) {
        processStepSchedulingUnits.sort(Comparator.comparing(unit -> unit.getGene().getExecutionInterval().getStart()));

        Map<ServiceType, List<ServiceTypeSchedulingUnit>> requiredServiceTypeMap = new HashMap<>();
        for (ProcessStepSchedulingUnit processStepSchedulingUnit : processStepSchedulingUnits) {
            Chromosome.Gene gene = processStepSchedulingUnit.getGene();
            requiredServiceTypeMap.putIfAbsent(processStepSchedulingUnit.getProcessStep().getServiceType(), new ArrayList<>());

            boolean overlapFound = false;
//            if (!gene.isFixed()) {
            List<ServiceTypeSchedulingUnit> requiredServiceTypes = requiredServiceTypeMap.get(gene.getProcessStepSchedulingUnit().getProcessStep().getServiceType());
            for (ServiceTypeSchedulingUnit requiredServiceType : requiredServiceTypes) {
                Interval overlap = requiredServiceType.getServiceAvailableTime().overlap(gene.getExecutionInterval());
                if (overlap != null) {
                    Interval deploymentInterval = requiredServiceType.getServiceAvailableTime();
                    Interval geneInterval = gene.getExecutionInterval();
                    long newStartTime = Math.min(geneInterval.getStartMillis(), deploymentInterval.getStartMillis());
                    long newEndTime = Math.max(geneInterval.getEndMillis(), deploymentInterval.getEndMillis());

                    requiredServiceType.setServiceAvailableTime(new Interval(newStartTime, newEndTime));
                    requiredServiceType.getGenes().add(gene);

                    overlapFound = true;
                    break;
                }
            }
//            }

            if (!overlapFound) {
                ServiceTypeSchedulingUnit newServiceTypeSchedulingUnit = new ServiceTypeSchedulingUnit(processStepSchedulingUnit.getProcessStep().getServiceType(), this.containerDeploymentTime, gene.isFixed());
                newServiceTypeSchedulingUnit.setServiceAvailableTime(gene.getExecutionInterval());
                newServiceTypeSchedulingUnit.addProcessStep(gene);
                if (newServiceTypeSchedulingUnit.isFixed()) {
                    newServiceTypeSchedulingUnit.setContainer(gene.getProcessStepSchedulingUnit().getProcessStep().getContainer());
                    newServiceTypeSchedulingUnit.setVirtualMachineSchedulingUnit(gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit());
                }

                requiredServiceTypeMap.get(processStepSchedulingUnit.getProcessStep().getServiceType()).add(newServiceTypeSchedulingUnit);
            }
        }

        List<ServiceTypeSchedulingUnit> returnList = new ArrayList<>();
        requiredServiceTypeMap.forEach((k, v) -> returnList.addAll(v));

        returnList.forEach(unit -> {
            try {

                for (Chromosome.Gene gene : unit.getGenes()) {
                    if (gene.isFixed()) {
                        unit.setContainer(gene.getProcessStepSchedulingUnit().getProcessStep().getContainer());
                        unit.setVirtualMachineSchedulingUnit(gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit());
                        break;
                    }
                }

                if (!withContainerResize || unit.getContainer() == null) {
                    unit.setContainer(getContainer(unit.getServiceType(), unit.getGenes().size()));
                } else {
                    unit.setContainer(resizeContainer(unit.getContainer(), unit.getServiceType(), unit.getGenes().size()));
                }
            } catch (ContainerImageNotFoundException e) {
                log.error("Could not find a fitting container");
            }
        });

        return returnList;
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
