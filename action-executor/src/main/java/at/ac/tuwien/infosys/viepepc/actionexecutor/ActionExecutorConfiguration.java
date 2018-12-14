package at.ac.tuwien.infosys.viepepc.actionexecutor;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class ActionExecutorConfiguration {


    @Bean
    @Scope("prototype")
    public OnlyContainerDeploymentController getOnlyContainerDeploymentController(ProcessStep processStep) {
        return new OnlyContainerDeploymentController(processStep);
    }


}
