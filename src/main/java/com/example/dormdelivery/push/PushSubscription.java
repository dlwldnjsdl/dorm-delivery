package com.example.dormdelivery.push;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="push_subscriptions") @Getter @Setter
public class PushSubscription {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long userId;
    @Column(length=64) private String deviceHash;
    @Column(nullable=false, unique=true, length=64) private String endpointHash;
    @Column(nullable=false, length=2048) private String endpoint;
    @Column(nullable=false, length=128) private String p256dh;
    @Column(nullable=false, length=64) private String auth;
}
