package at.ac.tuwien.infosys.viepepc.reasoner.impl.configurations;

import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OneVMPerTaskImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.StartParExceedImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("StartParExceed-container")
public class StartParExceedProvisioningConfiguration {
	
	@Bean
	public ProcessInstancePlacementProblem initializeParameters() {
		log.info("Profile StartParExceed");
		return new StartParExceedImpl();
	}

}
