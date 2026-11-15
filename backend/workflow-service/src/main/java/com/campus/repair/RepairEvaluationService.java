package com.campus.repair;

import com.campus.common.error.ApiException;
import com.campus.common.security.IdentityPrincipal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable evaluation and process history; upstream repair and resource workflows are separate. */
@Service
public class RepairEvaluationService {
 private final JdbcTemplate jdbc;
 public RepairEvaluationService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Order(UUID id,UUID reporterId,UUID technicianId,String status,UUID acceptedBy,Instant acceptedAt,String acceptanceComment,Integer rating,String evaluation,Instant evaluatedAt,int version){}
 public record Event(UUID id,UUID actorId,String eventType,String fromStatus,String toStatus,UUID assignedTo,String note,Instant createdAt,int orderVersion,Integer rating){}
 @Transactional
 public Order evaluate(UUID id,IdentityPrincipal actor,Map<String,Object> body){
  permission(actor,"repair:evaluate");Order order=load(id,true);rejectTechnician(order,actor);
  if(!"COMPLETED".equals(order.status()))throw new ApiException(409,"REPAIR_NOT_COMPLETED");
  if(!actor.id().equals(order.acceptedBy()))throw new ApiException(403,"REPAIR_NOT_ACCEPTOR");
  fields(body,Set.of("rating","evaluation","version"));int rating=integer(body,"rating");
  if(rating<1||rating>5)throw new ApiException(400,"REPAIR_INVALID_RATING");
  String evaluation=text(body,"evaluation");int version=integer(body,"version");
  if(order.version()!=version||order.evaluatedAt()!=null)throw new ApiException(409,"REPAIR_VERSION_CONFLICT");
  OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
  int changed=jdbc.update("UPDATE m3.repair_orders SET rating=?,evaluation=?,evaluated_at=?,updated_at=?,version=version+1 WHERE id=? AND version=? AND evaluated_at IS NULL",rating,evaluation,now,now,id,version);
  if(changed!=1)throw new ApiException(409,"REPAIR_VERSION_CONFLICT");
  jdbc.update("INSERT INTO m3.repair_events(id,order_id,actor_id,event_type,from_status,to_status,note,order_version,rating,created_at,updated_at) VALUES(?,?,?,'EVALUATE','COMPLETED','COMPLETED',?,?,?,?,?)",UUID.randomUUID(),id,actor.id(),evaluation,version+1,rating,now,now);
  return load(id,false);
 }
 /** Enforce independent acceptance without claiming unavailable resource release has completed. */
 @Transactional(readOnly=true)
 public void acceptance(UUID id,IdentityPrincipal actor,Map<String,Object> body){
  permission(actor,"repair:accept");Order order=load(id,false);rejectTechnician(order,actor);
  fields(body,Set.of("approved","comment","version"));
  if(!(body.get("approved") instanceof Boolean))throw new ApiException(400,"INVALID_REQUEST");
  text(body,"comment");if(integer(body,"version")!=order.version())throw new ApiException(409,"REPAIR_VERSION_CONFLICT");
  if(!"PENDING_ACCEPTANCE".equals(order.status()))throw new ApiException(409,"REPAIR_NOT_PENDING_ACCEPTANCE");
  throw new ApiException(409,"ACCEPTANCE_INTEGRATION_PENDING");
 }
 @Transactional(readOnly=true)
 public Order detail(UUID id,IdentityPrincipal actor){permission(actor,"repair:history:read");Order o=load(id,false);participant(o,actor);return o;}
 @Transactional(readOnly=true)
 public List<Event> history(UUID id,IdentityPrincipal actor){
  detail(id,actor);
  return jdbc.query("SELECT * FROM m3.repair_events WHERE order_id=? ORDER BY created_at,id",(r,n)->new Event(r.getObject("id",UUID.class),r.getObject("actor_id",UUID.class),r.getString("event_type"),r.getString("from_status"),r.getString("to_status"),r.getObject("assigned_to",UUID.class),r.getString("note"),instant(r,"created_at"),r.getInt("order_version"),r.getObject("rating",Integer.class)),id);
 }
 private Order load(UUID id,boolean lock){
  var rows=jdbc.query("SELECT * FROM m3.repair_orders WHERE id=?"+(lock?" FOR UPDATE":""),(r,n)->new Order(r.getObject("id",UUID.class),r.getObject("reporter_id",UUID.class),r.getObject("technician_id",UUID.class),r.getString("status"),r.getObject("accepted_by",UUID.class),instant(r,"accepted_at"),r.getString("acceptance_comment"),r.getObject("rating",Integer.class),r.getString("evaluation"),instant(r,"evaluated_at"),r.getInt("version")),id);
  if(rows.isEmpty())throw new ApiException(404,"REPAIR_NOT_FOUND");return rows.getFirst();
 }
 private static Instant instant(ResultSet r,String key)throws SQLException{OffsetDateTime v=r.getObject(key,OffsetDateTime.class);return v==null?null:v.toInstant();}
 private static void permission(IdentityPrincipal a,String code){if(a==null||!a.permissions().contains(code))throw new ApiException(403,"ACCESS_DENIED");}
 private static void rejectTechnician(Order o,IdentityPrincipal a){if(a.id().equals(o.technicianId()))throw new ApiException(403,"REPAIR_SELF_ACCEPTANCE_DENIED");}
 private static void participant(Order o,IdentityPrincipal a){if(!a.id().equals(o.reporterId())&&!a.id().equals(o.technicianId())&&!a.id().equals(o.acceptedBy()))throw new ApiException(403,"REPAIR_NOT_PARTICIPANT");}
 private static void fields(Map<String,Object> b,Set<String> f){if(b==null||!b.keySet().equals(f))throw new ApiException(400,"INVALID_REQUEST");}
 private static int integer(Map<String,Object> b,String k){if(!(b.get(k) instanceof Integer v))throw new ApiException(400,"INVALID_REQUEST");return v;}
 private static String text(Map<String,Object> b,String k){if(!(b.get(k) instanceof String v)||v.isBlank())throw new ApiException(400,"INVALID_REQUEST");return v;}
}
