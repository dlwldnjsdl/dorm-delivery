package com.example.dormdelivery.room;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String menu;

    private int maxPeople;

    private int currentPeople;

    private String deliveryPlace;

    private LocalDateTime orderTime;

    private String status;

    // 방을 만든 사용자의 기숙사
    private String dormitory;
}