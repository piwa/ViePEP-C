package at.ac.tuwien.infosys.viepepc.actionexecutor;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.DockerControllerService;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.library.entities.Action;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.ServiceExecution;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker.ServiceInvokeException;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StopWatch;

import java.util.concurrent.TimeUnit;

/**
 * Created by philippwaibel on 18/05/16.
 */
@Slf4j
public class OnlyContainerDeploymentController implements Runnable {

    @Autowired
    private ReportDaoService reportDaoService;
    @Autowired
    private ServiceExecution serviceExecution;
    @Autowired
    private DockerControllerService dockerControllerService;

    @Value("${simulate}")
    private boolean simulate;
    @Value("${only.container.deploy.time}")
    private long onlyContainerDeploymentTime = 40000;

    private ProcessStep processStep;
    private Container container;

    public OnlyContainerDeploymentController(ProcessStep processStep) {
        this.processStep = processStep;
        this.container = processStep.getContainer();
    }

    @Override
    public void run() {

        StopWatch stopWatch = new StopWatch();
        stopWatch.start("deploy container");

        boolean success = deployContainer(container);

        if (success) {
            stopWatch.stop();
//            log.debug("Container deploy duration: " + container.toString() + ": " + stopWatch.getTotalTimeMillis());


            if (processStep.getScheduledStartDate() != null && DateTime.now().isBefore(processStep.getScheduledStartDate().minusSeconds(2))) {
                Duration duration = new Duration(DateTime.now(), processStep.getScheduledStartDate().minusSeconds(2));
                try {
                    TimeUnit.MILLISECONDS.sleep(duration.getMillis());
                } catch (InterruptedException e) {
                    log.error("Exception while invoking service. Reset.", e);
                }
            }


            try {
                serviceExecution.startExecution(processStep, container);
            } catch (ServiceInvokeException e) {
                log.error("Exception while invoking service. Reset.", e);
                reset("Service");
            }

        } else {
            reset("Container");
        }

    }


    private boolean deployContainer(Container container) {
        synchronized (container) {
            if (container.getContainerStatus().equals(ContainerStatus.DEPLOYED)) {
                log.debug("Container already running: " + container);
                return true;
            }

            try {
                log.info("Deploy new container: " + container);
                dockerControllerService.startContainer(container);
                ContainerReportingAction report = null;
                if (simulate) {
                    report = new ContainerReportingAction(DateTime.now().plus(onlyContainerDeploymentTime), container.getName(), container.getContainerConfiguration().getName(), null, Action.START);
                } else {
                    report = new ContainerReportingAction(DateTime.now(), container.getName(), container.getContainerConfiguration().getName(), null, Action.START);
                }
                reportDaoService.save(report);
                return true;

            } catch (InterruptedException | DockerException e) {
                log.error("EXCEPTION while deploying Container. Reset execution request.", e);
                return false;
            }
        }
    }


    private void reset(String failureReason) {
        if (container != null) {
            ContainerReportingAction reportContainer = new ContainerReportingAction(DateTime.now(), container.getName(), container.getContainerConfiguration().getName(), null, Action.FAILED, failureReason);
            reportDaoService.save(reportContainer);
            container.shutdownContainer();
        }

        // TODO
//        inMemoryCache.getProcessStepsWaitingForExecution().remove(processStep);
//        inMemoryCache.getProcessStepsWaitingForServiceDone().remove(processStep.getName());
        processStep.reset();
    }

}
