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
    @Value("${perform.correctnes.checks}")
    private boolean performChecks = true;

//    public void checkIfFixedGeneHasContainerSchedulingUnit(Chromosome chromosome, String position) {
//        if (performChecks) {
//            for (List<Chromosome.Gene> row : chromosome.getGenes()) {
//                for (Chromosome.Gene gene : row) {
//                    if (gene.isFixed()) {
//                        if (gene.getProcessStepSchedulingUnit() == null) {
//                            log.error("processStepSchedulingUnit is null (at=" + position + ") gene=" + gene);
//                        } else if (gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit() == null) {
//                            log.error("ContainerSchedulingUnit is null (at=" + position + ") gene=" + gene);
//                        } else if (gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm() == null) {
//                            log.error("scheduledOnVm is null (at=" + position + ") gene=" + gene);
//                        } else {
//                            for (ContainerSchedulingUnit scheduledContainer : gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm().getScheduledContainers()) {
//                                if (scheduledContainer == null) {
//                                    log.error("one element of ScheduledContainers is null (at=" + position + ") gene=" + gene);
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }

    public void checkContainerSchedulingUnits(Chromosome chromosome, String position) {

        if (performChecks) {

            for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
                if (gene.getProcessStepSchedulingUnit() == null) {
                    log.debug("processStepSchedulingUnit is null (at=" + position + ") gene=" + gene);
                } else if (gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit() == null) {
                    log.debug("getVirtualMachineSchedulingUnit is null (at=" + position + ") gene=" + gene);
                } else {
                    for (ProcessStepSchedulingUnit scheduledProcessStep : gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit().getProcessStepSchedulingUnits()) {
                        if (scheduledProcessStep == null) {
                            log.debug("one element of ProcessSteps on the VM is null (at=" + position + ") gene=" + gene);
                        }
                    }
                }
            }

            List<ProcessStepSchedulingUnit> processStepSchedulingUnits1 = new ArrayList<>();
            List<ProcessStepSchedulingUnit> processStepSchedulingUnits2 = new ArrayList<>();
            for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
                processStepSchedulingUnits1.add(gene.getProcessStepSchedulingUnit());
                processStepSchedulingUnits2.addAll(gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit().getProcessStepSchedulingUnits());
            }
            for (ProcessStepSchedulingUnit processStepSchedulingUnit : processStepSchedulingUnits2) {
                if (!processStepSchedulingUnits1.contains(processStepSchedulingUnit)) {
                    log.debug("A container is on a VM but not used by a Gene (at=" + position + ")");
                }
            }

            for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
                if (gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit().getProcessStepSchedulingUnits().size() == 0) {
                    log.debug("No ProcessStep on VM (at=" + position + ")");
                }
            }

            for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
                ProcessStepSchedulingUnit processStepSchedulingUnit = gene.getProcessStepSchedulingUnit();
                Set<ProcessStepSchedulingUnit> processStepSchedulingUnitsFromVM = processStepSchedulingUnit.getVirtualMachineSchedulingUnit().getProcessStepSchedulingUnits();
                if (!processStepSchedulingUnitsFromVM.contains(processStepSchedulingUnit)) {
                    log.debug("ProcessStep is not on VM (at=" + position + ")");
                }
            }

            Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = chromosome.getFlattenChromosome().stream().map(gene -> gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit()).collect(Collectors.toSet());
            for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : virtualMachineSchedulingUnits) {
                if(virtualMachineSchedulingUnit.getCloudResourceUsageInterval().getEnd().isBefore(vmSelectionHelper.getOptimizationEndTime())) {
                    log.debug("vm end time before optimizationEndTime (at=" + position + ") on VM=" + virtualMachineSchedulingUnit);
                }
                if (!vmSelectionHelper.checkIfVirtualMachineIsBigEnough(virtualMachineSchedulingUnit)) {
                    log.debug("not enough space (at=" + position + ") on VM=" + virtualMachineSchedulingUnit);
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

    /**
     * returns a list of service types (can be mapped to containers) that have to be deployed to execute a list of process steps.
     *
     * @param chromosome
     */
    public List<ServiceTypeSchedulingUnit> getRequiredServiceTypes(Chromosome chromosome) {

        List<ServiceTypeSchedulingUnit> returnList = new ArrayList<>();
        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = chromosome.getFlattenChromosome().stream().map(gene -> gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit()).filter(Objects::nonNull).collect(Collectors.toSet());
        virtualMachineSchedulingUnits.forEach(virtualMachineSchedulingUnit -> {
            returnList.addAll(getRequiredServiceTypesOneVM(virtualMachineSchedulingUnit));
        });

        return returnList;
    }

    public List<ServiceTypeSchedulingUnit> getRequiredServiceTypesOneVM(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit) {
        return getRequiredServiceTypesOneVM(virtualMachineSchedulingUnit, new ArrayList<>());
    }
    @NotNull
    public List<ServiceTypeSchedulingUnit> getRequiredServiceTypesOneVM(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, List<ProcessStepSchedulingUnit> additionalProcessSteps) {

        Set<ProcessStepSchedulingUnit> processStepSchedulingUnits = new HashSet<>(virtualMachineSchedulingUnit.getProcessStepSchedulingUnits());
        processStepSchedulingUnits.addAll(additionalProcessSteps);

        Map<ServiceType, List<ServiceTypeSchedulingUnit>> requiredServiceTypeMap = new HashMap<>();
        processStepSchedulingUnits.forEach(processStepSchedulingUnit -> {

            requiredServiceTypeMap.putIfAbsent(processStepSchedulingUnit.getProcessStep().getServiceType(), new ArrayList<>());
            Chromosome.Gene gene = processStepSchedulingUnit.getGene();

            boolean overlapFound = false;
            List<ServiceTypeSchedulingUnit> requiredServiceTypes = requiredServiceTypeMap.get(gene.getProcessStepSchedulingUnit().getProcessStep().getServiceType());
            for (ServiceTypeSchedulingUnit requiredServiceType : requiredServiceTypes) {
                Interval overlap = requiredServiceType.getServiceAvailableTime().overlap(gene.getExecutionInterval());
                if (overlap != null) {
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
                    requiredServiceType.getGenes().add(gene);

                    overlapFound = true;
                    break;
                }
            }

            if (!overlapFound) {
                ServiceTypeSchedulingUnit newServiceTypeSchedulingUnit = new ServiceTypeSchedulingUnit(gene.getProcessStepSchedulingUnit().getProcessStep().getServiceType(), this.containerDeploymentTime, gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit());
                newServiceTypeSchedulingUnit.setServiceAvailableTime(gene.getExecutionInterval());
                newServiceTypeSchedulingUnit.addProcessStep(gene);

                requiredServiceTypeMap.get(gene.getProcessStepSchedulingUnit().getProcessStep().getServiceType()).add(newServiceTypeSchedulingUnit);
            }
        });

        List<ServiceTypeSchedulingUnit> returnList = new ArrayList<>();
        requiredServiceTypeMap.forEach((k, v) -> returnList.addAll(v));

        returnList.forEach(unit -> {
            try {
                unit.setContainer(getContainer(unit.getServiceType(), unit.getGenes().size()));
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
