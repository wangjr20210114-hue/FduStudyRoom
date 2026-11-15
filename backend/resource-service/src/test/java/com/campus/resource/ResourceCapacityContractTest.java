package com.campus.resource;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class ResourceCapacityContractTest {
    @Test void plannedCapacityIncludesTheWholeRequestedWindow()throws Exception{
        ResourceApiTest h=new ResourceApiTest();
        h.start();
        try{
            h.arrange();
            var room=h.call(h.admin,"POST","/api/resources/rooms",h.room("CAPACITY"),false);
            h.status(201,room);
            String id=h.tree(room).path("id").asText();
            for(String code:List.of("C1","C2"))h.status(201,h.call(h.admin,"POST","/api/resources/rooms/"+id+"/seats",ResourceApiTest.map("code",code,"socketType","NONE","enabled",true),false));
            var result=h.in("GET","/internal/resources/capacity?startsAt=2030-06-03T00:00:00Z&endsAt=2030-06-03T04:00:00Z&roomId="+id,null);
            h.status(200,result);
            assertEquals(28800,h.tree(result).path("capacitySeatSeconds").asLong(),"resource contract response: S024 two seats times four planned hours");
            assertTrue(h.tree(result).path("algorithm").asText().contains("PLANNED_CAPACITY"));
            var actual=h.in("GET","/internal/resources/capacity?startsAt=2030-06-03T00:00:00Z&endsAt=2030-06-03T04:00:00Z&asOf=2030-06-03T00:00:00Z&roomId="+id,null);
            h.status(200,actual);
            assertEquals(0,h.tree(actual).path("capacitySeatSeconds").asLong());
            assertFalse(h.tree(actual).path("algorithm").asText().contains("PLANNED_CAPACITY"));
        }
        finally{
            h.stop();
        }
    }
}
