package at.ac.tuwien.infosys.viepepc.serviceexecutor;

import at.ac.tuwien.infosys.viepep.database.entities.ProcessStep;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Created by philippwaibel on 18/05/16. edited by Gerta Sheganaku
 */
@Slf4j
@Component
public class ServiceExecutionController{

    @Autowired
    private LeaseVMAndStartExecution leaseVMAndStartExecution;

    @Async("serviceProcessExecuter")
    public void startInvocation(List<ProcessStep> processSteps) {

        final Map<VirtualMachine, List<ProcessStep>> vmProcessStepsMap = new HashMap<>();
        for (final ProcessStep processStep : processSteps) {

//            processStep.setStartDate(new Date());
            VirtualMachine scheduledAt = processStep.getScheduledAtVM();
            List<ProcessStep> processStepsOnVm = new ArrayList<>();
            if (vmProcessStepsMap.containsKey(scheduledAt)) {
                processStepsOnVm.addAll(vmProcessStepsMap.get(scheduledAt));
            }
            processStepsOnVm.add(processStep);
            vmProcessStepsMap.put(scheduledAt, processStepsOnVm);
        }

        for (final VirtualMachine virtualMachine : vmProcessStepsMap.keySet()) {

            final List<ProcessStep> processSteps1 = vmProcessStepsMap.get(virtualMachine);
            if (!virtualMachine.isLeased()) {
                virtualMachine.setLeased(true);
                virtualMachine.setStartedAt(new Date());

                leaseVMAndStartExecution.leaseVMAndStartExecution(virtualMachine, processSteps1);

            } else {
                leaseVMAndStartExecution.startExecutions(vmProcessStepsMap.get(virtualMachine), virtualMachine);
            }
        }
    }
    
    @Async("serviceProcessExecuter")
    public void startInvocation(List<ProcessStep> processSteps, List<DockerContainer> containers) {

    	final Map<VirtualMachine, Map<DockerContainer, List<ProcessStep>>> vmContainerProcessStepMap = new HashMap<>();
    	final Map<DockerContainer, List<ProcessStep>> containerProcessStepsMap = new HashMap<>();

        for (final ProcessStep processStep : processSteps) {
            processStep.setStartDate(new Date());
            DockerContainer scheduledAt = processStep.getScheduledAtContainer();
            if (!containerProcessStepsMap.containsKey(scheduledAt)) {
            	containerProcessStepsMap.put(scheduledAt, new ArrayList<>());
            }
            containerProcessStepsMap.get(scheduledAt).add(processStep);
        }
        
        for (final DockerContainer container : containerProcessStepsMap.keySet()) {
            
            VirtualMachine scheduledAt = container.getVirtualMachine();
            if(scheduledAt == null) {
            	log.error("SCHEDULED AT (VM) NULL .  NO GOOD for container "+container);
            }
            if(!vmContainerProcessStepMap.containsKey(scheduledAt)) {
            	vmContainerProcessStepMap.put(scheduledAt, new HashMap<DockerContainer, List<ProcessStep>>());
            }
            vmContainerProcessStepMap.get(scheduledAt).put(container, containerProcessStepsMap.get(container));
        }

        for (final VirtualMachine virtualMachine : vmContainerProcessStepMap.keySet()) {
            final Map<DockerContainer, List<ProcessStep>> containerProcessSteps = vmContainerProcessStepMap.get(virtualMachine);
            try {
                if (!virtualMachine.isLeased()) {
                    virtualMachine.setLeased(true);
                    virtualMachine.setStartedAt(new Date());
                    leaseVMAndStartExecution.leaseVMAndStartExecution(virtualMachine, containerProcessSteps);

                } else {
                    leaseVMAndStartExecution.startExecutions(vmContainerProcessStepMap.get(virtualMachine), virtualMachine);
                }
			} catch (Exception e) {
				log.error("Unable start invocation: " + e);
			}
        }
    }
}
