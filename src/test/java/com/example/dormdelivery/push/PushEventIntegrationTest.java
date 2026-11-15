package com.example.dormdelivery.push;

import com.example.dormdelivery.room.*;
import com.example.dormdelivery.user.*;
import com.example.dormdelivery.chat.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.messaging.simp.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(PushEventIntegrationTest.TransportConfig.class)
class PushEventIntegrationTest {
    @TestConfiguration static class TransportConfig {
        @Bean @Primary RecordingSender recordingSender() { return new RecordingSender(); }
    }
    static class RecordingSender extends WebPushSender {
        final BlockingQueue<Long> delivered=new LinkedBlockingQueue<>();
        RecordingSender() { super(false,"","",""); }
        @Override public boolean available() { return true; }
        @Override public int send(PushSubscription subscription,byte[] payload) { delivered.add(subscription.getUserId()); return 201; }
    }
    @Autowired RecordingSender sender;
    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired RoomMemberRepository members;
    @Autowired PushSubscriptionRepository subscriptions;
    @Autowired RoomMemberController controller;
    @Autowired ChatController chat;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.context.ApplicationEventPublisher events;
    @Autowired org.springframework.web.context.WebApplicationContext webContext;
    User user() {
        var user=new User();user.setName("test");user.setPhone(UUID.randomUUID().toString());user.setPassword("test-only");user.setDormitory("웅비홀");return users.save(user);
    }
    MockHttpSession session(User user) { var session=new MockHttpSession();session.setAttribute("userId",user.getId());return session; }
    void subscription(User user) {
        var sub=new PushSubscription();sub.setUserId(user.getId());sub.setEndpointHash(UUID.randomUUID().toString());sub.setEndpoint("https://fcm.googleapis.com/test");sub.setP256dh("transport-fake");sub.setAuth("transport-fake");subscriptions.save(sub);
    }
    @Test void browserSubscriptionJsonIsAcceptedAndCrossOriginWritesAreRejected() throws Exception {
        var user=user();
        byte[] bytes=new byte[65];bytes[0]=4;
        String key=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String auth=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        String json="{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/"+UUID.randomUUID()+"\",\"expirationTime\":null,\"keys\":{\"p256dh\":\""+key+"\",\"auth\":\""+auth+"\"}}";
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext).addFilters(webContext.getBean(com.example.dormdelivery.config.RequestOriginFilter.class)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/push/subscriptions").session(session(user)).header("Origin","http://localhost:5173").contentType("application/json").content(json))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().httpOnly("push_device",true));
        assertEquals(1,subscriptions.findByUserIdIn(List.of(user.getId())).size());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/push/subscriptions").session(session(user)).header("Origin","https://evil.test").contentType("application/json").content(json))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
    @Test void actualJoinChatAndOrderingDispatchOnlyToRoomRecipientsAfterCommit() throws Exception {
        sender.delivered.clear();
        var host=user();var guest=user();var outsider=user();subscription(host);subscription(guest);subscription(outsider);
        Room room=new Room();room.setDormitory("웅비홀");room.setStatus("RECRUITING");room.setCurrentPeople(1);room.setMaxPeople(2);rooms.save(room);
        RoomMember hm=new RoomMember();hm.setRoom(room);hm.setUser(host);hm.setHost(true);members.save(hm);
        Room other=new Room();other.setDormitory("웅비홀");other.setStatus("RECRUITING");rooms.save(other);
        RoomMember om=new RoomMember();om.setRoom(other);om.setUser(outsider);members.save(om);
        controller.joinRoom(room.getId(),guest.getId(),session(guest));
        assertEquals(host.getId(),sender.delivered.poll(5,TimeUnit.SECONDS));
        assertNull(sender.delivered.poll(100,TimeUnit.MILLISECONDS));
        var gm=members.findByUser_Id(guest.getId()).getFirst();
        controller.toggleReady(hm.getId(),session(host));controller.toggleReady(gm.getId(),session(guest));
        controller.startOrder(hm.getId(),session(host));
        assertEquals(guest.getId(),sender.delivered.poll(5,TimeUnit.SECONDS));
        ChatMessage message=new ChatMessage();message.setMessage("private body");
        var headers=SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);headers.setSessionAttributes(Map.of("httpSession",session(guest)));
        chat.sendMessage(room.getId(),message,headers);
        assertEquals(host.getId(),sender.delivered.poll(5,TimeUnit.SECONDS));
        assertNull(sender.delivered.poll(100,TimeUnit.MILLISECONDS));
        // Rolled-back transactions must never schedule a notification.
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status -> {
            events.publishEvent(new RoomNotice(room.getId(),guest.getId(),"CHAT"));
            throw new IllegalStateException("rollback-test");
        }));
        assertNull(sender.delivered.poll(200,TimeUnit.MILLISECONDS));
    }
}
