package at.ac.tuwien.infosys.viepepc.reasoner.impl.configurations;

import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OneVMPerTaskImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("OneVMPerTask-container")
public class OneVMPerTaskProvisioningConfiguration {
	
	@Bean
	public ProcessInstancePlacementProblem initializeParameters() {
		log.info("Profile OneVMPerTask");
		return new OneVMPerTaskImpl();
	}
/*
	@Bean
	public ProcessOptimizationResults processResults() {
		log.info("Profile OneVMPerTask");
		return new ProcessOptimizationResultsImpl();
	}
*/
}
