package com.example.dormdelivery.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomMemberRepository
        extends JpaRepository<RoomMember, Long> {

    List<RoomMember> findByRoomId(Long roomId);

    List<RoomMember> findByUser_Id(Long userId);

    boolean existsByRoom_IdAndUser_Id(
            Long roomId,
            Long userId
    );
}
