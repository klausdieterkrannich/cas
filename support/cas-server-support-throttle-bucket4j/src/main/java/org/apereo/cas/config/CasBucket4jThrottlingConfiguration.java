package org.apereo.cas.config;

import org.apereo.cas.bucket4j.consumer.BucketConsumer;
import org.apereo.cas.bucket4j.producer.BucketProducer;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.support.CasFeatureModule;
import org.apereo.cas.throttle.ThrottledRequestExecutor;
import org.apereo.cas.util.spring.beans.BeanCondition;
import org.apereo.cas.util.spring.beans.BeanSupplier;
import org.apereo.cas.util.spring.boot.ConditionalOnFeature;
import org.apereo.cas.web.Bucket4jThrottledRequestExecutor;

import lombok.val;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ScopedProxyMode;

/**
 * This is {@link CasBucket4jThrottlingConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 6.0.0
 */
@Configuration(value = "CasBucket4jThrottlingConfiguration", proxyBeanMethods = false)
@EnableConfigurationProperties(CasConfigurationProperties.class)
@ConditionalOnFeature(feature = CasFeatureModule.FeatureCatalog.Throttling, module = "bucket4j")
public class CasBucket4jThrottlingConfiguration {
    private static final BeanCondition CONDITION = BeanCondition.on("cas.authn.throttle.bucket4j.enabled").isTrue().evenIfMissing();
    
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public ThrottledRequestExecutor throttledRequestExecutor(
        final ConfigurableApplicationContext applicationContext,
        @Qualifier("bucket4jThrottledRequestConsumer")
        final BucketConsumer bucket4jThrottledRequestConsumer) {
        return BeanSupplier.of(ThrottledRequestExecutor.class)
            .when(CONDITION.given(applicationContext.getEnvironment()))
            .supply(() -> new Bucket4jThrottledRequestExecutor(bucket4jThrottledRequestConsumer))
            .otherwiseProxy()
            .get();
    }

    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @ConditionalOnMissingBean(name = "bucket4jThrottledRequestConsumer")
    public BucketConsumer bucket4jThrottledRequestConsumer(
        final ConfigurableApplicationContext applicationContext,
        final CasConfigurationProperties casProperties) {
        return BeanSupplier.of(BucketConsumer.class)
            .when(CONDITION.given(applicationContext.getEnvironment()))
            .supply(() -> {
                val throttle = casProperties.getAuthn().getThrottle();
                return BucketProducer.builder().properties(throttle.getBucket4j()).build().produce();
            })
            .otherwiseProxy()
            .get();
    }
}
