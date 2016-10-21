package at.ac.tuwien.infosys.viepepc.serviceexecutor;

import at.ac.tuwien.infosys.viepepc.database.entities.Action;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachineReportingAction;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by philippwaibel on 18/05/16.
 */
@Component
@Scope("prototype")
@Slf4j
public class LeaseVMAndStartExecution {

    @Autowired
    private ReportDaoService reportDaoService;
//    @Autowired
//    private ViePEPClientService viePEPClientService;
    @Autowired
    private ServiceExecution serviceExecution;
    @Autowired
    private PlacementHelper placementHelper;
//    @Autowired
//    private ViePEPDockerControllerService dockerControllerService;

    @Value("${simulate}")
    private boolean simulate;
    @Value("${use.container}")
    private boolean useDocker;
    @Value("${virtualmachine.startup.time}")
    private long startupTime;
    

    @Async
    public void leaseVMAndStartExecutionOnVirtualMachine(VirtualMachine virtualMachine, List<ProcessStep> processSteps) {

        final StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String address = startVM(virtualMachine);

        VirtualMachineReportingAction report =  new VirtualMachineReportingAction(new Date(), virtualMachine.getName(), Action.START);
        reportDaoService.save(report);

        if (address == null) {
            log.error("VM " + virtualMachine.getName() + " was not started, reset task");
            for(ProcessStep processStep : processSteps) {
                processStep.setStartDate(null);
                processStep.setScheduled(false);
                processStep.setScheduledAtVM(null);
            }
            return;
        } else {
            long time = stopWatch.getTotalTimeMillis();
            stopWatch.stop();
            virtualMachine.setStartupTime(time);
            virtualMachine.setStarted(true);
            virtualMachine.setIpAddress(address);

            startExecutionsOnVirtualMachine(processSteps, virtualMachine);
        }
    }

    @Async
    public void leaseVMAndStartExecutionOnContainer(VirtualMachine virtualMachine, Map<Container, List<ProcessStep>> containerProcessSteps) {
    	final StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String address = startVM(virtualMachine);

        VirtualMachineReportingAction report =  new VirtualMachineReportingAction(new Date(), virtualMachine.getName(), Action.START);
        reportDaoService.save(report);

        if (address == null) {
            log.error("VM " + virtualMachine.getName() + " was not started, reset task");
            for(Container container : containerProcessSteps.keySet()){
            	for(ProcessStep processStep : containerProcessSteps.get(container)) {
            		processStep.setStartDate(null);
            		processStep.setScheduled(false);
            		processStep.setScheduledAtVM(null);
            	}
            	container.shutdownContainer();
            }
            return;
        } else {
            long time = stopWatch.getTotalTimeMillis();
            stopWatch.stop();
            virtualMachine.setStartupTime(time);
            virtualMachine.setStarted(true);
            virtualMachine.setIpAddress(address);

            startExecutionsOnContainer(containerProcessSteps, virtualMachine);

        }
	}

    public void startExecutionsOnVirtualMachine(final List<ProcessStep> processSteps, final VirtualMachine virtualMachine) {
        for (final ProcessStep processStep : processSteps) {
            processStep.setStartDate(new Date());
        	serviceExecution.startExecution(processStep, virtualMachine);

        }
    }

	public void startExecutionsOnContainer(Map<Container, List<ProcessStep>> containerProcessSteps, VirtualMachine virtualMachine) {
		for (final Container container : containerProcessSteps.keySet()) {
			deployContainer(virtualMachine, container);
			for (final ProcessStep processStep : containerProcessSteps.get(container)) {
	            processStep.setStartDate(new Date());
				serviceExecution.startExecution(processStep, container);
			}	
		}
	}

    private String startVM(VirtualMachine virtualMachine){
    	String address = null;
    	if (simulate) {
            address = "128.130.172.211";
            try {
                Thread.sleep(virtualMachine.getStartupTime());
                /* if we are not in Docker mode, additionally sleep some time for deployment of the service */
                if (!useDocker) {
                    Thread.sleep(virtualMachine.getVmType().getDeployTime());
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
//            address = viePEPClientService.startNewVM(virtualMachine.getName(), virtualMachine.getVmType().flavor(), virtualMachine.getServiceType().name(), virtualMachine.getVmType().getLocation());
            log.info("VM up and running with ip: " + address + " vm: " + virtualMachine);
            try {
                Thread.sleep(startupTime); //sleep 15 seconds, since as soon as it is up, it still has to deploy the services
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    	return address;
    }
    
    private void deployContainer(VirtualMachine vm, Container container) {
    	if(container.isRunning()){
    		log.info("Container "+ container + " already running on vm "+ container.getVirtualMachine());
    		return;
    	}
    	
    	if(simulate) {
    		try {
    			if(placementHelper.imageForContainerEverDeployedOnVM(container, vm) == 0){
                    Thread.sleep(container.getDeployTime());
    			}
                Thread.sleep(container.getDeployTime());
            } catch (InterruptedException e) {
                e.printStackTrace();
                
            }
    	} else {
    		log.info("Start Container: " + container + " on VM: " + vm);
/*			try {
				dockerControllerService.startDocker(vm, container);
			} catch (CouldNotStartContainerException e) {
				e.printStackTrace();
			}
*/    	}
    	container.setRunning(true);
    	container.setStartedAt(new Date());
		vm.addContainer(container);

        ContainerReportingAction report =  new ContainerReportingAction(new Date(), container.getName(), vm.getName(), Action.START);
        reportDaoService.save(report);
         
    }

}
