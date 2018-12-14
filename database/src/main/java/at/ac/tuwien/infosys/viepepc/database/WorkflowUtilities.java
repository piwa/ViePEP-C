package at.ac.tuwien.infosys.viepepc.database;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import org.joda.time.DateTime;

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

	long getRemainingLeasingDuration(DateTime tau_t, VirtualMachine vm);

    long getRemainingSetupTime(Container scheduledAtContainer, DateTime tau_t);
}
