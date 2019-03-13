package at.ac.tuwien.infosys.viepepc.engine.manager.rest.impl;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.impl.Watchdog;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.engine.manager.rest.WorkflowRestService;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.library.registry.ServiceRegistryReader;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.service.ServiceTypeNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.core.Reasoning;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Created by philippwaibel on 17/05/16. edited by Gerta Sheganaku
 */
@RestController
@Slf4j
public class WorkflowRestServiceImpl implements WorkflowRestService {

    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private Reasoning reasoning;
    @Autowired
    private ServiceRegistryReader serviceRegistryReader;
    @Autowired
    private Watchdog watchdog;

    private int totalProcessStepCounter = 0;

    private static Object SYNC_OBJECT = new Object();

    @RequestMapping(value = "/evaluation/killvm", method = RequestMethod.GET)
    public void killRandomVM()  {
        watchdog.setKillFirstVM(true);
    }

    @RequestMapping(value = "/stop", method = RequestMethod.GET, consumes = MediaType.APPLICATION_XML_VALUE)
    public void stopExecution()  {
        reasoning.stop();
    }

    @RequestMapping(value = "/", method = RequestMethod.GET, consumes = MediaType.APPLICATION_XML_VALUE)
    public List<WorkflowElement> getWorkflows() throws Exception {
        return cacheWorkflowService.getAllWorkflowElements();
    }

    @Override
    @RequestMapping(value = "/addWorkflowRequest", method = RequestMethod.POST, consumes = MediaType.APPLICATION_XML_VALUE)
    public void addWorkflow(@RequestBody WorkflowElement workflowElement) throws ServiceTypeNotFoundException {
        DateTime date = DateTime.now();
        log.info("Recieved 1 new WorkflowElement");
        workflowElement.setArrivedAt(date);
        preProcess(workflowElement);
        log.info("add new WorkflowElement: " + workflowElement.toString());
        cacheWorkflowService.addWorkflowInstance(workflowElement);
        log.info("Done: Add new WorkflowElement: " + workflowElement.toString());
        reasoning.setNextOptimizeTimeNow();

        log.debug("Total amount of process steps to execute=" + totalProcessStepCounter);
    }

    @Override
    @RequestMapping(value = "/addWorkflowRequests", method = RequestMethod.POST, consumes = MediaType.APPLICATION_XML_VALUE)
    public void addWorkflow(@RequestBody WorkflowElements workflowElement) throws ServiceTypeNotFoundException {

        synchronized (SYNC_OBJECT) {

            try {
                DateTime date = DateTime.now();
                log.info("Recieved new WorkflowElements: " + workflowElement.getWorkflowElements().size());
                for (WorkflowElement element : workflowElement.getWorkflowElements()) {
                    element.setArrivedAt(date);
                    preProcess(element);
                    log.debug("add new WorkflowElement: " + element.toString());
                    cacheWorkflowService.addWorkflowInstance(element);
                    log.debug("Done: Add new WorkflowElement: " + element.toString());
                }
                reasoning.setNextOptimizeTimeNow();
            } catch (Exception ex) {
                log.error("EXCEPTION", ex);
            }
        }

        log.debug("Total amount of process steps to execute=" + totalProcessStepCounter);
    }

    private void preProcess(Element parent) throws ServiceTypeNotFoundException {
        if (parent == null) {
            return;
        }
        List<Element> elements = parent.getElements();
        if (elements == null || elements.size() == 0) {
            return;
        }
        for (Element element : elements) {
            element.setParent(parent);
            if (element instanceof XORConstruct) {
                XORConstruct element2 = (XORConstruct) element;
                int size = element2.getElements().size();
//                Random random = new Random();
//                int i = random.nextInt(size);
                int i = 0;
                Element subelement1 = element2.getElements().get(i);
                setAllOthersToNotExecuted(element2.getElements(), subelement1);
                element.getParent().setNextXOR(subelement1);
            } else if (element instanceof LoopConstruct) {
                ((LoopConstruct) element).setNumberOfIterationsInWorstCase(3);
//                Random random = new Random();
//                int i = random.nextInt(((LoopConstruct) element).getNumberOfIterationsInWorstCase()) + 1;
                int i = ((LoopConstruct) element).getNumberOfIterationsInWorstCase();
                ((LoopConstruct) element).setNumberOfIterationsToBeExecuted(i);
                // ((LoopConstruct) element).setIterations(1);
            }  //TODO: CHECK just ignore loops?
            else if (element instanceof ProcessStep) {

                if(((ProcessStep) element).hasBeenExecuted()) {
                    totalProcessStepCounter = totalProcessStepCounter + 1;
                }

                ProcessStep processStep = (ProcessStep) element;
                if (processStep.getServiceType().getServiceTypeResources() == null) {
                    processStep.setServiceType(serviceRegistryReader.findServiceType(processStep.getServiceType().getName()));
                }
            }
            preProcess(element);
        }
    }

    private void setAllOthersToNotExecuted(List<Element> elements, Element ignore) {
        if (elements == null) {
            return;
        }
        for (Element element : elements) {
            if (!element.getName().equals(ignore.getName())) {
                if (element instanceof ProcessStep) {
                    ((ProcessStep) element).setHasToBeExecuted(false);
                } else {
                    setAllOthersToNotExecuted(element.getElements(), ignore);
                }
            }
        }
    }

}
