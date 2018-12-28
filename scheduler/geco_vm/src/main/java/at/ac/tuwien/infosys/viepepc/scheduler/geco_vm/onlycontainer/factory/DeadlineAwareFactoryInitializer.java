package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.factory;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities.ContainerSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities.ProcessStepSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities.VirtualMachineSchedulingUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.*;

@Slf4j
public class DeadlineAwareFactoryInitializer {

    @Getter @Setter private Chromosome.Gene firstGene;
    @Getter @Setter private Chromosome.Gene lastGene;
    private DateTime optimizationEndTime;
    @Setter private final long containerDeploymentTime;
    @Setter private final long virtualMachineDeploymentTime;

    private Map<ServiceType, ServiceType> clonedServiceTypes = new HashMap<>();
    @Getter private Map<VirtualMachine, VirtualMachineSchedulingUnit> virtualMachineSchedulingUnitMap = new HashMap<>();
    @Getter private Map<ProcessStepSchedulingUnit, ContainerSchedulingUnit> fixedContainerSchedulingUnitMap = new HashMap<>();

    public DeadlineAwareFactoryInitializer(DateTime optimizationEndTime, long containerDeploymentTime, long virtualMachineDeploymentTime) {
        this.optimizationEndTime = optimizationEndTime;
        this.containerDeploymentTime = containerDeploymentTime;
        this.virtualMachineDeploymentTime = virtualMachineDeploymentTime;
    }

    public List<Chromosome.Gene> createStartChromosome(Element currentElement) {
        this.firstGene = null;
        this.lastGene = null;
        this.clonedServiceTypes = new HashMap<>();
        this.virtualMachineSchedulingUnitMap = new HashMap<>();
        this.fixedContainerSchedulingUnitMap = new HashMap<>();

        List<Chromosome.Gene> subChromosome = new ArrayList<>();
        createStartChromosomeRec(currentElement, optimizationEndTime, subChromosome);

        return subChromosome;

    }

    private DateTime createStartChromosomeRec(Element currentElement, DateTime startTime, List<Chromosome.Gene> chromosome) {
        if (currentElement instanceof ProcessStep) {

            ProcessStep processStep = (ProcessStep) currentElement;
//            boolean isRunning = processStep.getStartDate() != null && processStep.getFinishedAt() == null;
//            boolean isDone = processStep.getStartDate() != null && processStep.getFinishedAt() != null;
//
//
//            if (isRunning == false && processStep.getScheduledStartDate() != null) {
//                isRunning = processStep.getScheduledStartDate().isBefore(this.optimizationEndTime) && processStep.getScheduledStartDate().plus(processStep.getExecutionTime()).isAfter(this.optimizationEndTime);
//            }
//            if (isDone == false && processStep.getScheduledStartDate() != null) {
//                isDone = processStep.getScheduledStartDate().isBefore(this.optimizationEndTime) && processStep.getScheduledStartDate().plus(processStep.getExecutionTime()).isBefore(this.optimizationEndTime);
//            }

            boolean isRunning = false;
            boolean isDone = false;

            if(processStep.getScheduledStartDate() != null) {
                if (processStep.getStartDate() != null && processStep.getFinishedAt() == null &&
                        processStep.getScheduledStartDate().isBefore(this.optimizationEndTime) && processStep.getScheduledStartDate().plus(processStep.getExecutionTime()).isAfter(this.optimizationEndTime)) {
                    isRunning = true;
                } else if (processStep.getStartDate() != null && processStep.getFinishedAt() != null &&
                        processStep.getScheduledStartDate().isBefore(this.optimizationEndTime) && processStep.getScheduledStartDate().plus(processStep.getExecutionTime()).isBefore(this.optimizationEndTime)) {
                    isDone = true;
                }
            }

            return getStartTimeForProcessStep(processStep, startTime, chromosome, isDone, isRunning);

        } else {
            if (currentElement instanceof WorkflowElement) {
                for (Element element : currentElement.getElements()) {
                    startTime = createStartChromosomeRec(element, startTime, chromosome);
                }
            } else if (currentElement instanceof Sequence) {
                for (Element element1 : currentElement.getElements()) {
                    startTime = createStartChromosomeRec(element1, startTime, chromosome);
                }
            } else if (currentElement instanceof ANDConstruct || currentElement instanceof XORConstruct) {
                DateTime latestEndTime = startTime;
                for (Element element1 : currentElement.getElements()) {
                    DateTime tmpEndTime = createStartChromosomeRec(element1, startTime, chromosome);
                    if (tmpEndTime.isAfter(latestEndTime)) {
                        latestEndTime = tmpEndTime;
                    }
                }
                startTime = latestEndTime;
            } else if (currentElement instanceof LoopConstruct) {

                if ((currentElement.getNumberOfExecutions() < ((LoopConstruct) currentElement).getNumberOfIterationsToBeExecuted())) {
                    for (Element subElement : currentElement.getElements()) {
                        startTime = createStartChromosomeRec(subElement, startTime, chromosome);
                    }
                }


            }
            return startTime;
        }
    }

    private DateTime getStartTimeForProcessStep(ProcessStep processStep, DateTime startTime, List<Chromosome.Gene> chromosome, boolean isDone, boolean isRunning) {

        boolean inProcessStepExecutionPrepartation = false;

        if (isDone) {
            return startTime;
        }

        if(processStep.getContainer() != null) {
            DateTime containerDeployStartTime = processStep.getContainer().getScheduledAvailableInterval().getStart().minus(containerDeploymentTime);
            ContainerStatus containerStatus = processStep.getContainer().getContainerStatus();
//            VirtualMachineStatus virtualMachineStatus = processStep.getContainer().getVirtualMachine().getVirtualMachineStatus();
//            Collection<Interval> virtualMachineDeployStartTime = processStep.getContainer().getVirtualMachine().getScheduledAvailableIntervals().values();
            // TODO at the moment we do not fix a processStep when the VM is booting since a VM can be booted for a lot of containers (e.g. 10). This would mean that
            //  we fix all process steps -> maybe fix only if the VM is booted for this process step. Or maybe if the process step is under the 3 earliest process steps that will be executed on this VM

            if(containerStatus.equals(ContainerStatus.DEPLOYING) || containerStatus.equals(ContainerStatus.DEPLOYED) || containerDeployStartTime.isBefore(this.optimizationEndTime)
//                    || virtualMachineStatus.equals(VirtualMachineStatus.DEPLOYING) || virtualMachineStatus.equals(VirtualMachineStatus.DEPLOYED)
                    ) {
                inProcessStepExecutionPrepartation = true;
            }
        }


//        if (processStep.getContainer() != null && (processStep.getContainer().getContainerStatus().equals(ContainerStatus.DEPLOYING) || processStep.getContainer().getContainerStatus().equals(ContainerStatus.DEPLOYED))) {
//            inProcessStepExecutionPrepartation = true;
//        }

        if (processStep.isHasToBeExecuted() && !isRunning && !inProcessStepExecutionPrepartation) {

            if (processStep.getScheduledStartDate() != null) {
                startTime = processStep.getScheduledStartDate();
            } else {
                startTime = startTime.plus(containerDeploymentTime);
            }

            Chromosome.Gene gene = new Chromosome.Gene(getProcessStepSchedulingUnit(processStep, false), startTime, false);
            chromosome.add(gene);

            checkFirstAndLastGene(gene);

            return gene.getExecutionInterval().getEnd();
        } else if (isRunning || inProcessStepExecutionPrepartation) {
            DateTime realStartTime = processStep.getStartDate();
            if (realStartTime == null) {
                realStartTime = processStep.getScheduledStartDate();
            }

            Chromosome.Gene gene = new Chromosome.Gene(getProcessStepSchedulingUnit(processStep, true), realStartTime, true);
            gene.getProcessStep().getContainerSchedulingUnit().getProcessStepGenes().add(gene);
            chromosome.add(gene);

            checkFirstAndLastGene(gene);

            return gene.getExecutionInterval().getEnd();
        }
        return startTime;
    }

    private void checkFirstAndLastGene(Chromosome.Gene gene) {
        if (firstGene == null || firstGene.getExecutionInterval().getStart().isAfter(gene.getExecutionInterval().getStart())) {
            firstGene = gene;
        }
        if (lastGene == null || lastGene.getExecutionInterval().getEnd().isBefore(gene.getExecutionInterval().getEnd())) {
            lastGene = gene;
        }
    }

    private ProcessStepSchedulingUnit getProcessStepSchedulingUnit(ProcessStep processStep, boolean isFixed) {

        try {
            ServiceType clonedServiceType = clonedServiceTypes.get(processStep.getServiceType());

            if (clonedServiceType == null) {
                clonedServiceType = processStep.getServiceType().clone();
                clonedServiceTypes.put(processStep.getServiceType(), clonedServiceType);
            }

            ProcessStepSchedulingUnit processStepSchedulingUnit = new ProcessStepSchedulingUnit(processStep, clonedServiceType);

            if(isFixed) {
                Container container = processStep.getContainer();
                ContainerSchedulingUnit containerSchedulingUnit = fixedContainerSchedulingUnitMap.get(container);
                if (containerSchedulingUnit == null) {
                    containerSchedulingUnit = new ContainerSchedulingUnit(containerDeploymentTime);
                    containerSchedulingUnit.setContainer(container);
                    fixedContainerSchedulingUnitMap.put(processStepSchedulingUnit, containerSchedulingUnit);
                }
                processStepSchedulingUnit.setContainerSchedulingUnit(containerSchedulingUnit);

                VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = virtualMachineSchedulingUnitMap.get(container.getVirtualMachine());
                if (virtualMachineSchedulingUnit == null) {
                    virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(virtualMachineDeploymentTime);
                    virtualMachineSchedulingUnit.setVirtualMachine(container.getVirtualMachine());
                    virtualMachineSchedulingUnitMap.put(container.getVirtualMachine(), virtualMachineSchedulingUnit);
                }
                virtualMachineSchedulingUnit.getScheduledContainers().add(containerSchedulingUnit);
                containerSchedulingUnit.setScheduledOnVm(virtualMachineSchedulingUnit);
            }

            return processStepSchedulingUnit;
        } catch (CloneNotSupportedException e) {
            log.error("Exception", e);
            return null;
        }
    }
}
