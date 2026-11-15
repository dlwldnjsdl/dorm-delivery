import { useEffect, useRef, useState } from "react";
import "./App.css";
import PushNotificationButton from './PushNotificationButton';
import { logoutWithPush, syncExistingPush } from './push';

const API = '';

function App() {
  // =========================
  // 로그인 / 회원가입
  // =========================

  const [authMode, setAuthMode] = useState("login");
  const [passwordResetAvailable, setPasswordResetAvailable] = useState(false);
  useEffect(() => {
    fetch('/users/capabilities').then(response => response.ok ? response.json() : {})
      .then(config => setPasswordResetAvailable(config.passwordResetAvailable === true))
      .catch(() => setPasswordResetAvailable(false));
  }, []);

  const [rememberPhone, setRememberPhone] = useState(
    () => localStorage.getItem("rememberedPhone") !== null
  );

  const [resetName, setResetName] = useState("");
  const [resetDormitory, setResetDormitory] = useState("");
  const [newPassword, setNewPassword] = useState("");

  const [phone, setPhone] = useState(
    () => localStorage.getItem("rememberedPhone") || ""
  );

  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");

  const [signupName, setSignupName] = useState("");
  const [signupDormitory, setSignupDormitory] = useState("");

  const [user, setUser] = useState(null);

  // =========================
  // 방
  // =========================

  const [rooms, setRooms] = useState([]);
  const [myRoomIds, setMyRoomIds] = useState([]);

  const [loadingRooms, setLoadingRooms] = useState(false);

  const [selectedRoom, setSelectedRoom] = useState(null);
  const [members, setMembers] = useState([]);
  const isSelectedRoomMember = members.some(member => member.room?.id === selectedRoom?.id && member.user?.id === user?.id);

  const [showCreateRoom, setShowCreateRoom] = useState(false);

  const [roomFilter, setRoomFilter] = useState("ALL");

  const [roomTitle, setRoomTitle] = useState("");
  const [menu, setMenu] = useState("");
  const [maxPeople, setMaxPeople] = useState(4);
  const [deliveryPlace, setDeliveryPlace] = useState("");
  const [orderTime, setOrderTime] = useState("");

  // =========================
  // 채팅
  // =========================

  const [socket, setSocket] = useState(null);
  const [chatConnected, setChatConnected] = useState(false);

  const [chats, setChats] = useState([]);
  const [chatMessage, setChatMessage] = useState("");

  const chatEndRef = useRef(null);

  // =========================
  // 최초 로그인 확인
  // =========================

  useEffect(() => {
    checkLogin();
  }, []);

  // =========================
  // 채팅 자동 스크롤
  // =========================

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({
      behavior: "smooth",
    });
  }, [chats]);

  // =========================
  // 메인 방 목록 자동 갱신
  // =========================

  useEffect(() => {
    if (!user || selectedRoom) {
      return;
    }

    const interval = setInterval(() => {
      loadRooms(false, user.id);
    }, 2000);

    return () => {
      clearInterval(interval);
    };
  }, [user?.id, selectedRoom?.id]);

  // =========================
  // 방 상세 자동 갱신
  // =========================

  useEffect(() => {
    if (!selectedRoom?.id) {
      return;
    }

    const roomId = selectedRoom.id;

    const interval = setInterval(() => {
      loadRoomDetail(roomId);
    }, 1000);

    return () => {
      clearInterval(interval);
    };
  }, [selectedRoom?.id]);

  // =========================
  // WebSocket 연결
  // =========================

  useEffect(() => {
    if (!selectedRoom?.id) {
      return;
    }

    const roomId = selectedRoom.id;

    if (!isSelectedRoomMember) return;
    loadChatHistory(roomId);
    const ws = new WebSocket(`${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`);

    ws.onopen = () => {
      const connectFrame =
        "CONNECT\n" +
        "accept-version:1.2\n" +
        `host:${window.location.host}\n` +
        "\n" +
        "\0";

      ws.send(connectFrame);
    };

    ws.onmessage = (event) => {
      const frames = event.data.split("\0");

      frames.forEach((frame) => {
        if (!frame.trim()) {
          return;
        }

        if (frame.startsWith("CONNECTED")) {
          setChatConnected(true);

          const subscribeFrame =
            "SUBSCRIBE\n" +
            `id:room-${roomId}\n` +
            `destination:/topic/rooms/${roomId}\n` +
            "\n" +
            "\0";

          ws.send(subscribeFrame);

          return;
        }

        if (frame.startsWith("MESSAGE")) {
          const bodyIndex = frame.indexOf("\n\n");

          if (bodyIndex === -1) {
            return;
          }

          const body = frame.substring(bodyIndex + 2);

          try {
            const chat = JSON.parse(body);

            setChats((prev) => [...prev, chat]);
          } catch (error) {
            console.log(error);
          }
        }
      });
    };

    ws.onerror = () => {
      setChatConnected(false);
    };

    ws.onclose = () => {
      setChatConnected(false);
    };

    setSocket(ws);

    return () => {
      ws.close();

      setChatConnected(false);
      setSocket(null);
    };
  }, [selectedRoom?.id, isSelectedRoomMember]);

  // =========================
  // 로그인 확인
  // =========================

  const checkLogin = async () => {
    try {
      const response = await fetch(`${API}/users/me`, {
        credentials: "include",
      });

      if (!response.ok) {
        return;
      }

      const currentUser = await response.json();

      setUser(currentUser);
      await syncExistingPush().catch(() => {});

      await loadRooms(true, currentUser.id);
      await openNotificationRoom();
    } catch (error) {
      console.log("로그인 세션 없음");
    }
  };

  // =========================
  // 로그인
  // =========================

  const login = async () => {
    if (!phone.trim() || !password.trim()) {
      setMessage("전화번호와 비밀번호를 입력해주세요.");
      return;
    }

    try {
      const response = await fetch(`${API}/users/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({
          phone,
          password,
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();

        setMessage(
          errorText || "전화번호 또는 비밀번호를 확인해주세요."
        );
        return;
      }

      const loginUser = await response.json();

      if (rememberPhone) {
        localStorage.setItem("rememberedPhone", phone);
      } else {
        localStorage.removeItem("rememberedPhone");
      }

      setUser(loginUser);
      await syncExistingPush().catch(() => {});
      setMessage("");

      await loadRooms(true, loginUser.id);
      await openNotificationRoom();
    } catch (error) {
      console.log(error);
      setMessage("서버에 연결할 수 없습니다.");
    }
  };

  // =========================
  // 회원가입
  // =========================

  const signup = async () => {
    if (
      !signupName.trim() ||
      !signupDormitory ||
      !phone.trim() ||
      !password.trim()
    ) {
      setMessage("모든 정보를 입력해주세요.");
      return;
    }

    try {
      const response = await fetch(`${API}/users/signup`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({
          name: signupName,
          phone,
          password,
          dormitory: signupDormitory,
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();

        setMessage(
          errorText || "회원가입에 실패했습니다."
        );
        return;
      }

      setSignupName("");
      setSignupDormitory("");
      setPassword("");

      setAuthMode("login");
      setMessage("회원가입 완료! 로그인해주세요.");
    } catch (error) {
      console.log(error);
      setMessage("서버에 연결할 수 없습니다.");
    }
  };

  // =========================
  // 비밀번호 재설정
  // =========================

  const resetPassword = async () => {
    if (
      !resetName.trim() ||
      !resetDormitory ||
      !phone.trim() ||
      !newPassword.trim()
    ) {
      setMessage("모든 정보를 입력해주세요.");
      return;
    }

    try {
      const response = await fetch(`${API}/users/reset-password`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          name: resetName,
          phone,
          dormitory: resetDormitory,
          newPassword,
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();

        setMessage(
          errorText || "비밀번호 변경에 실패했습니다."
        );
        return;
      }

      setPassword("");
      setNewPassword("");
      setResetName("");
      setResetDormitory("");

      setAuthMode("login");
      setMessage(
        "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요."
      );
    } catch (error) {
      console.log(error);
      setMessage("서버에 연결할 수 없습니다.");
    }
  };

  // =========================
  // 로그아웃
  // =========================

  const logout = async () => {
    try {
      await logoutWithPush();
    } catch (error) {
      alert(error.message);
      return;
    }

    setUser(null);

    setRooms([]);
    setMyRoomIds([]);

    setSelectedRoom(null);
    setMembers([]);
    setChats([]);

    setPhone(localStorage.getItem("rememberedPhone") || "");
    setPassword("");
    setMessage("");

    setRoomFilter("ALL");
    setAuthMode("login");
  };

  // =========================
  // 방 목록 + 내 참여 방 조회
  // =========================

  const loadRooms = async (
    showLoading = true,
    userIdOverride = null
  ) => {
    if (showLoading) {
      setLoadingRooms(true);
    }

    try {
      const roomResponse = await fetch(
        `${API}/rooms`,
        {
          credentials: "include",
        }
      );

      if (!roomResponse.ok) {
        return;
      }

      let roomList = await roomResponse.json();

      const statusOrder = {
        RECRUITING: 1,
        RECRUITMENT_COMPLETE: 2,
        ORDERING: 3,
        SETTLEMENT_COMPLETE: 4,
        COMPLETED: 5,
      };

      roomList.sort((a, b) => {
        const statusDifference =
          (statusOrder[a.status] || 99) -
          (statusOrder[b.status] || 99);

        if (statusDifference !== 0) {
          return statusDifference;
        }

        return b.id - a.id;
      });

      setRooms(roomList);

      const currentUserId =
        userIdOverride || user?.id;

      if (currentUserId) {
        const memberResponse = await fetch(
          `${API}/room-members/user/${currentUserId}`,
          {
            credentials: "include",
          }
        );

        if (memberResponse.ok) {
          const myMembers =
            await memberResponse.json();

          const ids = myMembers.map(
            (member) => member.room.id
          );

          setMyRoomIds(ids);
        }
      }
    } catch (error) {
      console.log(error);
    } finally {
      if (showLoading) {
        setLoadingRooms(false);
      }
    }
  };

  // =========================
  // 방 만들기
  // =========================

  const createRoom = async () => {
    if (
      !roomTitle.trim() ||
      !menu.trim() ||
      !deliveryPlace.trim() ||
      !maxPeople
    ) {
      alert("방 정보를 모두 입력해주세요.");
      return;
    }

    if (Number(maxPeople) < 2) {
      alert("최대 인원은 2명 이상이어야 합니다.");
      return;
    }

    try {
      const response = await fetch(`${API}/rooms`, {
        method: "POST",

        headers: {
          "Content-Type": "application/json",
        },

        credentials: "include",

        body: JSON.stringify({
          title: roomTitle,
          menu,
          maxPeople: Number(maxPeople),
          deliveryPlace,
          orderTime: orderTime || null,
        }),
      });

      if (!response.ok) {
        alert("방 만들기에 실패했습니다.");
        return;
      }

      const newRoom = await response.json();

      setRoomTitle("");
      setMenu("");
      setMaxPeople(4);
      setDeliveryPlace("");
      setOrderTime("");

      setShowCreateRoom(false);

      await loadRooms(false, user.id);

      await openRoom(newRoom);
    } catch (error) {
      console.log(error);

      alert("서버에 연결할 수 없습니다.");
    }
  };

  // =========================
  // 방 들어가기
  // =========================

  const openNotificationRoom = async () => {
    const roomId = new URLSearchParams(window.location.search).get('room');
    if (!/^\d+$/.test(roomId || '')) return;
    const response = await fetch(`${API}/rooms/${roomId}`, { credentials: 'include' });
    if (response.ok) {
      await openRoom(await response.json());
      window.history.replaceState({}, '', window.location.pathname);
    }
  };

  const openRoom = async (room) => {
    setSelectedRoom(room);

    await loadRoomDetail(room.id);
  };

  // =========================
  // 방 상세 조회
  // =========================

  const loadRoomDetail = async (roomId) => {
    try {
      const roomResponse = await fetch(
        `${API}/rooms/${roomId}`,
        {
          credentials: "include",
        }
      );

      if (roomResponse.ok) {
        const room = await roomResponse.json();

        setSelectedRoom(room);
      }

      const memberResponse = await fetch(
        `${API}/room-members/room/${roomId}`,
        {
          credentials: "include",
        }
      );

      if (memberResponse.ok) {
        const memberList =
          await memberResponse.json();

        setMembers(memberList);
      }
    } catch (error) {
      console.log(error);
    }
  };

  // =========================
  // 방 목록으로
  // =========================

  const goBack = async () => {
    setSelectedRoom(null);
    setMembers([]);
    setChats([]);

    await loadRooms(false, user.id);
  };

  // =========================
  // 방 참여
  // =========================

  const joinRoom = async () => {
    if (!selectedRoom || !user) {
      return;
    }

    try {
      const response = await fetch(
        `${API}/room-members/room/${selectedRoom.id}/user/${user.id}`,
        {
          method: "POST",
          credentials: "include",
        }
      );

      if (!response.ok) {
        alert("방에 참여할 수 없습니다.");
        return;
      }

      await loadRoomDetail(selectedRoom.id);
      await loadRooms(false, user.id);
    } catch (error) {
      console.log(error);

      alert("서버에 연결할 수 없습니다.");
    }
  };

  // =========================
  // 방 나가기
  // =========================

  const leaveRoom = async (memberId) => {
    if (!selectedRoom) {
      return;
    }

    const ok = window.confirm(
      "정말 방에서 나갈까요?"
    );

    if (!ok) {
      return;
    }

    try {
      const response = await fetch(
        `${API}/room-members/${memberId}`,
        {
          method: "DELETE",
          credentials: "include",
        }
      );

      if (!response.ok) {
        alert("지금은 방에서 나갈 수 없습니다.");
        return;
      }

      await loadRoomDetail(selectedRoom.id);
      await loadRooms(false, user.id);
    } catch (error) {
      console.log(error);
    }
  };

  // =========================
  // 방 삭제
  // =========================

  const deleteRoom = async () => {
    if (!selectedRoom) {
      return;
    }

    const ok = window.confirm(
      "정말 이 방을 삭제할까요?\n삭제하면 되돌릴 수 없습니다."
    );

    if (!ok) {
      return;
    }

    try {
      const response = await fetch(
        `${API}/rooms/${selectedRoom.id}`,
        {
          method: "DELETE",
          credentials: "include",
        }
      );

      if (!response.ok) {
        const errorText =
          await response.text();

        alert(
          errorText ||
            "방을 삭제할 수 없습니다."
        );

        return;
      }

      alert("방이 삭제되었습니다.");

      setSelectedRoom(null);
      setMembers([]);
      setChats([]);

      await loadRooms(false, user.id);
    } catch (error) {
      console.log(error);

      alert("서버에 연결할 수 없습니다.");
    }
  };

  // =========================
  // READY
  // =========================

  const toggleReady = async (memberId) => {
    if (!selectedRoom) {
      return;
    }

    try {
      const response = await fetch(
        `${API}/room-members/${memberId}/ready`,
        {
          method: "POST",
          credentials: "include",
        }
      );

      if (!response.ok) {
        alert("READY 상태를 변경할 수 없습니다.");
        return;
      }

      await loadRoomDetail(selectedRoom.id);
    } catch (error) {
      console.log(error);
    }
  };

  // =========================
  // 주문 시작
  // =========================

  const startOrder = async (memberId) => {
    if (!selectedRoom) {
      return;
    }

    const ok = window.confirm(
      "모든 인원이 준비되었습니다. 주문을 시작할까요?"
    );

    if (!ok) {
      return;
    }

    try {
      const response = await fetch(
        `${API}/room-members/${memberId}/start-order`,
        {
          method: "POST",
          credentials: "include",
        }
      );

      if (!response.ok) {
        alert("아직 주문을 시작할 수 없습니다.");
        return;
      }

      await loadRoomDetail(selectedRoom.id);
    } catch (error) {
      console.log(error);
    }
  };

  // =========================
  // 채팅 기록
  // =========================

  const loadChatHistory = async (roomId) => {
    try {
      const response = await fetch(
        `${API}/chats/room/${roomId}`,
        {
          credentials: "include",
        }
      );

      if (!response.ok) {
        setChats([]);
        return;
      }

      const history = await response.json();

      setChats(history);
    } catch (error) {
      console.log(error);
    }
  };

  // =========================
  // 채팅 전송
  // =========================

  const sendChat = () => {
    if (!chatMessage.trim()) {
      return;
    }

    if (
      !socket ||
      socket.readyState !== WebSocket.OPEN
    ) {
      alert("채팅 서버에 연결 중입니다.");
      return;
    }

    const body = JSON.stringify({
      senderId: user.id,
      message: chatMessage.trim(),
    });

    const sendFrame =
      "SEND\n" +
      `destination:/app/chat/${selectedRoom.id}\n` +
      "content-type:application/json\n" +
      "\n" +
      body +
      "\0";

    socket.send(sendFrame);

    setChatMessage("");
  };

  // =========================
  // 정산
  // =========================

  const toggleSettlement = async (memberId) => {
    if (!selectedRoom) {
      return;
    }

    try {
      const response = await fetch(
        `${API}/room-members/${memberId}/settle`,
        {
          method: "POST",
          credentials: "include",
        }
      );

      if (!response.ok) {
        alert("정산 상태를 변경할 수 없습니다.");
        return;
      }

      await loadRoomDetail(selectedRoom.id);
    } catch (error) {
      console.log(error);
    }
  };

  // =========================
  // 최종 완료
  // =========================

  const completeRoom = async (memberId) => {
    const ok = window.confirm(
      "공동배달을 최종 완료할까요?"
    );

    if (!ok) {
      return;
    }

    try {
      const response = await fetch(
        `${API}/room-members/${memberId}/complete`,
        {
          method: "POST",
          credentials: "include",
        }
      );

      if (!response.ok) {
        alert("아직 완료할 수 없습니다.");
        return;
      }

      await loadRoomDetail(selectedRoom.id);
    } catch (error) {
      console.log(error);
    }
  };

  // =========================
  // 상태 한글
  // =========================

  const getStatusText = (status) => {
    if (status === "RECRUITING") {
      return "모집중";
    }

    if (status === "RECRUITMENT_COMPLETE") {
      return "모집완료";
    }

    if (status === "ORDERING") {
      return "주문중";
    }

    if (status === "SETTLEMENT_COMPLETE") {
      return "정산완료";
    }

    if (status === "COMPLETED") {
      return "완료";
    }

    return status;
  };

  // =========================
  // 방 필터링
  // =========================

  const filteredRooms = rooms.filter((room) => {
    if (roomFilter === "RECRUITING") {
      return room.status === "RECRUITING";
    }

    if (roomFilter === "JOINED") {
      return myRoomIds.includes(room.id);
    }

    return true;
  });

  // =========================
  // 로그인 / 회원가입 화면
  // =========================

  if (!user) {
    return (
      <div className="login-page">
        <div className="login-box">

          <div className="brand">
            <div className="dku-badge">
              <span className="bear">🐻</span>
              <span className="dku-text">DKU</span>
            </div>

            <h1>단짝</h1>

            <span className="brand-en">
              DAN-JJAK
            </span>

            <p>
              단국대 기숙사 공동배달 매칭
            </p>

            <span className="campus-notice">
              단국대학교 죽전캠퍼스 생활관 전용
            </span>
          </div>


          {authMode !== "reset" && (
            <div className="auth-tabs">

              <button
                type="button"
                className={
                  authMode === "login"
                    ? "auth-tab active"
                    : "auth-tab"
                }
                onClick={() => {
                  setAuthMode("login");
                  setMessage("");
                }}
              >
                로그인
              </button>

              <button
                type="button"
                className={
                  authMode === "signup"
                    ? "auth-tab active"
                    : "auth-tab"
                }
                onClick={() => {
                  setAuthMode("signup");
                  setMessage("");
                }}
              >
                회원가입
              </button>

            </div>
          )}


          {authMode === "signup" && (
            <div className="input-group">

              <input
                type="text"
                placeholder="이름"
                value={signupName}
                onChange={(e) =>
                  setSignupName(e.target.value)
                }
              />

              <select
                value={signupDormitory}
                onChange={(e) =>
                  setSignupDormitory(e.target.value)
                }
              >
                <option value="" disabled>
                  기숙사를 선택해주세요
                </option>

                <option value="웅비홀">
                  웅비홀
                </option>

                <option value="집현재">
                  집현재
                </option>

                <option value="진리관">
                  진리관
                </option>
              </select>

            </div>
          )}


          {authMode !== "reset" && (
            <>
              <div className="input-group">

                <input
                  type="text"
                  placeholder="전화번호"
                  value={phone}
                  onChange={(e) =>
                    setPhone(e.target.value)
                  }
                />

                <input
                  type="password"
                  placeholder="비밀번호"
                  value={password}
                  onChange={(e) =>
                    setPassword(e.target.value)
                  }
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      if (authMode === "login") {
                        login();
                      } else {
                        signup();
                      }
                    }
                  }}
                />

              </div>


              {authMode === "login" && (
                <div className="login-options">

                  <label className="remember-login">

                    <input
                      type="checkbox"
                      checked={rememberPhone}
                      onChange={(e) =>
                        setRememberPhone(
                          e.target.checked
                        )
                      }
                    />

                    <span>
                      로그인 정보 저장
                    </span>

                  </label>

                  {passwordResetAvailable && <button
                    type="button"
                    className="forgot-password-button"
                    onClick={() => {
                      setAuthMode("reset");
                      setMessage("");
                    }}
                  >
                    비밀번호를 잊으셨나요?
                  </button>}

                </div>
              )}


              {authMode === "login" ? (
                <button
                  type="button"
                  className="login-button"
                  onClick={login}
                >
                  로그인
                </button>
              ) : (
                <button
                  type="button"
                  className="login-button"
                  onClick={signup}
                >
                  단짝 시작하기
                </button>
              )}
            </>
          )}


          {authMode === "reset" && (
            <div className="reset-area">

              <h3>
                비밀번호 재설정
              </h3>

              <p>
                가입할 때 입력한 정보를 확인해주세요.
              </p>

              <div className="input-group">

                <input
                  type="text"
                  placeholder="이름"
                  value={resetName}
                  onChange={(e) =>
                    setResetName(e.target.value)
                  }
                />

                <select
                  value={resetDormitory}
                  onChange={(e) =>
                    setResetDormitory(
                      e.target.value
                    )
                  }
                >
                  <option value="" disabled>
                    기숙사를 선택해주세요
                  </option>

                  <option value="웅비홀">
                    웅비홀
                  </option>

                  <option value="집현재">
                    집현재
                  </option>

                  <option value="진리관">
                    진리관
                  </option>
                </select>

                <input
                  type="text"
                  placeholder="전화번호"
                  value={phone}
                  onChange={(e) =>
                    setPhone(e.target.value)
                  }
                />

                <input
                  type="password"
                  placeholder="새 비밀번호"
                  value={newPassword}
                  onChange={(e) =>
                    setNewPassword(e.target.value)
                  }
                />

              </div>

              <button
                type="button"
                className="login-button"
                onClick={resetPassword}
              >
                비밀번호 변경
              </button>

              <button
                type="button"
                className="back-login-button"
                onClick={() => {
                  setAuthMode("login");
                  setMessage("");
                }}
              >
                ← 로그인으로 돌아가기
              </button>

            </div>
          )}


          {message && (
            <p className="message">
              {message}
            </p>
          )}

        </div>
      </div>
    );
  }



  // =========================
  // 방 상세
  // =========================

  if (selectedRoom) {
    const myMember = members.find(
      (member) =>
        member.user?.id === user.id
    );

    const isHost =
      myMember?.host === true;

    const canJoin =
      !myMember &&
      selectedRoom.status === "RECRUITING" &&
      selectedRoom.currentPeople <
        selectedRoom.maxPeople;

    const canReady =
      myMember &&
      (
        selectedRoom.status === "RECRUITING" ||
        selectedRoom.status ===
          "RECRUITMENT_COMPLETE"
      );

    return (
      <div className="main-page">

        <header className="top-header">

          <div className="header-inner">

            <div className="header-brand">

              <div className="small-bear">
                🐻
              </div>

              <div>
                <h1>단짝</h1>
                <span>DAN-JJAK</span>
              </div>

            </div>


            <div className="user-area">
              <PushNotificationButton />

              <div className="user-info">

                <strong>
                  {user.name}
                </strong>

                <span>
                  {user.dormitory ||
                    "기숙사 미설정"}
                </span>

              </div>

              <button
                className="logout-button"
                onClick={logout}
              >
                로그아웃
              </button>

            </div>

          </div>

        </header>


        <main className="detail-content">

          <div className="detail-top-buttons">

            <button
              className="back-button"
              onClick={goBack}
            >
              ← 방 목록
            </button>


            {isHost &&
              selectedRoom.status ===
                "RECRUITING" && (

              <button
                className="delete-room-button"
                onClick={deleteRoom}
              >
                방 삭제
              </button>

            )}

          </div>


          <div className="room-detail-card">

            <div className="detail-status-row">

              <span
                className={
                  "status-badge " +
                  selectedRoom.status
                }
              >
                {getStatusText(
                  selectedRoom.status
                )}
              </span>

              <span className="detail-people">
                👤 {selectedRoom.currentPeople}
                /{selectedRoom.maxPeople}
              </span>

            </div>


            <h2>
              {selectedRoom.title}
            </h2>


            <div className="detail-info-grid">

              <div className="detail-info-box">
                <span>🍗</span>

                <div>
                  <small>메뉴</small>

                  <strong>
                    {selectedRoom.menu ||
                      "메뉴 미정"}
                  </strong>
                </div>
              </div>


              <div className="detail-info-box">
                <span>📍</span>

                <div>
                  <small>배달 장소</small>

                  <strong>
                    {selectedRoom.deliveryPlace ||
                      "장소 미정"}
                  </strong>
                </div>
              </div>


              <div className="detail-info-box">
                <span>⏰</span>

                <div>
                  <small>주문 시간</small>

                  <strong>
                    {selectedRoom.orderTime
                      ? selectedRoom.orderTime
                          .replace("T", " ")
                          .substring(0, 16)
                      : "미정"}
                  </strong>
                </div>
              </div>

            </div>


            {canJoin && (
              <button
                className="join-button"
                onClick={joinRoom}
              >
                이 방 참여하기
              </button>
            )}

          </div>


          <section className="member-section">

            <div className="member-section-title">

              <div>
                <h3>참여자</h3>

                <p>
                  모두 READY하면 주문을 시작할 수 있어요.
                </p>
              </div>

              <strong>
                {selectedRoom.currentPeople}명
              </strong>

            </div>


            <div className="member-list">

              {members.map((member) => (

                <div
                  className="member-card"
                  key={member.id}
                >

                  <div className="member-left">

                    <div className="member-avatar">
                      🐻
                    </div>


                    <div className="member-name">

                      <div>

                        <strong>
                          {member.user?.name ||
                            member.memberName ||
                            "사용자"}
                        </strong>

                        {member.host && (
                          <span className="host-badge">
                            방장
                          </span>
                        )}

                        {member.user?.id === user.id && (
                          <span className="me-badge">
                            나
                          </span>
                        )}

                      </div>


                      <small>

                        {selectedRoom.status === "ORDERING"
                          ? member.settled
                            ? "정산 완료"
                            : "정산 대기"
                          : selectedRoom.status ===
                              "SETTLEMENT_COMPLETE"
                            ? "정산 완료"
                            : member.ready
                              ? "주문 준비 완료"
                              : "준비 중"}

                      </small>

                    </div>

                  </div>


                  {(
                    selectedRoom.status === "ORDERING" ||
                    selectedRoom.status ===
                      "SETTLEMENT_COMPLETE"
                  ) ? (

                    <span
                      className={
                        member.settled
                          ? "settle-state settled"
                          : "settle-state"
                      }
                    >
                      {member.settled
                        ? "정산완료"
                        : "정산대기"}
                    </span>

                  ) : (

                    <span
                      className={
                        member.ready
                          ? "ready-state ready"
                          : "ready-state"
                      }
                    >
                      {member.ready
                        ? "READY"
                        : "대기"}
                    </span>

                  )}

                </div>

              ))}

            </div>


            {myMember && canReady && (

              <div className="detail-actions">

                <button
                  className={
                    myMember.ready
                      ? "ready-button active"
                      : "ready-button"
                  }
                  onClick={() =>
                    toggleReady(myMember.id)
                  }
                >
                  {myMember.ready
                    ? "✓ READY 완료"
                    : "READY"}
                </button>


                {isHost &&
                  selectedRoom.status ===
                    "RECRUITMENT_COMPLETE" && (

                  <button
                    className="start-order-button"
                    onClick={() =>
                      startOrder(myMember.id)
                    }
                  >
                    주문 시작하기
                  </button>

                )}


                {!isHost &&
                  selectedRoom.status ===
                    "RECRUITING" && (

                  <button
                    className="leave-button"
                    onClick={() =>
                      leaveRoom(myMember.id)
                    }
                  >
                    방 나가기
                  </button>

                )}

              </div>

            )}

          </section>


          {(
            selectedRoom.status === "ORDERING" ||
            selectedRoom.status ===
              "SETTLEMENT_COMPLETE" ||
            selectedRoom.status === "COMPLETED"
          ) && (

            <section className="chat-section">

              <div className="chat-header">

                <div>
                  <h3>
                    💬 실시간 채팅
                  </h3>

                  <p>
                    주문 상황을 같이 공유해보세요.
                  </p>
                </div>

                <span
                  className={
                    chatConnected
                      ? "chat-status connected"
                      : "chat-status"
                  }
                >
                  {chatConnected
                    ? "● 연결됨"
                    : "● 연결중"}
                </span>

              </div>


              <div className="chat-box">

                {chats.length === 0 ? (

                  <div className="no-chat">
                    아직 메시지가 없어요.
                    <br />
                    첫 메시지를 보내보세요 👋
                  </div>

                ) : (

                  chats.map((chat) => {
                    const isMine =
                      chat.senderId === user.id;

                    return (
                      <div
                        key={
                          chat.id ||
                          `${chat.sentAt}-${chat.message}`
                        }
                        className={
                          isMine
                            ? "chat-row mine"
                            : "chat-row"
                        }
                      >

                        {!isMine && (
                          <div className="chat-sender">
                            {chat.senderName}
                          </div>
                        )}

                        <div className="chat-bubble">
                          {chat.message}
                        </div>

                        <div className="chat-time">

                          {chat.sentAt
                            ? new Date(
                                chat.sentAt
                              ).toLocaleTimeString(
                                "ko-KR",
                                {
                                  hour: "2-digit",
                                  minute: "2-digit",
                                }
                              )
                            : ""}

                        </div>

                      </div>
                    );
                  })

                )}

                <div ref={chatEndRef} />

              </div>


              {selectedRoom.status ===
                "ORDERING" && (

                <div className="chat-input-area">

                  <input
                    type="text"
                    placeholder="메시지를 입력하세요"
                    value={chatMessage}
                    onChange={(e) =>
                      setChatMessage(
                        e.target.value
                      )
                    }
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        sendChat();
                      }
                    }}
                  />

                  <button onClick={sendChat}>
                    전송
                  </button>

                </div>

              )}

            </section>

          )}


          {myMember &&
            selectedRoom.status === "ORDERING" && (

            <section className="settlement-section">

              <div className="settlement-title">

                <h3>💰 정산</h3>

                <p>
                  송금이 끝났다면 정산 완료를 눌러주세요.
                </p>

              </div>


              <button
                className={
                  myMember.settled
                    ? "settlement-button done"
                    : "settlement-button"
                }
                onClick={() =>
                  toggleSettlement(myMember.id)
                }
              >
                {myMember.settled
                  ? "✓ 내 정산 완료"
                  : "정산 완료"}
              </button>

            </section>

          )}


          {selectedRoom.status ===
            "SETTLEMENT_COMPLETE" && (

            <section className="complete-section">

              <div className="complete-icon">
                🎉
              </div>

              <h3>
                모든 참여자의 정산이 완료됐어요!
              </h3>

              <p>
                이제 공동배달을 마무리할 수 있어요.
              </p>


              {isHost && (

                <button
                  className="final-complete-button"
                  onClick={() =>
                    completeRoom(myMember.id)
                  }
                >
                  공동배달 완료하기
                </button>

              )}

            </section>

          )}


          {selectedRoom.status === "COMPLETED" && (

            <section className="finished-section">

              <div className="finished-icon">
                🐻💙
              </div>

              <h3>
                공동배달 완료!
              </h3>

              <p>
                단짝에서 맛있는 한 끼를 함께했어요.
              </p>

              <button onClick={goBack}>
                방 목록으로 돌아가기
              </button>

            </section>

          )}

        </main>

      </div>
    );
  }

  // =========================
  // 메인 화면
  // =========================

  return (
    <div className="main-page">

      <header className="top-header">

        <div className="header-inner">

          <div className="header-brand">

            <div className="small-bear">
              🐻
            </div>

            <div>
              <h1>단짝</h1>
              <span>DAN-JJAK</span>
            </div>

          </div>


          <div className="user-area">
            <PushNotificationButton />

            <div className="user-info">

              <strong>
                {user.name}
              </strong>

              <span>
                {user.dormitory ||
                  "기숙사 미설정"}
              </span>

            </div>

            <button
              className="logout-button"
              onClick={logout}
            >
              로그아웃
            </button>

          </div>

        </div>

      </header>


      <main className="main-content">

        <section className="welcome-section">

          <div className="welcome-campus">
            🏫 단국대학교 죽전캠퍼스
          </div>

          <h2>
            안녕하세요, <strong>{user.name}</strong>님 👋
          </h2>

          <p className="welcome-main-text">
            <span>{user.dormitory}</span>에서
            같이 배달시킬 사람을 찾아보세요.
          </p>

          <p className="welcome-description">
            같은 생활관 학생들과 배달비를 나누고 함께 주문할 수 있어요.
          </p>

        </section>


        <section className="room-section">

          <div className="room-section-header">

            <div>

              <h3>
                공동배달 방
              </h3>

              <p>
                {user.dormitory}에서 모집 중인 공동배달을 확인해보세요.
              </p>

            </div>

            <button
              className="refresh-button"
              onClick={() =>
                loadRooms(true, user.id)
              }
            >
              새로고침
            </button>

          </div>


          <div className="room-filter-bar">

            <button
              className={
                roomFilter === "ALL"
                  ? "room-filter-button active"
                  : "room-filter-button"
              }
              onClick={() =>
                setRoomFilter("ALL")
              }
            >
              전체
              <span>{rooms.length}</span>
            </button>

            <button
              className={
                roomFilter === "RECRUITING"
                  ? "room-filter-button active"
                  : "room-filter-button"
              }
              onClick={() =>
                setRoomFilter("RECRUITING")
              }
            >
              모집중
              <span>
                {
                  rooms.filter(
                    (room) =>
                      room.status === "RECRUITING"
                  ).length
                }
              </span>
            </button>

            <button
              className={
                roomFilter === "JOINED"
                  ? "room-filter-button active"
                  : "room-filter-button"
              }
              onClick={() =>
                setRoomFilter("JOINED")
              }
            >
              참여중
              <span>{myRoomIds.length}</span>
            </button>

          </div>


          {loadingRooms ? (

            <div className="empty-box">
              방 목록을 불러오는 중...
            </div>

          ) : filteredRooms.length === 0 ? (

            <div className="empty-box">

              <div className="empty-icon">
                🍽️
              </div>

              <h4>
                {roomFilter === "RECRUITING"
                  ? "현재 모집 중인 방이 없어요."
                  : roomFilter === "JOINED"
                    ? "현재 참여 중인 방이 없어요."
                    : "아직 만들어진 방이 없어요."}
              </h4>

              <p>
                {roomFilter === "ALL"
                  ? "첫 번째 공동배달 방을 만들어보세요!"
                  : "다른 방을 확인해보세요."}
              </p>

            </div>

          ) : (

            <div className="room-grid">

              {filteredRooms.map((room) => (

                <div
                  className={
                    room.status === "COMPLETED"
                      ? "room-card completed-room"
                      : "room-card"
                  }
                  key={room.id}
                >

                  <div className="room-card-top">

                    <div className="room-badges">

                      <span
                        className={
                          "status-badge " +
                          room.status
                        }
                      >
                        {getStatusText(
                          room.status
                        )}
                      </span>


                      {myRoomIds.includes(room.id) && (

                        <span className="joined-badge">
                          참여중
                        </span>

                      )}

                    </div>


                    <span className="people-count">
                      👤 {room.currentPeople}
                      /{room.maxPeople}
                    </span>

                  </div>


                  <h4>
                    {room.title ||
                      "제목 없는 방"}
                  </h4>


                  <div className="room-info">

                    <p>
                      <span>🍗</span>
                      {room.menu ||
                        "메뉴 미정"}
                    </p>

                    <p>
                      <span>📍</span>
                      {room.deliveryPlace ||
                        "장소 미정"}
                    </p>

                  </div>


                  <button
                    className="enter-room-button"
                    onClick={() =>
                      openRoom(room)
                    }
                  >
                    방 들어가기
                  </button>

                </div>

              ))}

            </div>

          )}

        </section>


        <button
          className="create-room-button"
          onClick={() =>
            setShowCreateRoom(true)
          }
        >
          <span>＋</span>
          방 만들기
        </button>

      </main>


      {showCreateRoom && (

        <div className="modal-background">

          <div className="create-room-modal">

            <div className="modal-header">

              <div>

                <h2>
                  새 공동배달 방
                </h2>

                <p>
                  {user.dormitory}에서 같이 주문할 사람을 모집합니다.
                </p>

              </div>


              <button
                className="close-button"
                onClick={() =>
                  setShowCreateRoom(false)
                }
              >
                ×
              </button>

            </div>


            <div className="create-form">

              <label>방 제목</label>

              <input
                type="text"
                placeholder="예) 야식 치킨 같이 먹어요"
                value={roomTitle}
                onChange={(e) =>
                  setRoomTitle(
                    e.target.value
                  )
                }
              />


              <label>메뉴</label>

              <input
                type="text"
                placeholder="예) 치킨"
                value={menu}
                onChange={(e) =>
                  setMenu(
                    e.target.value
                  )
                }
              />


              <label>최대 인원</label>

              <input
                type="number"
                min="2"
                max="10"
                value={maxPeople}
                onChange={(e) =>
                  setMaxPeople(
                    e.target.value
                  )
                }
              />


              <label>배달 장소</label>

              <input
                type="text"
                placeholder={`예) ${user.dormitory} 1층 로비`}
                value={deliveryPlace}
                onChange={(e) =>
                  setDeliveryPlace(
                    e.target.value
                  )
                }
              />


              <label>주문 시간</label>

              <input
                type="datetime-local"
                value={orderTime}
                onChange={(e) =>
                  setOrderTime(
                    e.target.value
                  )
                }
              />

            </div>


            <div className="modal-buttons">

              <button
                className="cancel-button"
                onClick={() =>
                  setShowCreateRoom(false)
                }
              >
                취소
              </button>

              <button
                className="confirm-create-button"
                onClick={createRoom}
              >
                방 만들기
              </button>

            </div>

          </div>

        </div>

      )}

    </div>
  );
}

export default App;
