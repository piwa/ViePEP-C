package at.ac.tuwien.infosys.viepepc.database;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;

import java.util.List;

/**
 * Created by philippwaibel on 18/05/16. edited by Gerta Sheganaku
 */
public interface WorkflowUtilities {


    void setFinishedWorkflows();

    List<ProcessStep> getNotStartedUnfinishedSteps();

    List<Element> getFlattenWorkflow(List<Element> flattenWorkflowList, Element parentElement);

    List<ProcessStep> getNextSteps(String workflowInstanceId);

    List<ProcessStep> getRunningProcessSteps(String workflowInstanceId);

    List<Element> getRunningSteps();

    List<ProcessStep> getRunningProcessSteps(List<Element> elements);

    List<ProcessStep> getNextSteps(Element workflow, Element andElement);

    void resetChildren(List<Element> elementList);

}
