package com.campus.approval;
import java.time.Clock;import org.springframework.context.annotation.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration @EnableScheduling public class ApprovalConfiguration {
 @Bean("approvalClock") @ConditionalOnMissingBean(name="approvalClock") Clock approvalClock(){return Clock.systemUTC();}
}