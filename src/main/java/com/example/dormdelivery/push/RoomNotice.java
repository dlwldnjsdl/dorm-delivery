package com.example.dormdelivery.push;
import com.example.dormdelivery.room.RoomMember;
import java.util.List;
public record RoomNotice(Long roomId, Long actorId, String type) {
    public List<Long> recipients(List<RoomMember> members) {
        return members.stream().filter(m -> m.getUser()!=null)
                .filter(m -> !m.getUser().getId().equals(actorId))
                .filter(m -> !"JOIN".equals(type) || m.isHost())
                .map(m -> m.getUser().getId()).distinct().toList();
    }
    public String body() {
        return switch(type) {
            case "JOIN" -> "공동배달 방에 새로운 참여자가 들어왔습니다.";
            case "ORDER" -> "방장이 공동배달 주문을 시작했습니다.";
            default -> "공동배달 방에 새 메시지가 도착했습니다.";
        };
    }
}
