package at.ac.tuwien.infosys.viepepc.scheduler.core.impl;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.WorkflowDaoService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.library.OptimizationTimeHolder;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.core.Reasoning;
import at.ac.tuwien.infosys.viepepc.scheduler.library.*;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by philippwaibel on 17/05/16. edited by Gerta Sheganaku
 */
@Component
@Slf4j
public class ReasoningImpl implements Reasoning {

    @Autowired
    private HandleOptimizationResult handleOptimizationResult;
    @Autowired
    private SchedulerAlgorithm resourcePredictionService;
    @Autowired
    private WorkflowUtilities workflowUtilities;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private WorkflowDaoService workflowDaoService;
    @Autowired
    private PrintRunningInfo printRunningInfo;

    @Value("${reasoner.autoTerminate.wait.time}")
    private int autoTerminateWait;

    private DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

    private boolean run = true;

    private AtomicLong lastTerminateCheckTime = new AtomicLong(0);
    private AtomicLong lastPrintStatusTime = new AtomicLong(0);


    private static final long POLL_INTERVAL_MILLISECONDS = 1000;
    private static final long TERMINATE_CHECK_INTERVAL_MILLISECONDS = 30000;
    private static final long PRINT_STATUS_INTERVAL_MILLISECONDS = 60000;

    @Value("${optimization.interval.ms}")
    private int minTauTDifference;
    private boolean printRunningInformation = true;

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

                        StringBuilder builder = new StringBuilder();
                        workflows.forEach(workflow -> builder.append(workflow.getName()).append(","));
                        log.info("Running workflow instances (" + workflows.size() + " running): " + builder.toString());// WAS EMPTY? : " + workflows.isEmpty());

                        if (workflows.isEmpty()) {
                            if (emptyTime == null) {
                                emptyTime = new Date();
                            }
                            log.info("Time first empty: " + emptyTime);
                        } else {
                            emptyTime = null;
                        }
                        if (emptyTime != null && ((new Date()).getTime() - emptyTime.getTime()) >= (60 * 1000 * autoTerminateWait)) {
                            if (autoTerminate) {
                                run = false;
                            }
                        }
                    }

                    if (now - lastPrintStatusTime.get() > PRINT_STATUS_INTERVAL_MILLISECONDS) {
                        lastPrintStatusTime.set(now);
                        if (printRunningInformation) {
                            printRunningInfo.printRunningInformation();
                        }
                    }

                    if (now >= OptimizationTimeHolder.nextOptimizeTime.get()) {
                        long difference = performOptimisation();
                        OptimizationTimeHolder.nextOptimizeTime.set(DateTime.now().getMillis() + difference);
                    }

                    Thread.sleep(POLL_INTERVAL_MILLISECONDS);

                } catch (ProblemNotSolvedException ex) {
                    log.error("An exception occurred, could not solve the problem", ex);
                    OptimizationTimeHolder.nextOptimizeTime.set(System.currentTimeMillis() + RETRY_TIMEOUT_MILLIS);
                } catch (Exception ex) {
                    log.error("An unknown exception occurred. Terminating.", ex);
                    run = false;
                }
            }
        }

        waitUntilAllProcessesDone();

        finishingTasks();

        for (WorkflowElement workflowElement : cacheWorkflowService.getAllWorkflowElements()) {
            workflowDaoService.finishWorkflow(workflowElement);
        }

        return new AsyncResult<>(true);
    }

    private void finishingTasks() {
        List<WorkflowElement> workflows = cacheWorkflowService.getAllWorkflowElements();
        int delayed = 0;
        for (WorkflowElement workflow : workflows) {
            log.info("workflow: " + workflow.getName() + " Deadline: " + dtfOut.print(new DateTime(workflow.getDeadline())));

            ProcessStep lastExecutedElement = workflow.getLastExecutedElement();
            if (lastExecutedElement != null) {
                DateTime finishedAt = lastExecutedElement.getFinishedAt();
                workflow.setFinishedAt(finishedAt);
                long delay = (finishedAt.getMillis() - workflow.getDeadline()) / 1000;
                boolean ok = delay < 0;
                String message = " LastDone: " + dtfOut.print(finishedAt);
                if (ok) {
                    log.info(message + " : was ok");
                } else {
                    log.info(message + " : delayed in seconds: " + delay);
                    delayed++;
                }
                cacheWorkflowService.deleteRunningWorkflowInstance(workflow);
            } else {
                log.info(" LastDone: not yet finished");
            }
        }
        log.info(String.format("From %s workflows, %s where delayed", workflows.size(), delayed));
    }

    private void waitUntilAllProcessesDone() {
        int times = 0;
        int size = workflowUtilities.getRunningSteps().size();
        while (size != 0 && times <= 5) {
            log.info("there are still steps running waiting 1 minute: steps running: " + size);
            try {
                Thread.sleep(60000);//
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            size = workflowUtilities.getRunningSteps().size();
            times++;
        }
    }

    private long performOptimisation() throws Exception {

        workflowUtilities.setFinishedWorkflows();

        DateTime tau_t_0 = new DateTime();

        log.info("---------------- tau_t_0 : " + tau_t_0 + " -----------------");
        log.info("-------------- tau_t_0.time : " + tau_t_0.toString() + " --------------");

        OptimizationResult optimize = resourcePredictionService.optimize(tau_t_0);

        if (optimize == null) {
            throw new ProblemNotSolvedException("Could not solve the Problem");
        }

        handleOptimizationResult.processResults(optimize, tau_t_0);

        long difference = minTauTDifference;
        log.info("------------------------- sleep for: " + difference / 1000 + " seconds --------------------------");
        log.info("------------- next iteration: " + DateTime.now().plus(difference) + " --------------");

        return difference;
    }


    public void stop() {
        this.run = false;
    }

    public void setNextOptimizeTimeNow() {
        setNextOptimizeTimeAfter(0);
    }

    public void setNextOptimizeTimeAfter(long millis) {
        OptimizationTimeHolder.nextOptimizeTime.set(System.currentTimeMillis() + millis);
    }
}
