package com.campus.resource.application;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
public class CheckinCodeConfiguration {
    @Bean Clock resourceCheckinClock() { return Clock.systemUTC(); }
}
