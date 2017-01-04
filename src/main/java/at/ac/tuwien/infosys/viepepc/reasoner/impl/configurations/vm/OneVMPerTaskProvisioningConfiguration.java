package at.ac.tuwien.infosys.viepepc.reasoner.impl.configurations.vm;

import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.vm.OneVMPerTaskImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("OneVMPerTask")
public class OneVMPerTaskProvisioningConfiguration {
	
	@Bean
	public ProcessInstancePlacementProblem initializeParameters() {
		log.info("Profile OneVMPerTask");
		return new OneVMPerTaskImpl();
	}

}
