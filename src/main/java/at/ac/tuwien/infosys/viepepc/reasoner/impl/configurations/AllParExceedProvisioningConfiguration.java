package at.ac.tuwien.infosys.viepepc.reasoner.impl.configurations;

import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.AllParExceedImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.AllParNotExceedImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("AllParExceed-container")
public class AllParExceedProvisioningConfiguration {
	
	@Bean
	public ProcessInstancePlacementProblem initializeParameters() {
		log.info("Profile AllParExceed");
		return new AllParExceedImpl();
	}

}
