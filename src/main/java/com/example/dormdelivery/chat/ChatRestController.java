package com.example.dormdelivery.chat;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chats")
public class ChatRestController {
    private final com.example.dormdelivery.room.RoomMemberRepository members;

    private final ChatMessageRepository chatMessageRepository;

    public ChatRestController(
            ChatMessageRepository chatMessageRepository, com.example.dormdelivery.room.RoomMemberRepository members) {
        this.members=members;

        this.chatMessageRepository = chatMessageRepository;
    }

    // 특정 방의 이전 채팅 조회
    @GetMapping("/room/{roomId}")
    public List<ChatMessage> getChatHistory(
            @PathVariable Long roomId, jakarta.servlet.http.HttpSession session) {
        Long user=com.example.dormdelivery.push.PushSubscriptions.user(session);
        if(!members.existsByRoom_IdAndUser_Id(roomId,user)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);

        return chatMessageRepository
                .findByRoomIdOrderBySentAtAsc(roomId);
    }
}
