package com.example.dormdelivery.push;
import com.example.dormdelivery.room.*;
import com.example.dormdelivery.user.User;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoomNoticeTest {
    RoomMember member(long userId, boolean host) {
        User user=new User(); user.setId(userId);
        RoomMember member=new RoomMember(); member.setUser(user); member.setHost(host); return member;
    }
    @Test void chatExcludesAuthorAndDeduplicatesRecipients() {
        assertEquals(List.of(2L), new RoomNotice(9L,1L,"CHAT").recipients(List.of(member(1,true),member(2,false),member(2,false))));
    }
    @Test void joiningOnlyNotifiesHost() {
        assertEquals(List.of(1L),new RoomNotice(9L,3L,"JOIN").recipients(List.of(member(1,true),member(2,false),member(3,false))));
    }
    @Test void orderingExcludesHost() {
        assertEquals(List.of(2L),new RoomNotice(9L,1L,"ORDER").recipients(List.of(member(1,true),member(2,false))));
    }
}
