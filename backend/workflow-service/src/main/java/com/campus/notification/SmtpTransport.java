package com.campus.notification;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Small SMTP adapter: STARTTLS/implicit TLS and AUTH PLAIN; no credentials are logged. */
@Component
public class SmtpTransport {
    public static final class DeliveryFailure extends Exception {
        private final String code;private final boolean uncertain;
        DeliveryFailure(String code,boolean uncertain){super(code);this.code=code;this.uncertain=uncertain;}
        public String code(){return code;}public boolean uncertain(){return uncertain;}
    }
    private final Environment env;
    public SmtpTransport(Environment env){this.env=env;}
    private String value(String key,String fallback){return env.getProperty("campus.notifications.smtp."+key,fallback);}
    public boolean configured(){return !value("host","").isBlank()&&!value("from","").isBlank();}
    private boolean address(String address){return address!=null&&address.length()<=254&&address.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?");}
    private SSLSocket secure(Socket raw,String host,int port)throws IOException {
        SSLSocket socket=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(raw,host,port,true);
        var parameters=socket.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");socket.setSSLParameters(parameters);socket.startHandshake();return socket;
    }
    public void send(NotificationStore.Delivery task)throws DeliveryFailure {
        String host=value("host",""),from=value("from",""),mode=value("tls-mode","starttls"),username=value("username",""),password=value("password","");
        boolean insecure=Boolean.parseBoolean(value("allow-insecure","false"));
        if(!configured())throw new DeliveryFailure("SMTP_NOT_CONFIGURED",false);
        if(username.isBlank()!=password.isEmpty())throw new DeliveryFailure("SMTP_AUTH_CONFIGURATION_INVALID",false);
        if(!address(from)||!address(task.address()))throw new DeliveryFailure("EMAIL_ADDRESS_INVALID",false);
        if(!Set.of("starttls","implicit","plain").contains(mode))throw new DeliveryFailure("SMTP_TLS_CONFIGURATION_INVALID",false);
        if(insecure&&mode.equals("starttls"))mode="plain";
        if(mode.equals("plain")&&(!insecure||!username.isEmpty()||!password.isEmpty()))throw new DeliveryFailure("SMTP_TLS_REQUIRED",false);
        int port,timeout;try{port=Integer.parseInt(value("port",mode.equals("implicit")?"465":"587"));timeout=Integer.parseInt(value("timeout-ms","3000"));if(port<1||port>65535||timeout<100||timeout>30000)throw new NumberFormatException();}catch(NumberFormatException error){throw new DeliveryFailure("SMTP_CONFIGURATION_INVALID",false);}
        boolean dataStarted=false;Socket raw=new Socket();Socket active=raw;
        try {
            raw.connect(new InetSocketAddress(host,port),timeout);raw.setSoTimeout(timeout);
            if(mode.equals("implicit"))active=secure(raw,host,port);
            var channel=new Channel(active);channel.expect(220);channel.command("EHLO studyroom",250);
            if(mode.equals("starttls")){channel.command("STARTTLS",220);active=secure(raw,host,port);channel=new Channel(active);channel.command("EHLO studyroom",250);}
            if(!username.isBlank()){String auth=Base64.getEncoder().encodeToString(("\0"+username+"\0"+password).getBytes(StandardCharsets.UTF_8));channel.command("AUTH PLAIN "+auth,235);}
            channel.command("MAIL FROM:<"+from+">",250);channel.command("RCPT TO:<"+task.address()+">",250);channel.command("DATA",354);
            String subject=encodedSubject(task.title());
            String body=Base64.getMimeEncoder(76,"\r\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(task.body().getBytes(StandardCharsets.UTF_8));
            dataStarted=true;channel.write("From: <"+from+">\r\nTo: <"+task.address()+">\r\nSubject: "+subject+"\r\nMessage-ID: <"+task.id()+"@studyroom>\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"+body+"\r\n.\r\n");
            channel.expect(250);dataStarted=false; // SMTP acceptance confirmed; QUIT failure cannot retract it.
            try{channel.command("QUIT",221);}catch(IOException|DeliveryFailure ignored){}
        }catch(DeliveryFailure error){throw error;}catch(IOException error){throw new DeliveryFailure(dataStarted?"SMTP_DELIVERY_UNCERTAIN":"SMTP_UNAVAILABLE",dataStarted);}finally{try{active.close();}catch(IOException ignored){}if(active!=raw)try{raw.close();}catch(IOException ignored){}}
    }
    private static String encodedSubject(String title){
        // RFC 2047 section 2: <=75 characters per encoded-word and <=76 per physical line.
        // 39 UTF-8 bytes yield a <=64 character word, leaving room for "Subject: ".
        var result=new StringBuilder();var part=new StringBuilder();int bytes=0;
        for(int offset=0;offset<title.length();){
            int point=title.codePointAt(offset);String unit=new String(Character.toChars(point));int length=unit.getBytes(StandardCharsets.UTF_8).length;
            if(bytes+length>39){appendSubjectPart(result,part);part.setLength(0);bytes=0;}
            part.append(unit);bytes+=length;offset+=Character.charCount(point);
        }
        if(!part.isEmpty())appendSubjectPart(result,part);return result.toString();
    }
    private static void appendSubjectPart(StringBuilder result,StringBuilder part){
        if(!result.isEmpty())result.append("\r\n ");
        result.append("=?UTF-8?B?").append(Base64.getEncoder().encodeToString(part.toString().getBytes(StandardCharsets.UTF_8))).append("?=");
    }
    private static final class Channel {
        private final BufferedReader reader;private final Writer writer;
        Channel(Socket socket)throws IOException{reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));writer=new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8);}
        void write(String text)throws IOException{writer.write(text);writer.flush();}
        void command(String text,int expected)throws IOException,DeliveryFailure{write(text+"\r\n");expect(expected);}
        String line()throws IOException{StringBuilder out=new StringBuilder();for(int n=0;n<4096;n++){int ch=reader.read();if(ch<0)throw new EOFException();if(ch=='\n')return out.toString().replace("\r","");out.append((char)ch);}throw new IOException("SMTP line too long");}
        void expect(int expected)throws IOException,DeliveryFailure{for(int n=0;n<100;n++){String line=line();if(line.length()<3||!line.substring(0,3).chars().allMatch(Character::isDigit))throw new IOException("Invalid SMTP reply");int code=Integer.parseInt(line.substring(0,3));if(code!=expected)throw new DeliveryFailure("SMTP_REJECTED",false);if(line.length()==3||line.charAt(3)!='-')return;}throw new IOException("SMTP reply too long");}
    }
}
