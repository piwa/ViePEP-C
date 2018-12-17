package at.ac.tuwien.infosys.viepepc.library.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Documented
@Import(LibraryConfiguration.class)
@Configuration
public @interface EnableLibraryModule {
}
