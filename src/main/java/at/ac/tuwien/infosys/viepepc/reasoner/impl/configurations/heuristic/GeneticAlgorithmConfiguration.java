package at.ac.tuwien.infosys.viepepc.reasoner.impl.configurations.heuristic;

import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.frincu.vm.AllParExceedImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.PIPPImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("GeneticAlgorithm")
public class GeneticAlgorithmConfiguration {
	
	@Bean
	public ProcessInstancePlacementProblem initializeParameters() {
		log.info("Profile GeneticAlgorithm");
		return new PIPPImpl();
	}

}
