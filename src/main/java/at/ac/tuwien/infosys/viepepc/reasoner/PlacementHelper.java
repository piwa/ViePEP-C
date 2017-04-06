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

    long getRemainingSetupTime(VirtualMachine vm, Date now);

    List<Element> getRunningSteps();

    List<ProcessStep> getRunningProcessSteps(List<Element> elements);

    void terminateVM(VirtualMachine virtualMachine);

    List<ProcessStep> getNextSteps(Element workflow, Element andElement);

    void resetChildren(List<Element> elementList);

	String getGammaVariable(VMType vmType);

	String getExecutionTimeViolationVariable(WorkflowElement workflowInstance);
	
	String getExecutionTimeVariable(WorkflowElement workflowInstance);

	double getPenaltyCostPerQoSViolationForProcessInstance(WorkflowElement workflowInstance);

	String getDecisionVariableX(Element step, VirtualMachine vm);

	String getFValueCVariable(VirtualMachine vm);
	
	String getFValueRVariable(VirtualMachine vm);

	int getBeta(VirtualMachine vm);

	String getDecisionVariableY(VirtualMachine vm);

	int getZ(String type, VirtualMachine vm);

	long getRemainingLeasingDuration(DateTime tau_t, VirtualMachine vm);

	String getGVariable(VirtualMachine vm);

	double getSuppliedCPUPoints(VirtualMachine vm);

	double getSuppliedRAMPoints(VirtualMachine vm);

	double getRequiredCPUPoints(ProcessStep step);

	double getRequiredRAMPoints(ProcessStep step);

	long getEnactmentDeadline(WorkflowElement workflowInstance);

	String getDecisionVariableX(Element step, Container container);

	String getDecisionVariableA(Container container, VirtualMachine vm);

	double getSuppliedCPUPoints(Container container);

	double getSuppliedRAMPoints(Container container);

	String getGYVariable(VirtualMachine vm);

	int imageForStepEverDeployedOnVM(ProcessStep step, VirtualMachine vm);

	void stopContainer(Container container);

	int imageForContainerEverDeployedOnVM(Container container, VirtualMachine vm);

	long getRemainingSetupTime(Container scheduledAtContainer, Date tau_t);

	String getATimesG(VirtualMachine vm, Container container);

	String getATimesT1(Container container, VirtualMachine vm);

	String getAtimesX(ProcessStep step, Container container, VirtualMachine vm);

}
