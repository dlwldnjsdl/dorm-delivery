package com.example.dormdelivery.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 전화번호 중복 확인
    boolean existsByPhone(String phone);

    // 로그인할 때 전화번호로 회원 찾기
    Optional<User> findByPhone(String phone);
}