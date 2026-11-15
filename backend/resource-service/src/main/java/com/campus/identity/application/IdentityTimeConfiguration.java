package com.campus.identity.application;
import java.time.Clock;import org.springframework.context.annotation.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
@Configuration(proxyBeanMethods=false)
public class IdentityTimeConfiguration {
 @Bean("identityClock") @ConditionalOnMissingBean(name="identityClock") Clock identityClock(){return Clock.systemUTC();}
}
