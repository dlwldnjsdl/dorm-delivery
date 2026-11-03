package com.example.dormdelivery.room;

import com.example.dormdelivery.user.User;
import com.example.dormdelivery.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rooms")
public class RoomController {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

    public RoomController(
            RoomRepository roomRepository,
            RoomMemberRepository roomMemberRepository,
            UserRepository userRepository
    ) {
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.userRepository = userRepository;
    }

    // =========================
    // 같은 기숙사 방 목록
    // =========================

    @GetMapping
    public ResponseEntity<?> getRooms(HttpSession session) {

        Long userId =
                (Long) session.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity
                    .status(401)
                    .body("로그인이 필요합니다.");
        }

        User user =
                userRepository
                        .findById(userId)
                        .orElse(null);

        if (user == null) {
            return ResponseEntity
                    .status(401)
                    .body("사용자를 찾을 수 없습니다.");
        }

        List<Room> rooms =
                roomRepository.findByDormitory(
                        user.getDormitory()
                );

        return ResponseEntity.ok(rooms);
    }

    // =========================
    // 방 상세
    // =========================

    @GetMapping("/{id}")
    public ResponseEntity<?> getRoom(
            @PathVariable Long id,
            HttpSession session
    ) {

        Long userId =
                (Long) session.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity
                    .status(401)
                    .body("로그인이 필요합니다.");
        }

        User user =
                userRepository
                        .findById(userId)
                        .orElse(null);

        Room room =
                roomRepository
                        .findById(id)
                        .orElse(null);

        if (user == null || room == null) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        // 다른 기숙사 방 접근 방지
        if (!user.getDormitory()
                .equals(room.getDormitory())) {

            return ResponseEntity
                    .status(403)
                    .body("다른 기숙사의 방에는 접근할 수 없습니다.");
        }

        return ResponseEntity.ok(room);
    }

    // =========================
    // 방 생성
    // =========================

    @PostMapping
    public ResponseEntity<?> createRoom(
            @RequestBody Room room,
            HttpSession session
    ) {

        Long userId =
                (Long) session.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity
                    .status(401)
                    .body("로그인이 필요합니다.");
        }

        User user =
                userRepository
                        .findById(userId)
                        .orElse(null);

        if (user == null) {
            return ResponseEntity
                    .status(401)
                    .body("사용자를 찾을 수 없습니다.");
        }

        // 기본 방 상태
        room.setCurrentPeople(1);
        room.setStatus("RECRUITING");

        // 핵심:
        // 프론트에서 기숙사를 보내는 게 아니라
        // 로그인한 사용자의 기숙사를 자동 적용
        room.setDormitory(
                user.getDormitory()
        );

        Room savedRoom =
                roomRepository.save(room);

        // 방 만든 사람 자동 참여
        RoomMember hostMember =
                new RoomMember();

        hostMember.setRoom(savedRoom);
        hostMember.setUser(user);
        hostMember.setMemberName(
                user.getName()
        );

        hostMember.setHost(true);
        hostMember.setReady(false);
        hostMember.setSettled(false);

        roomMemberRepository.save(
                hostMember
        );

        return ResponseEntity.ok(
                savedRoom
        );
    }

    // =========================
    // 방 삭제
    // =========================

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRoom(
            @PathVariable Long id,
            HttpSession session
    ) {

        Long userId =
                (Long) session.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity
                    .status(401)
                    .body("로그인이 필요합니다.");
        }

        Room room =
                roomRepository
                        .findById(id)
                        .orElse(null);

        if (room == null) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        if (!"RECRUITING"
                .equals(room.getStatus())) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            "모집중인 방만 삭제할 수 있습니다."
                    );
        }

        List<RoomMember> members =
                roomMemberRepository
                        .findByRoomId(id);

        RoomMember hostMember =
                members.stream()
                        .filter(member ->
                                member.getUser() != null
                                        &&
                                        member.getUser()
                                                .getId()
                                                .equals(userId)
                                        &&
                                        member.isHost()
                        )
                        .findFirst()
                        .orElse(null);

        if (hostMember == null) {
            return ResponseEntity
                    .status(403)
                    .body(
                            "방장만 방을 삭제할 수 있습니다."
                    );
        }

        roomMemberRepository.deleteAll(
                members
        );

        roomRepository.delete(room);

        return ResponseEntity.ok(
                "방이 삭제되었습니다."
        );
    }
}