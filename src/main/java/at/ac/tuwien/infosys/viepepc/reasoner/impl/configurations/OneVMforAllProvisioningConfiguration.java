package at.ac.tuwien.infosys.viepepc.reasoner.impl.configurations;

import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OneVMforAllImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("OneVMforAll-container")
public class OneVMforAllProvisioningConfiguration {
	
	@Bean
	public ProcessInstancePlacementProblem initializeParameters() {
		log.info("Profile OneVMforAll");
		return new OneVMforAllImpl();
	}
/*
	@Bean
	public ProcessOptimizationResults processResults() {
		log.info("Profile OneVMforAll");
		return new ProcessOptimizationResultsImpl();
	}
*/
}
