package at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;

public interface ServiceInvoker {

    void invoke(Container container, ProcessStep processStep) throws ServiceInvokeException;
}
