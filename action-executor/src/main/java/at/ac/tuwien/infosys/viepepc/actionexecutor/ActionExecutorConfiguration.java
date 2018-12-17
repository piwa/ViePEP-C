package at.ac.tuwien.infosys.viepepc.actionexecutor;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.*;

@Configuration
@PropertySources({
        @PropertySource("classpath:application_action_executor.properties")
})
public class ActionExecutorConfiguration {


    @Bean
    @Scope("prototype")
    public OnlyContainerDeploymentController getOnlyContainerDeploymentController(ProcessStep processStep) {
        return new OnlyContainerDeploymentController(processStep);
    }


}
