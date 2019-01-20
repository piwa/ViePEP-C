package at.ac.tuwien.infosys.viepepc.serviceexecutor.configuration;

import at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker.ServiceInvoker;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker.ServiceInvokerImpl;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker.ServiceInvokerSimulation;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker.ServiceInvokerSimulationExternalTaskImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.*;

@Configuration
@PropertySources({
        @PropertySource("classpath:application_service_executor.properties")
})
public class ServiceExecutorConfiguration {

    @Value("${simulate}")
    private boolean simulate;
    @Value("${simulate.with.external.task.runner}")
    private boolean simulateWithExternalTaskRunner;

    @Bean
    public ServiceInvoker getServiceInvoker() {
        if(simulateWithExternalTaskRunner) {
            return new ServiceInvokerSimulationExternalTaskImpl();
        }
        else if (simulate) {
            return new ServiceInvokerSimulation();
        }
        return new ServiceInvokerImpl();
    }

}
