package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.factory;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ProcessStepSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DeadlineAwareFactoryInitializer {

    @Value("${virtual.machine.default.deploy.time}")
    private long virtualMachineDeploymentTime;
    @Value("${container.default.deploy.time}")
    private long containerDeploymentTime;

    @Getter
    @Setter
    private Chromosome.Gene firstGene;
    @Getter
    @Setter
    private Chromosome.Gene lastGene;

    private DateTime optimizationEndTime;
    private Map<VirtualMachineInstance, VirtualMachineSchedulingUnit> virtualMachineSchedulingUnitMap = new HashMap<>();

    public void initialize(DateTime optimizationEndTime) {
        this.optimizationEndTime = optimizationEndTime;

        this.virtualMachineSchedulingUnitMap = new HashMap<>();
        this.firstGene = null;
        this.lastGene = null;
    }

    public List<Chromosome.Gene> createStartChromosome(Element currentElement) {

        List<Chromosome.Gene> subChromosome = new ArrayList<>();
        createStartChromosomeRec(currentElement, optimizationEndTime, subChromosome);

        return subChromosome;
    }

    private DateTime createStartChromosomeRec(Element currentElement, DateTime startTime, List<Chromosome.Gene> chromosome) {
        if (currentElement instanceof ProcessStep) {

            ProcessStep processStep = (ProcessStep) currentElement;

            boolean isRunning = false;
            boolean isDone = false;

            if (processStep.getScheduledStartDate() != null) {
                if (processStep.getStartDate() != null && processStep.getFinishedAt() == null &&
                        processStep.getScheduledStartDate().isBefore(this.optimizationEndTime) && processStep.getScheduledStartDate().plus(processStep.getExecutionTime()).isAfter(this.optimizationEndTime)) {
                    isRunning = true;
//                } else if ((processStep.getStartDate() != null && processStep.getFinishedAt() != null) ||
//                           (processStep.getStartDate() != null && processStep.getFinishedAt() == null && processStep.getScheduledStartDate().plus(processStep.getExecutionTime()).isBefore(this.optimizationEndTime))) {
//                    isDone = true;
//                } else if (processStep.getStartDate() == null && processStep.getFinishedAt() == null && processStep.getScheduledStartDate().plus(processStep.getExecutionTime()).isBefore(this.optimizationEndTime)) {
//                    isDone = true;
                } else if ((processStep.getStartDate() != null && processStep.getFinishedAt() != null) || processStep.getScheduledStartDate().plus(processStep.getExecutionTime()).isBefore(this.optimizationEndTime)) {
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

        if (processStep.getContainer() != null) {
            Container container = processStep.getContainer();
            ContainerStatus containerStatus = processStep.getContainer().getContainerStatus();
            DateTime containerDeployStartTime = container.getScheduledCloudResourceUsage().getStart();

            VirtualMachineInstance virtualMachineInstance = container.getVirtualMachineInstance();
            VirtualMachineStatus virtualMachineStatus = virtualMachineInstance.getVirtualMachineStatus();
            DateTime vmDeployStartTime = virtualMachineInstance.getScheduledCloudResourceUsage().getStart();

            if (containerStatus.equals(ContainerStatus.DEPLOYING) || containerStatus.equals(ContainerStatus.DEPLOYED) || containerDeployStartTime.isBefore(this.optimizationEndTime)) {
                inProcessStepExecutionPrepartation = true;
            } else if ((virtualMachineStatus.equals(VirtualMachineStatus.DEPLOYING) || virtualMachineStatus.equals(VirtualMachineStatus.DEPLOYED) || vmDeployStartTime.isBefore(this.optimizationEndTime)) &&
                    virtualMachineInstance.getScheduledAvailableInterval().getStart().plus(10000).isAfter(container.getScheduledCloudResourceUsage().getStart())) {
                inProcessStepExecutionPrepartation = true;
            }
        }

        if (processStep.isHasToBeExecuted() && !isRunning && !inProcessStepExecutionPrepartation) {

            if (processStep.getScheduledStartDate() != null) {
                startTime = processStep.getScheduledStartDate();
            } else {
                startTime = startTime.plus(containerDeploymentTime);
            }

            Chromosome.Gene gene = new Chromosome.Gene(getProcessStepSchedulingUnit(processStep, false), startTime, false);
            gene.getProcessStepSchedulingUnit().setGene(gene);
            chromosome.add(gene);

            checkFirstAndLastGene(gene);

            return gene.getExecutionInterval().getEnd();
        } else if (isRunning || inProcessStepExecutionPrepartation) {
            DateTime realStartTime = processStep.getStartDate();
            if (realStartTime == null) {
                realStartTime = processStep.getScheduledStartDate();
            }

            Chromosome.Gene gene = new Chromosome.Gene(getProcessStepSchedulingUnit(processStep, true), realStartTime, true);
            gene.getProcessStepSchedulingUnit().setGene(gene);
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

        ProcessStepSchedulingUnit processStepSchedulingUnit = new ProcessStepSchedulingUnit(processStep);

        if (isFixed) {
            setContainerAndVMSchedulingUnit(processStepSchedulingUnit);
        }

        return processStepSchedulingUnit;
    }

    public void setContainerAndVMSchedulingUnit(ProcessStepSchedulingUnit processStepSchedulingUnit) {
        ProcessStep processStep = processStepSchedulingUnit.getProcessStep();
        Container container = processStep.getContainer();

        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = virtualMachineSchedulingUnitMap.get(container.getVirtualMachineInstance());
        if (virtualMachineSchedulingUnit == null) {
            virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit( true, virtualMachineDeploymentTime, containerDeploymentTime, container.getVirtualMachineInstance());
            virtualMachineSchedulingUnitMap.put(container.getVirtualMachineInstance(), virtualMachineSchedulingUnit);
        }
        processStepSchedulingUnit.setVirtualMachineSchedulingUnit(virtualMachineSchedulingUnit);
        virtualMachineSchedulingUnit.getProcessStepSchedulingUnits().add(processStepSchedulingUnit);
    }
}
