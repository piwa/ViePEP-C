package at.ac.tuwien.infosys.viepepc.database.inmemory.services;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by philippwaibel on 10/06/16.
 */
@Component
public class CacheWorkflowService {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;


    public List<WorkflowElement> getRunningWorkflowInstances() {
        synchronized (inMemoryCache.getRunningWorkflows()) {
            List<WorkflowElement> workflows = Collections.synchronizedList(inMemoryCache.getRunningWorkflows());
            List<WorkflowElement> returnList = workflows.stream().filter(workflow -> workflow.getFinishedAt() == null).collect(Collectors.toList());

            return returnList;
        }
    }

    public void addWorkflowInstance(WorkflowElement workflowElement) {
        synchronized (inMemoryCache.getRunningWorkflows()) {
            inMemoryCache.getRunningWorkflows().add(workflowElement);
            inMemoryCache.getAllWorkflowInstances().add(workflowElement);
        }
    }


    public void deleteRunningWorkflowInstance(WorkflowElement workflowElement) {
        synchronized (inMemoryCache.getRunningWorkflows()) {
            Collections.synchronizedList(inMemoryCache.getRunningWorkflows()).remove(workflowElement);
        }
    }


    public List<WorkflowElement> getAllWorkflowElements() {
        synchronized (inMemoryCache.getRunningWorkflows()) {
            return inMemoryCache.getAllWorkflowInstances();
        }
    }

    public WorkflowElement getWorkflowById(String workflowInstanceId) {
        synchronized (inMemoryCache.getRunningWorkflows()) {
	        List<WorkflowElement> nextWorkflows = inMemoryCache.getRunningWorkflows();

            return nextWorkflows.stream().filter(nextWorkflow -> nextWorkflow.getName().equals(workflowInstanceId)).findFirst().orElse(null);
        }
    }
}
