package at.ac.tuwien.infosys.viepepc.reasoner.impl.configurations.container;

import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.container.StartParExceedContainerImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.vm.StartParExceedImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("StartParExceedContainer")
public class StartParExceedContainerProvisioningConfiguration {
	
	@Bean
	public ProcessInstancePlacementProblem initializeParameters() {
		log.info("Profile StartParExceedContainer");
		return new StartParExceedContainerImpl();
	}

}
