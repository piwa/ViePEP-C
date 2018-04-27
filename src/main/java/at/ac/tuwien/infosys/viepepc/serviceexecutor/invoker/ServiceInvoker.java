package at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;

public interface ServiceInvoker {

    void invoke(VirtualMachine virtualMachine, ProcessStep processStep) throws ServiceInvokeException;

    void invoke(Container container, ProcessStep processStep) throws ServiceInvokeException;
}
