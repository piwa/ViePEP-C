package at.ac.tuwien.infosys.viepepc.serviceexecutor;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
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
    public void startInvocationViaVMs(List<ProcessStep> processSteps) {

        final Map<VirtualMachine, List<ProcessStep>> vmProcessStepsMap = new HashMap<>();
        for (final ProcessStep processStep : processSteps) {

            VirtualMachine scheduledAt = processStep.getScheduledAtVM();
            List<ProcessStep> processStepsOnVm = new ArrayList<>();
            if (vmProcessStepsMap.containsKey(scheduledAt)) {
                processStepsOnVm.addAll(vmProcessStepsMap.get(scheduledAt));
            }
            processStepsOnVm.add(processStep);
            vmProcessStepsMap.put(scheduledAt, processStepsOnVm);
        }

        for (final VirtualMachine virtualMachine : vmProcessStepsMap.keySet()) {

            final List<ProcessStep> processStepsOnVm = vmProcessStepsMap.get(virtualMachine);
            if (!virtualMachine.isLeased()) {
                virtualMachine.setLeased(true);
                virtualMachine.setStartedAt(new Date());

                leaseVMAndStartExecution.leaseVMAndStartExecutionOnVirtualMachine(virtualMachine, processStepsOnVm);

            } else {
                leaseVMAndStartExecution.startExecutionsOnVirtualMachine(vmProcessStepsMap.get(virtualMachine), virtualMachine);
            }
        }
    }
    
    @Async("serviceProcessExecuter")
    public void startInvocationViaContainers(List<ProcessStep> processSteps) {

    	final Map<VirtualMachine, Map<Container, List<ProcessStep>>> vmContainerProcessStepMap = new HashMap<>();
    	final Map<Container, List<ProcessStep>> containerProcessStepsMap = new HashMap<>();

        for (final ProcessStep processStep : processSteps) {
            processStep.setStartDate(new Date());
            Container scheduledAt = processStep.getScheduledAtContainer();
            if (!containerProcessStepsMap.containsKey(scheduledAt)) {
            	containerProcessStepsMap.put(scheduledAt, new ArrayList<>());
            }
            containerProcessStepsMap.get(scheduledAt).add(processStep);
        }
        
        for (final Container container : containerProcessStepsMap.keySet()) {
            
            VirtualMachine scheduledAt = container.getVirtualMachine();
            if(scheduledAt == null) {
            	log.error("No VM set for Container " + container);
            }
            if(!vmContainerProcessStepMap.containsKey(scheduledAt)) {
            	vmContainerProcessStepMap.put(scheduledAt, new HashMap<>());
            }
            vmContainerProcessStepMap.get(scheduledAt).put(container, containerProcessStepsMap.get(container));
        }

        for (final VirtualMachine virtualMachine : vmContainerProcessStepMap.keySet()) {
            final Map<Container, List<ProcessStep>> containerProcessSteps = vmContainerProcessStepMap.get(virtualMachine);
            try {
                if (!virtualMachine.isLeased()) {
                    virtualMachine.setLeased(true);
                    virtualMachine.setStartedAt(new Date());
                    virtualMachine.setToBeTerminatedAt(new Date(virtualMachine.getStartedAt().getTime() + virtualMachine.getVmType().getLeasingDuration()));
                    leaseVMAndStartExecution.leaseVMAndStartExecutionOnContainer(virtualMachine, containerProcessSteps);

                } else {
                    leaseVMAndStartExecution.startExecutionsOnContainer(vmContainerProcessStepMap.get(virtualMachine), virtualMachine);
                }
			} catch (Exception e) {
				log.error("Unable start invocation: " + e);
			}
        }
    }
}
