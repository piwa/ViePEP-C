package at.ac.tuwien.infosys.viepepc.database.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Documented
@Import(DatabaseConfiguration.class)
@Configuration
public @interface EnableDatabaseModule {
}
