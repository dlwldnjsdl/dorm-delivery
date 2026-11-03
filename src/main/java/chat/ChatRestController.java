package com.example.dormdelivery.chat;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chats")
public class ChatRestController {

    private final ChatMessageRepository chatMessageRepository;

    public ChatRestController(
            ChatMessageRepository chatMessageRepository) {

        this.chatMessageRepository = chatMessageRepository;
    }

    // 특정 방의 이전 채팅 조회
    @GetMapping("/room/{roomId}")
    public List<ChatMessage> getChatHistory(
            @PathVariable Long roomId) {

        return chatMessageRepository
                .findByRoomIdOrderBySentAtAsc(roomId);
    }
}
