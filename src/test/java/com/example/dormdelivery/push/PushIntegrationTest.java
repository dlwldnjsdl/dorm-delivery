package com.example.dormdelivery.push;

import com.example.dormdelivery.room.*;
import com.example.dormdelivery.user.*;
import com.example.dormdelivery.chat.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="push.enabled=false")
class PushIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired RoomMemberRepository members;
    @Autowired PushSubscriptions subscriptions;
    @Autowired PushSubscriptionRepository pushRepository;
    @Autowired ChatController chat;
    @Autowired ChatMessageRepository messages;
    @Autowired PushDevices devices;
    @Autowired ChatAccess access;
    MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.webAppContextSetup(context).build(); }
    MockHttpSession session(Long id) { var s=new MockHttpSession(); s.setAttribute("userId",id); return s; }
    User user() { var u=new User(); u.setName("테스트");u.setPhone(UUID.randomUUID().toString());u.setPassword("test-only");u.setDormitory("웅비홀");return users.save(u); }
    @Test void subscriptionOwnershipAndIdempotency() {
        var a=user(); var b=user(); byte[] key=new byte[65]; key[0]=4;
        var input=new SubscriptionInput("https://fcm.googleapis.com/fcm/send/"+UUID.randomUUID(),Map.of("p256dh",Base64.getUrlEncoder().withoutPadding().encodeToString(key),"auth",Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16])));
        subscriptions.save(a.getId(),input); subscriptions.save(a.getId(),input);
        assertEquals(1,pushRepository.findByUserIdIn(List.of(a.getId())).size());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->subscriptions.save(b.getId(),input));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->subscriptions.remove(b.getId(),input.endpoint()));
        subscriptions.remove(a.getId(),input.endpoint()); subscriptions.remove(a.getId(),input.endpoint());
        assertTrue(pushRepository.findByUserIdIn(List.of(a.getId())).isEmpty());
    }
    @Test void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/push/public-key")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/push/subscriptions").contentType("application/json").content("{\"endpoint\":\"x\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/chats/room/1")).andExpect(status().isUnauthorized());
    }
    @Test void logoutAfterSessionExpiryDeletesOnlyCurrentDevice() throws Exception {
        var a=user(); var b=user();
        var request=new org.springframework.mock.web.MockHttpServletRequest();
        var response=new org.springframework.mock.web.MockHttpServletResponse();
        String deviceHash=devices.deviceHash(request,response);
        var sub=new PushSubscription();sub.setUserId(a.getId());sub.setEndpointHash(UUID.randomUUID().toString());sub.setEndpoint("https://fcm.googleapis.com/device");sub.setP256dh("key");sub.setAuth("auth");sub.setDeviceHash(deviceHash);pushRepository.save(sub);
        var other=new PushSubscription();other.setUserId(a.getId());other.setEndpointHash(UUID.randomUUID().toString());other.setEndpoint("https://fcm.googleapis.com/other");other.setP256dh("key");other.setAuth("auth");other.setDeviceHash("other-device");pushRepository.save(other);
        // No login session: HttpOnly capability still permits current-device cleanup.
        mvc.perform(post("/users/logout").cookie(response.getCookie("push_device"))).andExpect(status().isOk());
        assertFalse(pushRepository.existsById(sub.getId()));assertTrue(pushRepository.existsById(other.getId()));
        // A new account cannot receive the old account's device notifications.
        sub.setId(null);pushRepository.save(sub);
        request.setCookies(response.getCookie("push_device"));devices.reconcile(request,b.getId());
        assertTrue(pushRepository.findByDeviceHash(deviceHash).isEmpty());
    }
    @Test void websocketStopsOutboundDeliveryWhenSessionExpiresOrMembershipEnds() {
        var u=user();var s=session(u.getId());
        var room=new Room();room.setStatus("RECRUITING");rooms.save(room);
        var member=new RoomMember();member.setRoom(room);member.setUser(u);members.save(member);
        var connect=org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.CONNECT);
        connect.setSessionId("security-test");connect.setSessionAttributes(Map.of("httpSession",s));
        access.preSend(org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],connect.getMessageHeaders()),null);
        var output=SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);output.setSessionId("security-test");output.setDestination("/topic/rooms/"+room.getId());
        var message=org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],output.getMessageHeaders());
        assertNotNull(access.outbound(message));
        members.delete(member);assertNull(access.outbound(message));
        member.setId(null);members.save(member);s.invalidate();assertNull(access.outbound(message));
    }
    @Test void signupLoginAndLogoutStillWork() throws Exception {
        String phone=UUID.randomUUID().toString();
        mvc.perform(post("/users/signup").contentType("application/json").content("{\"name\":\"테스트\",\"phone\":\""+phone+"\",\"password\":\"testing-only\",\"dormitory\":\"웅비홀\"}")).andExpect(status().isOk());
        var s=new MockHttpSession();
        mvc.perform(post("/users/login").session(s).contentType("application/json").content("{\"phone\":\""+phone+"\",\"password\":\"testing-only\"}")).andExpect(status().isOk());
        assertNotNull(s.getAttribute("userId"));
        mvc.perform(get("/users/me").session(s)).andExpect(status().isOk());
        mvc.perform(post("/users/logout").session(s)).andExpect(status().isOk());
        assertTrue(s.isInvalid());
    }
    @Test void completeRoomFlowAndSenderIdentityArePreserved() throws Exception {
        var host=user();var guest=user();var outsider=user();
        var hs=session(host.getId());var gs=session(guest.getId());var os=session(outsider.getId());
        mvc.perform(post("/rooms").session(hs).contentType("application/json").content("{\"title\":\"회귀 테스트\",\"menu\":\"치킨\",\"maxPeople\":2,\"deliveryPlace\":\"로비\"}")).andExpect(status().isOk());
        var room=members.findByUser_Id(host.getId()).getFirst().getRoom();
        mvc.perform(post("/room-members/room/"+room.getId()+"/user/"+guest.getId()).session(gs)).andExpect(status().isOk());
        var hm=members.findByUser_Id(host.getId()).getFirst();var gm=members.findByUser_Id(guest.getId()).getFirst();
        mvc.perform(post("/room-members/"+hm.getId()+"/start-order").session(os)).andExpect(status().isForbidden());
        mvc.perform(post("/room-members/"+hm.getId()+"/ready").session(hs)).andExpect(status().isOk());
        mvc.perform(post("/room-members/"+gm.getId()+"/ready").session(gs)).andExpect(status().isOk());
        mvc.perform(post("/room-members/"+hm.getId()+"/start-order").session(hs)).andExpect(status().isOk());
        ChatMessage message=new ChatMessage();message.setSenderId(outsider.getId());message.setMessage("본문은 푸시에 포함되지 않음");
        var headers=SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);headers.setSessionAttributes(Map.of("httpSession",gs));
        chat.sendMessage(room.getId(),message,headers);
        assertEquals(guest.getId(),messages.findByRoomIdOrderBySentAtAsc(room.getId()).getFirst().getSenderId());
        mvc.perform(get("/chats/room/"+room.getId()).session(os)).andExpect(status().isForbidden());
        mvc.perform(get("/chats/room/"+room.getId()).session(gs)).andExpect(status().isOk());
        mvc.perform(post("/room-members/"+hm.getId()+"/settle").session(hs)).andExpect(status().isOk());
        mvc.perform(post("/room-members/"+gm.getId()+"/settle").session(gs)).andExpect(status().isOk());
        mvc.perform(post("/room-members/"+hm.getId()+"/complete").session(hs)).andExpect(status().isOk());
        assertEquals("COMPLETED",rooms.findById(room.getId()).orElseThrow().getStatus());
        mvc.perform(post("/room-members/"+hm.getId()+"/ready").session(hs)).andExpect(status().isBadRequest());
    }
}
