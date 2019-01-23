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


    public void checkContainerSchedulingUnits(Chromosome chromosome, String position) {

        List<ProcessStepSchedulingUnit> processStepSchedulingUnits = chromosome.getFlattenChromosome().stream().map(Chromosome.Gene::getProcessStepSchedulingUnit).collect(Collectors.toList());
        for (ProcessStepSchedulingUnit processStepSchedulingUnit : processStepSchedulingUnits) {
            VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = processStepSchedulingUnit.getVirtualMachineSchedulingUnit();
            if(!virtualMachineSchedulingUnit.getProcessStepSchedulingUnits().contains(processStepSchedulingUnit)) {
                log.error("A ProcessStep is defined for a VM but the VM does not contain it! (at="+ position + "); processStepSchedulingUnit=" + processStepSchedulingUnit + ", " + virtualMachineSchedulingUnit);
            }
        }

        if (performChecks) {

            for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
                if (gene.getProcessStepSchedulingUnit() == null) {
                    log.error("processStepSchedulingUnit is null (at=" + position + ") gene=" + gene);
                } else if (gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit() == null) {
                    log.error("getVirtualMachineSchedulingUnit is null (at=" + position + ") gene=" + gene);
                } else {
                    for (ProcessStepSchedulingUnit scheduledProcessStep : gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit().getProcessStepSchedulingUnits()) {
                        if (scheduledProcessStep == null) {
                            log.error("one element of ProcessSteps on the VM is null (at=" + position + ") gene=" + gene);
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
                    log.error("A container is on a VM but not used by a Gene (at=" + position + ")");
                }
            }

            for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
                if (gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit().getProcessStepSchedulingUnits().size() == 0) {
                    log.error("No ProcessStep on VM (at=" + position + ")");
                }
            }

            for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
                ProcessStepSchedulingUnit processStepSchedulingUnit = gene.getProcessStepSchedulingUnit();
                Set<ProcessStepSchedulingUnit> processStepSchedulingUnitsFromVM = processStepSchedulingUnit.getVirtualMachineSchedulingUnit().getProcessStepSchedulingUnits();
                if (!processStepSchedulingUnitsFromVM.contains(processStepSchedulingUnit)) {
                    log.error("ProcessStep is not on VM (at=" + position + ")");
                }
            }

            Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = chromosome.getFlattenChromosome().stream().map(gene -> gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit()).collect(Collectors.toSet());
            for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : virtualMachineSchedulingUnits) {
                if (!vmSelectionHelper.checkIfVirtualMachineIsBigEnough(virtualMachineSchedulingUnit)) {
                    log.error("not enough space (at=" + position + ") on VM=" + virtualMachineSchedulingUnit);
                }
            }
        }
    }

    public Container getContainer(ServiceType serviceType, int amount) throws ContainerImageNotFoundException {

        double cpuLoad = serviceType.getServiceTypeResources().getCpuLoad() + serviceType.getServiceTypeResources().getCpuLoad() * (amount - 1) * 2 / 3;
        double ram = serviceType.getServiceTypeResources().getMemory() + serviceType.getServiceTypeResources().getMemory() * (amount - 1) * 2 / 3;

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

    /**
     * returns a list of service types (can be mapped to containers) that have to be deployed to execute a list of process steps.
     *
     * @param chromosome
     */
    public List<ServiceTypeSchedulingUnit> getRequiredServiceTypes(Chromosome chromosome) {

        List<ServiceTypeSchedulingUnit> returnList = new ArrayList<>();
        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = chromosome.getFlattenChromosome().stream().map(gene -> gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit()).filter(Objects::nonNull).collect(Collectors.toSet());
        List<ProcessStepSchedulingUnit> testPSSize = new ArrayList<>();
        virtualMachineSchedulingUnits.stream().map(VirtualMachineSchedulingUnit::getProcessStepSchedulingUnits).forEach(testPSSize::addAll);

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

        Set<ProcessStepSchedulingUnit> processStepSchedulingUnits = new HashSet<>(virtualMachineSchedulingUnit.getProcessStepSchedulingUnits());
        processStepSchedulingUnits.addAll(additionalProcessSteps);

        Map<ServiceType, List<ServiceTypeSchedulingUnit>> requiredServiceTypeMap = new HashMap<>();
        for (ProcessStepSchedulingUnit processStepSchedulingUnit : processStepSchedulingUnits) {
            Chromosome.Gene gene = processStepSchedulingUnit.getGene();
            requiredServiceTypeMap.putIfAbsent(processStepSchedulingUnit.getProcessStep().getServiceType(), new ArrayList<>());

            boolean overlapFound = false;
            List<ServiceTypeSchedulingUnit> requiredServiceTypes = requiredServiceTypeMap.get(gene.getProcessStepSchedulingUnit().getProcessStep().getServiceType());
            for (ServiceTypeSchedulingUnit requiredServiceType : requiredServiceTypes) {
                Interval overlap = requiredServiceType.getServiceAvailableTime().overlap(gene.getExecutionInterval());
                if (overlap != null && requiredServiceType.isFixed() == gene.isFixed()) {
                    if (!gene.isFixed() || requiredServiceType.getContainer().getInternId().equals(gene.getProcessStepSchedulingUnit().getProcessStep().getContainer().getInternId())) {

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
            }

            if (!overlapFound) {
                ServiceTypeSchedulingUnit newServiceTypeSchedulingUnit = new ServiceTypeSchedulingUnit(processStepSchedulingUnit.getProcessStep().getServiceType(), this.containerDeploymentTime, gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit(), gene.isFixed());
                newServiceTypeSchedulingUnit.setServiceAvailableTime(gene.getExecutionInterval());
                newServiceTypeSchedulingUnit.addProcessStep(gene);
                if (newServiceTypeSchedulingUnit.isFixed()) {
                    newServiceTypeSchedulingUnit.setContainer(gene.getProcessStepSchedulingUnit().getProcessStep().getContainer());
                }

                requiredServiceTypeMap.get(processStepSchedulingUnit.getProcessStep().getServiceType()).add(newServiceTypeSchedulingUnit);
            }
        }

        List<ServiceTypeSchedulingUnit> returnList = new ArrayList<>();
        requiredServiceTypeMap.forEach((k, v) -> returnList.addAll(v));

        returnList.stream().filter(unit -> !unit.isFixed()).forEach(unit -> {
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
