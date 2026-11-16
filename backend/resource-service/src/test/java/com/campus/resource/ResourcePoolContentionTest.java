package com.campus.resource;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class ResourcePoolContentionTest {
    @Test void identityHttpDoesNotStarveSmallPool()throws Exception{
        ResourceApiTest h=new ResourceApiTest();
        h.poolSize=2;
        h.eligibilityBorrowsConnection=true;
        h.start();
        try{
            h.arrange();
            var result=h.race(h.alloc("M1",UUID.randomUUID(),ResourceApiTest.ROOM,ResourceApiTest.SEAT,h.userId,"01:00","02:00"),h.alloc("M1",UUID.randomUUID(),ResourceApiTest.ROOM,ResourceApiTest.SEAT,h.user2Id,"01:00","02:00"));
            assertEquals(Set.of(201,409),new HashSet<>(result),"resource contract response: identity HTTP must not starve own database pool");
        }
        finally{
            h.stop();
        }
    }
}
