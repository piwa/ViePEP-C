package at.ac.tuwien.infosys.viepepc.engine.manager.rest;


import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElements;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.service.ServiceTypeNotFoundException;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * User: bonomat
 * Date: 11/20/13
 */
public interface WorkflowRestService {
    /**
     * Returns an explicit collection of all workflows in XML format in response to HTTP GET requests.
     *
     * @return the collection of features
     */
    List<WorkflowElement> getWorkflows() throws Exception;

    /**
     * adds a workflow via RESTful webservice to the viepep bpms. Which will immediatly be scheduled
     *
     * @param workflowJaxB the workflow to add
     */
    void addWorkflow(@RequestBody WorkflowElement workflowJaxB) throws ServiceTypeNotFoundException;

    void addWorkflow(@RequestBody WorkflowElements workflowElement) throws ServiceTypeNotFoundException;

    void stopExecution();

}