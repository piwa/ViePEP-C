package at.ac.tuwien.infosys.viepepc.reasoner.frincu.impl.configurations.frincu.container;

import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.impl.frincu.container.OneVMPerTaskContainerImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("OneVMPerTaskContainer")
public class OneVMPerTaskContainerProvisioningConfiguration {
	
	@Bean
	public ProcessInstancePlacementProblem initializeParameters() {
		log.info("Profile OneVMPerTaskContainer");
		return new OneVMPerTaskContainerImpl();
	}

}
