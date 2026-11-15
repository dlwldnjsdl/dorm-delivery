package com.example.dormdelivery.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자 이름
    private String name;

    // 전화번호
    @Column(unique = true)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String phone;

    // 비밀번호
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    // 기숙사 정보
    private String dormitory;
}
