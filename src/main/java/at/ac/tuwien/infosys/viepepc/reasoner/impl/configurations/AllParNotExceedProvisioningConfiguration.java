package at.ac.tuwien.infosys.viepepc.reasoner.impl.configurations;

import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.AllParNotExceedImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.StartParNotExceedImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("AllParNotExceed-container")
public class AllParNotExceedProvisioningConfiguration {
	
	@Bean
	public ProcessInstancePlacementProblem initializeParameters() {
		log.info("Profile AllParNotExceed");
		return new AllParNotExceedImpl();
	}

}
