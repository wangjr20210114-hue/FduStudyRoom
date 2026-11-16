package com.campus.resource;
import com.campus.resource.application.ResourceStore;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class ResourceStatisticsBirthTest {
    @Test void birthIsKnownAbsenceButMigratedHistoryStaysUnknown()throws Exception{
        ResourceApiTest h=new ResourceApiTest();
        h.start();
        try{
            h.arrange();
            var body=h.room("BIRTH");
            body.put("openTime","00:00");
            body.put("closeTime","00:00");
            var r=h.call(h.admin,"POST","/api/resources/rooms",body,false);
            h.status(201,r);
            UUID room=UUID.fromString(h.tree(r).path("id").asText());
            h.status(201,h.call(h.admin,"POST","/api/resources/rooms/"+room+"/seats",ResourceApiTest.map("code","S1","socketType","NONE","enabled",true),false));
            h.clock("01:00");
            var stat=h.in("GET","/internal/resources/statistics?from=2030-06-02T23:00:00Z&to=2030-06-03T01:00:00Z&asOf=2030-06-03T01:00:00Z&roomId="+room,null);
            h.status(200,stat);
            assertTrue(h.tree(stat).path("historyComplete").asBoolean(),"resource contract response: HTTP birth proves earlier nonexistence");
            assertEquals(3600,h.tree(stat).path("capacitySeatSeconds").asLong());
            var before=h.in("GET","/internal/resources/statistics?from=2030-06-02T22:00:00Z&to=2030-06-02T23:30:00Z&asOf=2030-06-02T23:30:00Z&roomId="+room,null);
            h.status(200,before);
            assertTrue(h.tree(before).path("historyComplete").asBoolean());
            assertEquals(0,h.tree(before).path("capacitySeatSeconds").asLong());
            var store=h.app.getBean(ResourceStore.class);
            store.transaction(()->{
                store.event(UUID.randomUUID(),"ADMIN",ResourceApiTest.ROOM,1,"ROOM",ResourceApiTest.ROOM,"0".repeat(64),store.room(ResourceApiTest.ROOM,false),store.now(),false);return null;
            });
            var legacy=h.in("GET","/internal/resources/statistics?from=2030-06-02T23:00:00Z&to=2030-06-03T01:00:00Z&asOf=2030-06-03T01:00:00Z&roomId="+ResourceApiTest.ROOM,null);
            h.status(200,legacy);
            assertFalse(h.tree(legacy).path("historyComplete").asBoolean());
            assertTrue(h.tree(legacy).path("capacitySeatSeconds").isNull());
        }
        finally{
            h.stop();
        }
    }
}
