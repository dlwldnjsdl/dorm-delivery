package com.example.dormdelivery.room;

import com.example.dormdelivery.user.User;
import com.example.dormdelivery.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.util.List;

@RestController
@RequestMapping("/room-members")
public class RoomMemberController {

    private final RoomMemberRepository roomMemberRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public RoomMemberController(
            RoomMemberRepository roomMemberRepository,
            RoomRepository roomRepository,
            UserRepository userRepository) {

        this.roomMemberRepository = roomMemberRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    // 특정 방 참여자 목록 조회
    @GetMapping("/room/{roomId}")
    public List<RoomMember> getMembers(@PathVariable Long roomId) {
        return roomMemberRepository.findByRoomId(roomId);
    }

    @GetMapping("/user/{userId}")
    public List<RoomMember> getUserRooms(
            @PathVariable Long userId
    ) {
        return roomMemberRepository.findByUser_Id(userId);
    }

    // 실제 회원이 방에 참여
    @PostMapping("/room/{roomId}/user/{userId}")
    public ResponseEntity<?> joinRoom(
            @PathVariable Long roomId,
            @PathVariable Long userId,
            HttpSession session
    ) {

        Long loginUserId =
                (Long) session.getAttribute("userId");

        // 로그인 확인
        if (loginUserId == null) {
            return ResponseEntity
                    .status(401)
                    .body("로그인이 필요합니다.");
        }

        // 다른 사람 ID를 임의로 넣는 것 방지
        if (!loginUserId.equals(userId)) {
            return ResponseEntity
                    .status(403)
                    .body("본인 계정으로만 참여할 수 있습니다.");
        }

        Room room =
                roomRepository
                        .findById(roomId)
                        .orElse(null);

        User user =
                userRepository
                        .findById(userId)
                        .orElse(null);

        if (room == null || user == null) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        // 다른 기숙사 방 참여 방지
        if (!user.getDormitory()
                .equals(room.getDormitory())) {

            return ResponseEntity
                    .status(403)
                    .body("같은 기숙사의 방만 참여할 수 있습니다.");
        }

        if (!"RECRUITING"
                .equals(room.getStatus())) {

            return ResponseEntity
                    .badRequest()
                    .body("현재 모집 중인 방이 아닙니다.");
        }

        if (roomMemberRepository
                .existsByRoom_IdAndUser_Id(
                        roomId,
                        userId
                )) {

            return ResponseEntity
                    .badRequest()
                    .body("이미 참여 중인 방입니다.");
        }

        if (room.getCurrentPeople()
                >= room.getMaxPeople()) {

            return ResponseEntity
                    .badRequest()
                    .body("방 인원이 가득 찼습니다.");
        }

        RoomMember member =
                new RoomMember();

        member.setRoom(room);
        member.setUser(user);
        member.setMemberName(
                user.getName()
        );

        member.setReady(false);
        member.setSettled(false);

        // 혹시 참여자가 0명인 방이면 방장
        member.setHost(
                room.getCurrentPeople() == 0
        );

        roomMemberRepository.save(member);

        room.setCurrentPeople(
                room.getCurrentPeople() + 1
        );

        roomRepository.save(room);

        return ResponseEntity.ok(member);
    }


    // 방에서 참여자 나가기
    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long memberId) {

        return roomMemberRepository.findById(memberId)
                .map(member -> {

                    Room room = member.getRoom();

                    // 모집중이 아니면 퇴장 불가
                    if (!"RECRUITING".equals(room.getStatus())) {
                        return ResponseEntity.badRequest()
                                .<Void>build();
                    }

                    roomMemberRepository.delete(member);

                    // 현재 인원 -1
                    if (room.getCurrentPeople() > 0) {

                        room.setCurrentPeople(
                                room.getCurrentPeople() - 1
                        );

                        roomRepository.save(room);
                    }

                    return ResponseEntity.noContent()
                            .<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Ready 상태 변경
    @PostMapping("/{memberId}/ready")
    public ResponseEntity<RoomMember> toggleReady(
            @PathVariable Long memberId) {

        return roomMemberRepository.findById(memberId)
                .map(member -> {

                    // Ready 상태 반대로 변경
                    member.setReady(!member.isReady());

                    RoomMember savedMember =
                            roomMemberRepository.save(member);

                    Room room = member.getRoom();

                    // 같은 방 참여자 전부 조회
                    List<RoomMember> members =
                            roomMemberRepository.findByRoomId(
                                    room.getId()
                            );

                    // 전원이 Ready인지 확인
                    boolean allReady =
                            !members.isEmpty()
                                    && members.stream()
                                    .allMatch(RoomMember::isReady);

                    // 전원 Ready면 모집완료
                    if (allReady) {
                        room.setStatus("RECRUITMENT_COMPLETE");
                    } else {
                        room.setStatus("RECRUITING");
                    }

                    roomRepository.save(room);

                    return ResponseEntity.ok(savedMember);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 방장 지정
    @PostMapping("/{memberId}/host")
    public ResponseEntity<RoomMember> assignHost(
            @PathVariable Long memberId) {

        return roomMemberRepository.findById(memberId)
                .map(member -> {

                    Room room = member.getRoom();

                    List<RoomMember> members =
                            roomMemberRepository.findByRoomId(
                                    room.getId()
                            );

                    // 기존 방장 모두 해제
                    for (RoomMember roomMember : members) {
                        roomMember.setHost(false);
                        roomMemberRepository.save(roomMember);
                    }

                    // 선택한 참여자를 방장으로 지정
                    member.setHost(true);

                    RoomMember savedMember =
                            roomMemberRepository.save(member);

                    return ResponseEntity.ok(savedMember);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 방장만 주문 시작
    @PostMapping("/{memberId}/start-order")
    public ResponseEntity<Room> startOrder(
            @PathVariable Long memberId) {

        return roomMemberRepository.findById(memberId)
                .map(member -> {

                    Room room = member.getRoom();

                    // 방장이 아니면 불가
                    if (!member.isHost()) {
                        return ResponseEntity.badRequest()
                                .<Room>build();
                    }

                    // 모집완료 상태에서만 주문 시작 가능
                    if (!"RECRUITMENT_COMPLETE"
                            .equals(room.getStatus())) {

                        return ResponseEntity.badRequest()
                                .<Room>build();
                    }

                    room.setStatus("ORDERING");

                    Room savedRoom =
                            roomRepository.save(room);

                    return ResponseEntity.ok(savedRoom);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 개인 정산 완료 / 취소
    @PostMapping("/{memberId}/settle")
    public ResponseEntity<RoomMember> toggleSettlement(
            @PathVariable Long memberId) {

        return roomMemberRepository.findById(memberId)
                .map(member -> {

                    Room room = member.getRoom();

                    // 주문중 상태에서만 정산 가능
                    if (!"ORDERING".equals(room.getStatus())) {
                        return ResponseEntity.badRequest()
                                .<RoomMember>build();
                    }

                    // 정산 상태 반대로 변경
                    member.setSettled(!member.isSettled());

                    RoomMember savedMember =
                            roomMemberRepository.save(member);

                    List<RoomMember> members =
                            roomMemberRepository.findByRoomId(
                                    room.getId()
                            );

                    // 전원이 정산했는지 확인
                    boolean allSettled =
                            !members.isEmpty()
                                    && members.stream()
                                    .allMatch(RoomMember::isSettled);

                    // 전원 정산 완료
                    if (allSettled) {
                        room.setStatus("SETTLEMENT_COMPLETE");
                        roomRepository.save(room);
                    }

                    return ResponseEntity.ok(savedMember);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 방장만 최종 완료
    @PostMapping("/{memberId}/complete")
    public ResponseEntity<Room> completeRoom(
            @PathVariable Long memberId) {

        return roomMemberRepository.findById(memberId)
                .map(member -> {

                    Room room = member.getRoom();

                    // 방장만 가능
                    if (!member.isHost()) {
                        return ResponseEntity.badRequest()
                                .<Room>build();
                    }

                    // 정산완료 상태에서만 최종 완료 가능
                    if (!"SETTLEMENT_COMPLETE"
                            .equals(room.getStatus())) {

                        return ResponseEntity.badRequest()
                                .<Room>build();
                    }

                    room.setStatus("COMPLETED");

                    Room savedRoom =
                            roomRepository.save(room);

                    return ResponseEntity.ok(savedRoom);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}