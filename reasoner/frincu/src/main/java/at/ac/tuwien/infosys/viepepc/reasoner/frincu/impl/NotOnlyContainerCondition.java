package at.ac.tuwien.infosys.viepepc.reasoner.frincu.impl;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class NotOnlyContainerCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        return !environment.acceptsProfiles("OnlyContainerGeneticAlgorithm", "OnlyContainerBaseline");
    }
}
