package com.campus.reservation;

import com.campus.common.security.AuthSettings;
import javax.sql.DataSource;
import java.net.URI;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ReminderConfiguration {
 @Bean("reservationReminderClock") Clock reservationReminderClock(){return Clock.systemUTC();}
 @Bean ReminderDispatcher reservationReminderDispatcher(DataSource source,
   @Qualifier("reservationReminderClock") Clock clock,AuthSettings settings,
   @Value("${campus.reservation.notification-url:http://127.0.0.1:8081}") String endpoint) {
  return new ReminderDispatcher(source,clock,URI.create(endpoint),settings.serviceKey());
 }
}
