package fu.fa26.sep490.g58.otcms_be.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a Spring integration test that must use the dedicated local PostgreSQL test database. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalPostgresTestInitializer.class)
public @interface LocalPostgresTest {
}
