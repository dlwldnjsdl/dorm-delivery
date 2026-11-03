package com.example.dormdelivery.user;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Set<String> ALLOWED_DORMITORIES =
            Set.of("웅비홀", "집현재", "진리관");

    public UserController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // =========================
    // 회원가입
    // =========================

    @PostMapping("/signup")
    public ResponseEntity<?> signup(
            @RequestBody User user
    ) {

        if (user.getName() == null ||
                user.getName().isBlank()) {

            return ResponseEntity
                    .badRequest()
                    .body("이름을 입력해주세요.");
        }

        if (user.getPhone() == null ||
                user.getPhone().isBlank()) {

            return ResponseEntity
                    .badRequest()
                    .body("전화번호를 입력해주세요.");
        }

        if (user.getPassword() == null ||
                user.getPassword().isBlank()) {

            return ResponseEntity
                    .badRequest()
                    .body("비밀번호를 입력해주세요.");
        }

        if (user.getDormitory() == null ||
                !ALLOWED_DORMITORIES.contains(
                        user.getDormitory()
                )) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            "기숙사는 웅비홀, 집현재, 진리관 중에서 선택해주세요."
                    );
        }

        if (userRepository.existsByPhone(
                user.getPhone()
        )) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            "이미 가입된 전화번호입니다."
                    );
        }

        user.setPassword(
                passwordEncoder.encode(
                        user.getPassword()
                )
        );

        User savedUser =
                userRepository.save(user);

        return ResponseEntity.ok(
                createUserResponse(savedUser)
        );
    }

    // =========================
    // 로그인
    // =========================

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody User request,
            HttpSession session
    ) {

        User user =
                userRepository
                        .findByPhone(
                                request.getPhone()
                        )
                        .orElse(null);

        if (user == null) {
            return ResponseEntity
                    .status(401)
                    .body(
                            "전화번호 또는 비밀번호가 올바르지 않습니다."
                    );
        }

        String savedPassword =
                user.getPassword();

        boolean passwordMatches;

        boolean isBcrypt =
                savedPassword != null &&
                        (
                                savedPassword.startsWith("$2a$") ||
                                        savedPassword.startsWith("$2b$") ||
                                        savedPassword.startsWith("$2y$")
                        );

        if (isBcrypt) {

            passwordMatches =
                    passwordEncoder.matches(
                            request.getPassword(),
                            savedPassword
                    );

        } else {

            passwordMatches =
                    savedPassword != null &&
                            savedPassword.equals(
                                    request.getPassword()
                            );

            // 예전 평문 비밀번호 자동 BCrypt 변환
            if (passwordMatches) {

                user.setPassword(
                        passwordEncoder.encode(
                                request.getPassword()
                        )
                );

                userRepository.save(user);
            }
        }

        if (!passwordMatches) {

            return ResponseEntity
                    .status(401)
                    .body(
                            "전화번호 또는 비밀번호가 올바르지 않습니다."
                    );
        }

        session.setAttribute(
                "userId",
                user.getId()
        );

        return ResponseEntity.ok(
                createUserResponse(user)
        );
    }

    // =========================
    // 현재 로그인 사용자
    // =========================

    @GetMapping("/me")
    public ResponseEntity<?> getMe(
            HttpSession session
    ) {

        Long userId =
                (Long) session.getAttribute(
                        "userId"
                );

        if (userId == null) {

            return ResponseEntity
                    .status(401)
                    .body(
                            "로그인이 필요합니다."
                    );
        }

        User user =
                userRepository
                        .findById(userId)
                        .orElse(null);

        if (user == null) {

            return ResponseEntity
                    .status(401)
                    .body(
                            "사용자를 찾을 수 없습니다."
                    );
        }

        return ResponseEntity.ok(
                createUserResponse(user)
        );
    }

    // =========================
    // 로그아웃
    // =========================

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpSession session
    ) {

        session.invalidate();

        return ResponseEntity.ok(
                "로그아웃되었습니다."
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestBody Map<String, String> request
    ) {

        String name = request.get("name");
        String phone = request.get("phone");
        String dormitory = request.get("dormitory");
        String newPassword = request.get("newPassword");

        if (name == null || name.isBlank() ||
                phone == null || phone.isBlank() ||
                dormitory == null || dormitory.isBlank() ||
                newPassword == null || newPassword.isBlank()) {

            return ResponseEntity
                    .badRequest()
                    .body("모든 정보를 입력해주세요.");
        }

        User user = userRepository
                .findByPhone(phone)
                .orElse(null);

        if (user == null ||
                !user.getName().equals(name) ||
                !user.getDormitory().equals(dormitory)) {

            return ResponseEntity
                    .badRequest()
                    .body("회원 정보를 확인해주세요.");
        }

        user.setPassword(
                passwordEncoder.encode(newPassword)
        );

        userRepository.save(user);

        return ResponseEntity.ok(
                "비밀번호가 변경되었습니다."
        );
    }

    // =========================
    // 사용자 조회
    // =========================

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(
            @PathVariable Long id
    ) {

        User user =
                userRepository
                        .findById(id)
                        .orElse(null);

        if (user == null) {

            return ResponseEntity
                    .notFound()
                    .build();
        }

        return ResponseEntity.ok(
                createUserResponse(user)
        );
    }

    // =========================
    // 비밀번호 제외 응답
    // =========================

    private Map<String, Object> createUserResponse(
            User user
    ) {

        return Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "phone", user.getPhone(),
                "dormitory", user.getDormitory()
        );
    }
}