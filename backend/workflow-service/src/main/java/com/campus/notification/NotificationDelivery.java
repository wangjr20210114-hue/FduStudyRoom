package com.campus.notification;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component("notificationDelivery")
public class NotificationDelivery {
    private final NotificationStore store;private final SmtpTransport smtp;private final Environment env;
    public NotificationDelivery(NotificationStore store,SmtpTransport smtp,Environment env){this.store=store;this.smtp=smtp;this.env=env;}
    @Scheduled(fixedDelayString="${campus.notifications.delivery-delay-ms:1000}",initialDelayString="${campus.notifications.initial-delay-ms:1000}")
    public void deliverDue(){
        store.releaseDueInApp();if(!smtp.configured())return;
        int max=Math.max(1,Math.min(20,env.getProperty("campus.notifications.max-attempts",Integer.class,5)));
        int delay=Math.max(1,Math.min(86400,env.getProperty("campus.notifications.retry-seconds",Integer.class,30)));
        for(int i=0;i<50;i++){var next=store.claim(max);if(next==null)return;
            try{smtp.send(next);store.delivered(next);}catch(SmtpTransport.DeliveryFailure error){store.failed(next,error.code(),error.uncertain(),max,delay);}
        }
    }
    @Configuration(proxyBeanMethods=false) @EnableScheduling
    static class Scheduling {}
}
