package com.campus.resource;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class ResourceInternalCatalogueTest {
    @Test void roomReadsRequireServiceKey()throws Exception{
        ResourceApiTest h=new ResourceApiTest();
        h.start();
        try{
            h.arrange();
            var list=h.in("GET","/internal/resources/rooms",null);
            h.status(200,list);
            assertEquals(2,h.tree(list).size());
            var room=h.in("GET","/internal/resources/rooms/"+ResourceApiTest.ROOM,null);
            h.status(200,room);
            assertEquals("R101",h.tree(room).path("code").asText());
            h.status(401,h.call(h.user,"GET","/internal/resources/rooms/"+ResourceApiTest.ROOM,null,false));
            h.status(404,h.in("GET","/internal/resources/rooms/"+UUID.randomUUID(),null));
            assertFalse(room.body().contains("checkin"));
        }
        finally{
            h.stop();
        }
    }
    @Test void seatReadsRequireServiceKey()throws Exception{
        ResourceApiTest h=new ResourceApiTest();
        h.start();
        try{
            h.arrange();
            var list=h.in("GET","/internal/resources/rooms/"+ResourceApiTest.ROOM+"/seats",null);
            h.status(200,list);
            assertEquals(2,h.tree(list).size());
            var seat=h.in("GET","/internal/resources/seats/"+ResourceApiTest.SEAT,null);
            h.status(200,seat);
            assertEquals(ResourceApiTest.ROOM.toString(),h.tree(seat).path("roomId").asText());
            h.status(401,h.call(h.user,"GET","/internal/resources/seats/"+ResourceApiTest.SEAT,null,false));
            h.status(404,h.in("GET","/internal/resources/seats/"+UUID.randomUUID(),null));
        }
        finally{
            h.stop();
        }
    }
}
