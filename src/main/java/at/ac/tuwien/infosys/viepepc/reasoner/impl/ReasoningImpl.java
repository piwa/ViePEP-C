package at.ac.tuwien.infosys.viepepc.reasoner.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.WorkflowDaoService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import at.ac.tuwien.infosys.viepepc.reasoner.ProcessOptimizationResults;
import at.ac.tuwien.infosys.viepepc.reasoner.Reasoning;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by philippwaibel on 17/05/16. edited by Gerta Sheganaku
 */
@Component
@Scope("singleton")
@Slf4j
public class ReasoningImpl implements Reasoning {

    @Autowired
    private ProcessOptimizationResults processOptimizationResults;
    @Autowired
    private ProcessInstancePlacementProblem resourcePredictionService;
    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private WorkflowDaoService workflowDaoService;

    private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private boolean run = true;

    private AtomicLong lastTerminateCheckTime = new AtomicLong(0);
    private AtomicLong nextOptimizeTime = new AtomicLong(0);

    private static final long POLL_INTERVAL_MILLISECONDS = 1000;
    private static final long TERMINATE_CHECK_INTERVAL_MILLISECONDS = 10000;
	public static final long MIN_TAU_T_DIFFERENCE_MS = 10 * 1000;
	private static final long RETRY_TIMEOUT_MILLIS = 10 * 1000;


    @Async
    public Future<Boolean> runReasoning(Date date, boolean autoTerminate) throws InterruptedException {

        resourcePredictionService.initializeParameters();
        run = true;

        Date emptyTime = null;

        while (run) {
            synchronized (this) {
                try {
                	long now = System.currentTimeMillis();

                	if (now - lastTerminateCheckTime.get() > TERMINATE_CHECK_INTERVAL_MILLISECONDS) {
                		lastTerminateCheckTime.set(now);

                		List<WorkflowElement> workflows = cacheWorkflowService.getRunningWorkflowInstances();
                		log.info("Running workflow instances (" + workflows.size() + " running)");// WAS EMPTY? : " + workflows.isEmpty());

                        if(workflows.isEmpty()) {
                            if(emptyTime == null) {
                            	emptyTime = new Date();
                            }
                            log.info("Time first empty: " + emptyTime);
                        }
                        else {
                            emptyTime = null;
                        }
                        if (emptyTime != null && ((new Date()).getTime() - emptyTime.getTime()) >= (60 * 1000 * 1)) {
                        	if (autoTerminate) {
                        		run = false;
                        	}
                        }
                	}

                	if (now >= nextOptimizeTime.get()) {
                		 long difference = performOptimisation();
                		 nextOptimizeTime.set(now + difference);
                	}
                   
                	Thread.sleep(POLL_INTERVAL_MILLISECONDS);

                } catch (ProblemNotSolvedException ex) {
                    log.error("An exception occurred, could not solve the problem", ex);
           		 	nextOptimizeTime.set(System.currentTimeMillis() + RETRY_TIMEOUT_MILLIS);
                } catch (Exception ex) {
                    log.error("An unknown exception occurred. Terminating.", ex);
                    run = false;
                }
            }
        }

        waitUntilAllProcessDone();

        finishingTasks();

        for(WorkflowElement workflowElement : cacheWorkflowService.getAllWorkflowElements()) {
            workflowDaoService.finishWorkflow(workflowElement);
        }

        return new AsyncResult<>(true);
    }

    private void finishingTasks() {
        List<WorkflowElement> workflows = cacheWorkflowService.getAllWorkflowElements();
        int delayed = 0;
        for (WorkflowElement workflow : workflows) {
            log.info("workflow: " + workflow.getName() + " Deadline: " + formatter.format(new Date(workflow.getDeadline())));

            ProcessStep lastExecutedElement = workflow.getLastExecutedElement();
            if (lastExecutedElement != null) {
                Date finishedAt = lastExecutedElement.getFinishedAt();
                workflow.setFinishedAt(finishedAt);
                boolean ok = workflow.getDeadline() >= finishedAt.getTime();
                long delay = finishedAt.getTime() - workflow.getDeadline();
                String message = " LastDone: " + formatter.format(finishedAt);
                if (ok) {
                    log.info(message + " : was ok");
                } else {
                    log.info(message + " : delayed in seconds: " + delay / 1000);
                    delayed++;
                }
                cacheWorkflowService.deleteRunningWorkflowInstance(workflow);
            } else {
                log.info(" LastDone: not yet finished");
            }
        }
        log.info(String.format("From %s workflows, %s where delayed", workflows.size(), delayed));
    }

    private void waitUntilAllProcessDone() {
        int times = 0;
        int size = placementHelper.getRunningSteps().size();
        while (size != 0 && times <= 5) {
            log.info("there are still steps running waiting 1 minute: steps running: " + size);
            try {
                Thread.sleep(60000);//
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            size = placementHelper.getRunningSteps().size();
            times++;
        }
    }

    public long performOptimisation() throws Exception {

        placementHelper.setFinishedWorkflows();

        Date tau_t_0 = new Date();

        terminateVms(tau_t_0);

        log.info("--------- tau_t_0 : " + tau_t_0 + " ------------------------");
        log.info("--------- tau_t_0.time : " + tau_t_0.getTime() + " ------------------------");
        OptimizationResult optimize = resourcePredictionService.optimize(tau_t_0);

        if (optimize == null) {
            throw new ProblemNotSolvedException("Could not solve the Problem");
        }

//        log.info("Objective: " + optimize.getObjective());
        long tau_t_1 = optimize.getTauT1();
//        long tau_t_1 = optimize.get("tau_t_1").longValue() * 1000;//VERY IMPORTANT,
        log.info("tau_t_1 was calculted as: "+ new Date(tau_t_1) );
        
        Future<Boolean> processed = processOptimizationResults.processResults(optimize, tau_t_0);
        processed.get();
        
        long difference = tau_t_1 - new Date().getTime();
        if (difference < 0 || difference > 60*60*1000) {
            difference = MIN_TAU_T_DIFFERENCE_MS;
        }
        log.info("--------- sleep for: " + difference / 1000 + " seconds -----------");
        log.info("--------- next iteration: " + new Date(tau_t_1) + " -----------");
        
        
        return difference;
    }

    private void terminateVms(Date tau_t_0) {
        for(VirtualMachine vm : cacheVirtualMachineService.getStartedVMs()) {
            long timeUntilTermination = placementHelper.getRemainingLeasingDuration(tau_t_0, vm);
            if(timeUntilTermination < MIN_TAU_T_DIFFERENCE_MS) {
                if(vm.getDeployedContainers().size() > 0) {
                    log.info("Extend leasing of VM: " + vm.toString());
                    vm.setToBeTerminatedAt(new Date(vm.getToBeTerminatedAt().getTime() + vm.getVmType().getLeasingDuration()));
                }
                else {
                    placementHelper.terminateVM(vm);
                }
            }
        }
    }

    public void stop() {
        this.run = false;
    }

    public void setNextOptimizeTimeNow() {
    	setNextOptimizeTimeAfter(0);
    }

    public void setNextOptimizeTimeAfter(long millis) {
		nextOptimizeTime.set(System.currentTimeMillis() + millis);
	}
}
