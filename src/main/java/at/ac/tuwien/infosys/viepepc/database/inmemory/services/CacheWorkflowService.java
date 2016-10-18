package at.ac.tuwien.infosys.viepepc.database.inmemory.services;

import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

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
            List<WorkflowElement> returnList = new ArrayList<>();
            Iterator<WorkflowElement> iterator = workflows.iterator();
            while (iterator.hasNext()) {
                WorkflowElement workflow = iterator.next(); // TODO rerun if: java.util.ConcurrentModificationException: null
                if (workflow.getFinishedAt() == null) {
                    returnList.add(workflow);
                }
            }

            return returnList;
        }
    }

    public void addWorkflowInstance(WorkflowElement workflowElement) {
        synchronized (inMemoryCache.getRunningWorkflows()) {
            inMemoryCache.addRunningWorkflow(workflowElement);
            inMemoryCache.addToAllWorkflows(workflowElement);
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
	        Iterator<WorkflowElement> iterator = nextWorkflows.iterator();
	
	        while(iterator.hasNext()) {
	            WorkflowElement nextWorkflow = iterator.next();
	            if (nextWorkflow.getName().equals(workflowInstanceId)) {
	                return nextWorkflow;
	            }
	        }
	        return null;
        }
    }
}
