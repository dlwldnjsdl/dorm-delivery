package com.example.dormdelivery.chat;

import com.example.dormdelivery.room.RoomMemberRepository;
import com.example.dormdelivery.user.User;
import com.example.dormdelivery.user.UserRepository;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final RoomMemberRepository roomMemberRepository;

    public ChatController(
            SimpMessagingTemplate messagingTemplate,
            ChatMessageRepository chatMessageRepository,
            UserRepository userRepository,
            RoomMemberRepository roomMemberRepository) {

        this.messagingTemplate = messagingTemplate;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.roomMemberRepository = roomMemberRepository;
    }

    @MessageMapping("/chat/{roomId}")
    public void sendMessage(
            @DestinationVariable Long roomId,
            ChatMessage message) {

        // 사용자 ID가 없으면 전송 불가
        if (message.getSenderId() == null) {
            return;
        }

        // 실제 회원 조회
        User user = userRepository
                .findById(message.getSenderId())
                .orElse(null);

        if (user == null) {
            return;
        }

        // 해당 방 참여자가 아니면 채팅 불가
        boolean isMember =
                roomMemberRepository
                        .existsByRoom_IdAndUser_Id(
                                roomId,
                                user.getId()
                        );

        if (!isMember) {
            return;
        }

        // 방 번호 설정
        message.setRoomId(roomId);

        // 이름은 브라우저 입력값이 아니라
        // DB에 저장된 실제 회원 이름 사용
        message.setSenderName(user.getName());

        // 전송 시간
        message.setSentAt(LocalDateTime.now());

        // DB 저장
        ChatMessage savedMessage =
                chatMessageRepository.save(message);

        // 같은 방 사용자들에게 실시간 전송
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomId,
                savedMessage
        );
    }
}