package at.ac.tuwien.infosys.viepepc.reasoner;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import org.joda.time.DateTime;

import java.util.Date;
import java.util.List;

/**
 * Created by philippwaibel on 18/05/16. edited by Gerta Sheganaku
 */
public interface PlacementHelper {


    void setFinishedWorkflows();

    List<ProcessStep> getNotStartedUnfinishedSteps();

    List<Element> getFlattenWorkflow(List<Element> flattenWorkflowList, Element parentElement);

    List<ProcessStep> getNextSteps(String workflowInstanceId);

    List<ProcessStep> getRunningProcessSteps(String workflowInstanceId);

    List<Element> getRunningSteps();

    List<ProcessStep> getRunningProcessSteps(List<Element> elements);

    void terminateVM(VirtualMachine virtualMachine);

    List<ProcessStep> getNextSteps(Element workflow, Element andElement);

    void resetChildren(List<Element> elementList);

	long getRemainingLeasingDuration(DateTime tau_t, VirtualMachine vm);

	void stopContainer(Container container);

    long getRemainingSetupTime(Container scheduledAtContainer, DateTime tau_t);
}
