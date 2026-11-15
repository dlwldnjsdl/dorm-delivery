package com.example.dormdelivery.chat;
import com.example.dormdelivery.room.RoomMemberRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import java.util.Map;

@Component
public class ChatAccess implements ChannelInterceptor {
    private final java.util.concurrent.ConcurrentHashMap<String,HttpSession> sessions=new java.util.concurrent.ConcurrentHashMap<>();
    private final RoomMemberRepository members;
    public ChatAccess(RoomMemberRepository members) { this.members=members; }
    public static Long user(Map<String,Object> attributes) {
        try {
            if(attributes!=null && attributes.get("httpSession") instanceof HttpSession session && session.getAttribute("userId") instanceof Long id) return id;
        } catch(IllegalStateException ignored) {}
        throw new MessagingException("로그인이 필요합니다.");
    }
    @Override public Message<?> preSend(Message<?> message,MessageChannel channel) {
        var accessor=MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor.class);
        if(accessor==null || accessor.getCommand()==null) return message;
        StompCommand command=accessor.getCommand();
        if(command==StompCommand.DISCONNECT) { if(accessor.getSessionId()!=null) sessions.remove(accessor.getSessionId()); return message; }
        if(command==StompCommand.UNSUBSCRIBE) return message;
        Long user=user(accessor.getSessionAttributes());
        if(command==StompCommand.CONNECT && accessor.getSessionId()!=null) sessions.put(accessor.getSessionId(),(HttpSession)accessor.getSessionAttributes().get("httpSession"));
        if(command==StompCommand.SEND || command==StompCommand.SUBSCRIBE) {
            String destination=accessor.getDestination();
            String prefix=command==StompCommand.SEND?"/app/chat/":"/topic/rooms/";
            if(destination==null || !destination.startsWith(prefix)) throw new MessagingException("허용되지 않은 목적지입니다.");
            try {
                Long room=Long.valueOf(destination.substring(prefix.length()));
                if(!members.existsByRoom_IdAndUser_Id(room,user)) throw new MessagingException("방 참여자만 사용할 수 있습니다.");
            } catch(NumberFormatException e) { throw new MessagingException("잘못된 방 번호입니다."); }
        }
        return message;
    }
    public Message<?> outbound(Message<?> message) {
        var headers=org.springframework.messaging.simp.SimpMessageHeaderAccessor.wrap(message);
        String destination=headers.getDestination();
        if(destination==null || !destination.startsWith("/topic/rooms/")) return message;
        HttpSession session=sessions.get(headers.getSessionId());
        try {
            Long id=user(session==null?null:Map.of("httpSession",session));
            Long room=Long.valueOf(destination.substring("/topic/rooms/".length()));
            return members.existsByRoom_IdAndUser_Id(room,id)?message:null;
        } catch(RuntimeException e) { return null; }
    }
    @org.springframework.context.event.EventListener
    public void disconnected(org.springframework.web.socket.messaging.SessionDisconnectEvent event) { sessions.remove(event.getSessionId()); }
}
