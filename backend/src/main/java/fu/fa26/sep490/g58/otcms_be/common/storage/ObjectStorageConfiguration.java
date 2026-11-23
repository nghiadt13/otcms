package fu.fa26.sep490.g58.otcms_be.common.storage;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks the {@link ObjectStoragePort} provider from {@code app.storage.provider} (default {@code local}). */
@Configuration(proxyBeanMethods = false)
public class ObjectStorageConfiguration {
    @Bean
    @ConditionalOnMissingBean(ObjectStoragePort.class)
    @ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
    ObjectStoragePort localObjectStorage(@Value("${app.storage.local-root:./.storage}") String root) {
        return new LocalObjectStorageAdapter(Path.of(root));
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.storage.provider", havingValue = "supabase-storage")
    SupabaseObjectStorageAdapter supabaseObjectStorage(
            @Value("${app.storage.supabase-s3.endpoint}") URI endpoint,
            @Value("${app.storage.supabase-s3.region}") String region,
            @Value("${app.storage.supabase-s3.access-key}") String accessKey,
            @Value("${app.storage.supabase-s3.secret-key}") String secretKey,
            @Value("${app.storage.bucket:otcms-media}") String bucket) {
        return new SupabaseObjectStorageAdapter(endpoint, region, accessKey, secretKey, bucket);
    }
}
