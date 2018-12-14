package at.ac.tuwien.infosys.viepepc.reasoner.frincu.impl.configurations.frincu.vm;

import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.impl.frincu.vm.OneVMPerTaskImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile({"OneVMPerTask","test"})
public class OneVMPerTaskProvisioningConfiguration {
	
	@Bean
	public ProcessInstancePlacementProblem initializeParameters() {
		log.info("Profile OneVMPerTask");
		return new OneVMPerTaskImpl();
	}

}
