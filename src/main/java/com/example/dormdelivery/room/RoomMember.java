package com.example.dormdelivery.room;

import com.example.dormdelivery.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "room_members")
@Getter
@Setter
@NoArgsConstructor
public class RoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 참여한 방
    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;

    // 실제 회원
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // 기존 데이터 호환용 - 나중에 제거 예정
    private String memberName;

    private boolean ready;
    private boolean host;
    private boolean settled;
}