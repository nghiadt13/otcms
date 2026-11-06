package fu.fa26.sep490.g58.otcms_be.imaging.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({ImagingProperties.class, ImagingIdentityProperties.class})
public class ImagingConfiguration {
}
