flowchart LR
    A["소셜 로그인 시도"]
    B["auth kakao start 로 받은 데이터로 user service 호출"]
    C{"계정 존재 여부"}
    D["Access Token 및 Refresh Token 발급"]
    E["Refresh Token 은 Redis 저장
HttpOnly Cookie 로 전달
Access Token 은 Authorization Header 로 전달"]
    F["신규 유저 생성 및 userId 반환"]

    A --> B
    B --> C
    C -->|계정 있음| D
    C -->|계정 없음| F
    F --> D
    D --> E




flowchart LR
    A["Access Token 만료"]
    B["auth token refresh 호출"]
    C{"Refresh Token 서명 및 만료 검증
Redis 존재 여부 확인"}
    D["Access Token 및 Refresh Token 재발급"]
    E["로그아웃 처리
Token 재발급 거부"]

    A --> B
    B --> C
    C -->|유효| D
    C -->|만료| E




flowchart LR
    A["사용자가 티켓팅 페이지 진입"]

    B{"멤버십 우대 티켓팅 여부"}
    C["멤버십 존재"]
    D["멤버십 없음"]
    E["우대 없음"]

    F["대기열 진입"]
    G["접근 거부 401 Unauthorized"]

    H["예약 페이지 도달"]
    I["결제 진행 지갑 구현 여부"]
    J["결제 성공"]
    K["결제 실패
트랜잭션 처리
예약 페이지 또는 대기열 재진입"]
    L["티켓팅 페이지로 추방"]

    A --> B

    B -->|우대| C
    B -->|우대| D
    B -->|비우대| E

    C --> F
    D --> G
    E --> F

    F -->|"대기열 좌석 선택 제공 또는 입장권 발급"| H

    H --> I
    I -->|"좌석 선택 성공"| J
    I -->|"좌석 예약 실패"| K

    H -->|"토큰 만료 또는 무응답"| L
