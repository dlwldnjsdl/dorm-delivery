package com.example.dormdelivery.push;
import com.example.dormdelivery.room.*;
import com.example.dormdelivery.user.User;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationFailureTest {
    @Test void oneFailedDeviceDoesNotPreventOtherRecipientsAndExpiredSubscriptionIsRemoved() throws Exception {
        var rooms=mock(RoomRepository.class);var members=mock(RoomMemberRepository.class);
        var subscriptions=mock(PushSubscriptionRepository.class);var sender=mock(WebPushSender.class);
        when(rooms.existsById(1L)).thenReturn(true);
        User user=new User();user.setId(2L);RoomMember member=new RoomMember();member.setUser(user);
        when(members.findByRoomId(1L)).thenReturn(List.of(member));
        when(members.existsByRoom_IdAndUser_Id(1L,2L)).thenReturn(true);
        PushSubscription first=new PushSubscription();first.setId(10L);first.setUserId(2L);
        PushSubscription second=new PushSubscription();second.setId(11L);second.setUserId(2L);
        when(subscriptions.findByUserIdIn(List.of(2L))).thenReturn(List.of(first,second));
        when(subscriptions.existsById(anyLong())).thenReturn(true);
        when(sender.send(eq(first),any())).thenThrow(new java.io.IOException("test-only"));
        when(sender.send(eq(second),any())).thenReturn(410);
        var listener=new RoomNotificationListener(members,rooms,subscriptions,sender);
        assertDoesNotThrow(()->listener.deliver(new RoomNotice(1L,3L,"CHAT")));
        verify(subscriptions).deleteById(11L);listener.close();
    }
    @Test void recipientLookupFailureIsContained() {
        var rooms=mock(RoomRepository.class);when(rooms.existsById(1L)).thenThrow(new IllegalStateException());
        var listener=new RoomNotificationListener(mock(RoomMemberRepository.class),rooms,mock(PushSubscriptionRepository.class),mock(WebPushSender.class));
        assertDoesNotThrow(()->listener.deliver(new RoomNotice(1L,3L,"ORDER")));listener.close();
    }
}
