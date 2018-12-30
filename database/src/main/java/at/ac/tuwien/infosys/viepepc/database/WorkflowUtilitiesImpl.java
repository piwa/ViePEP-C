package at.ac.tuwien.infosys.viepepc.database;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Created by philippwaibel on 18/05/16. edited by Gerta Sheganaku
 */
@Component
@Slf4j
public class WorkflowUtilitiesImpl implements WorkflowUtilities {

    @Autowired
    private CacheWorkflowService cacheWorkflowService;

    private Map<Element, Boolean> andParentHasRunningChild = new HashMap<>();

    @Override
    public void setFinishedWorkflows() {
        List<WorkflowElement> nextWorkflows = cacheWorkflowService.getRunningWorkflowInstances();

        for (WorkflowElement workflow : nextWorkflows) {

            List<Element> flattenWorkflowList = getFlattenWorkflow(new ArrayList<>(), workflow);

            boolean workflowDone = true;
            DateTime finishedDate = new DateTime(0);
            for (Element element : flattenWorkflowList) {

                if (element instanceof ProcessStep && element.isLastElement()) {
                    if (((ProcessStep) element).isHasToBeExecuted()) {
                        if (element.getFinishedAt() == null) {
                            workflowDone = false;
                            break;
                        } else {
                            if (element.getFinishedAt().isAfter(finishedDate)) {
                                finishedDate = element.getFinishedAt();
                            }
                        }
                    }
                }
            }

            if (workflowDone) {
                for (Element element : flattenWorkflowList) {
                    if (element.getFinishedAt() != null) {
                        element.setFinishedAt(finishedDate);
                    }
                }
                workflow.setFinishedAt(finishedDate);
                cacheWorkflowService.deleteRunningWorkflowInstance(workflow);
            }

        }

    }

    @Override
    public List<ProcessStep> getNotStartedUnfinishedSteps() {

        List<ProcessStep> processSteps = new ArrayList<>();
        for (WorkflowElement workflowElement : cacheWorkflowService.getRunningWorkflowInstances()) {//.getAllWorkflowElements()) {
            List<Element> flattenWorkflowList = getFlattenWorkflow(new ArrayList<Element>(), workflowElement);
            for (Element element : flattenWorkflowList) {
                if (element instanceof ProcessStep && element.getFinishedAt() == null) {
                    if (((ProcessStep) element).getStartDate() == null && ((ProcessStep) element).getScheduledStartDate() == null) {
                        processSteps.add((ProcessStep) element);
                    }
                }
            }
        }

        return processSteps;
    }

    @Override
    public List<Element> getFlattenWorkflow(List<Element> flattenWorkflowList, Element parentElement) {
        if (parentElement.getElements() != null) {
            for (Element element : parentElement.getElements()) {
                flattenWorkflowList.add(element);
                flattenWorkflowList = getFlattenWorkflow(flattenWorkflowList, element);
            }
        }
        return flattenWorkflowList;
    }

    @Override
    public List<ProcessStep> getNextSteps(String workflowInstanceId) {

        List<WorkflowElement> nextWorkflows = cacheWorkflowService.getRunningWorkflowInstances();

        for (Element workflow : nextWorkflows) {
            if (workflow.getName().equals(workflowInstanceId)) {
//                andParentHasRunningChild = new HashMap<>();
                List<ProcessStep> nextStepElements = new ArrayList<>();
                nextStepElements.addAll(getNextSteps(workflow, null));
                return nextStepElements;
            }
        }
        return new ArrayList<>();
    }

    @Override
    public List<ProcessStep> getRunningProcessSteps(String workflowInstanceId) {
        WorkflowElement workflowInstance = cacheWorkflowService.getWorkflowById(workflowInstanceId);
        if (workflowInstance != null) {
            List<Element> workflowElement = new ArrayList<>();
            workflowElement.add(workflowInstance);
            return getRunningProcessSteps(workflowElement);
        }

        return new ArrayList<>();
    }

    @Override
    public List<Element> getRunningSteps() {
        List<Element> running = new ArrayList<>();
        for (WorkflowElement allWorkflowInstance : cacheWorkflowService.getRunningWorkflowInstances()) {//cacheWorkflowService.getAllWorkflowElements()) {     // TODO use getNextWorkflowInstances?
            running.addAll(getRunningProcessSteps(allWorkflowInstance.getElements()));
        }

        return running;
    }

    @Override
    public List<ProcessStep> getRunningProcessSteps(List<Element> elements) {
        List<ProcessStep> steps = new ArrayList<>();
        for (Element element : elements) {
            if (element instanceof ProcessStep) {
                if ((((ProcessStep) element).getStartDate() != null || ((ProcessStep) element).getScheduledStartDate() != null) && ((ProcessStep) element).getFinishedAt() == null) {
                    if (!steps.contains(element)) {
                        steps.add((ProcessStep) element);
                    }
                }
                // ignore else
            } else {
                steps.addAll(getRunningProcessSteps(element.getElements()));
            }

        }
        return steps;
    }

    @Override
    public List<ProcessStep> getNextSteps(Element workflow, Element andParent) {
        List<ProcessStep> nextSteps = new ArrayList<>();
        if (workflow instanceof ProcessStep) {
            if (!((ProcessStep) workflow).hasBeenExecuted() && ((ProcessStep) workflow).getStartDate() == null && ((ProcessStep) workflow).getScheduledStartDate() == null) {
                nextSteps.add((ProcessStep) workflow);
                return nextSteps;
            } else if ((((ProcessStep) workflow).getStartDate() != null || ((ProcessStep) workflow).getScheduledStartDate() != null) && ((ProcessStep) workflow).getFinishedAt() == null) {
                //Step is still running, ignore next step
                if (andParent != null) {
                    andParentHasRunningChild.put(andParent, true);
                }
                return nextSteps;
            } else {
                return nextSteps;
            }

        }
        for (Element element : workflow.getElements()) {
            if (element instanceof ProcessStep) {
                if (!((ProcessStep) element).hasBeenExecuted() && ((ProcessStep) element).getStartDate() == null && ((ProcessStep) element).getScheduledStartDate() == null) {
                    nextSteps.add((ProcessStep) element);
                    return nextSteps;
                } else if ((((ProcessStep) element).getStartDate() != null || ((ProcessStep) element).getScheduledStartDate() != null) && ((ProcessStep) element).getFinishedAt() == null) {
                    //Step is still running, ignore next step
                    if (andParent != null) {
                        andParentHasRunningChild.put(andParent, true);
                    }
                    return nextSteps;
                }
            } else {
                List<Element> subElementList = element.getElements();
                if (element instanceof ANDConstruct) {
                    andParentHasRunningChild.put(element, false);
                    List<ProcessStep> tempNextSteps = new ArrayList<>();
                    for (Element subElement : subElementList) {
                        tempNextSteps.addAll(getNextSteps(subElement, element));
                        if (andParentHasRunningChild.get(element)) {
                            return nextSteps;
                        }
                    }
                    nextSteps.addAll(tempNextSteps);

                } else if (element instanceof XORConstruct) {
                    nextSteps.addAll(getNextSteps(element.getParent().getNextXOR(), andParent));
                } else if (element instanceof LoopConstruct) {
                    if ((element.getNumberOfExecutions() < ((LoopConstruct) element).getNumberOfIterationsToBeExecuted())) {
                        for (Element subElement : subElementList) {
                            nextSteps.addAll(getNextSteps(subElement, andParent));
                        }

                    }
                } else { //sequence
                    nextSteps.addAll(getNextSteps(element, andParent));
                }
            }
            if (nextSteps.size() > 0) {
                return nextSteps;
            }
        }
        return nextSteps;
    }

    @Override
    public void resetChildren(List<Element> elementList) {
        if (elementList != null) {
            for (Element element : elementList) {
                if (element instanceof ProcessStep) {
                    ((ProcessStep) element).reset();

                } else {
                    resetChildren(element.getElements());
                }
            }
        }
    }


}
