1. MSA 치명적 문제: 서비스 간 동기 호출 금지
문제점
text
❌ 현재 구조:
Booking Service → (HTTP 동기) → Ticket Service
                → (HTTP 동기) → Auth Service
                → (HTTP 동기) → Payment Service

폭주 시: 모든 서비스 동시 과부하 → 연쇄 장애
해결: CQRS + Event Sourcing
typescript
// 티켓 오픈 5분 전: 모든 필요 데이터를 Redis에 사전 복제
await preWarmCache({
  inventory: Redis,      // 전체 좌석 재고
  eventMeta: Redis,      // 이벤트 정보
  pricing: Redis         // 가격 정보
});

// 예약 트랜잭션은 100% Redis만 사용 (외부 호출 0)
const result = await redis.eval(luaScript, {
  keys: [`inv:${seatId}`, `booking:${userId}`],
  // 재고 확인 + 예약을 원자적으로 처리
});
효과: 서비스 간 호출 0, DB 쿼리 0 → 초당 10만+ TPS 가능

2. 도메인 데이터 사전 복제 전략
대기실 진입 시 사용자 정보 캐싱 (선택적)
typescript
// 대기열 진입 시점 (부하 낮음)
async function joinQueue(userId: string) {
  // 1. 대기번호 할당
  const position = await assignQueuePosition(userId);
  
  // 2. Auth Service 호출 (이 시점은 부하 낮음)
  const userInfo = await authService.getUser(userId);
  
  // 3. 최소 정보만 Redis 캐싱 (개인정보 제외)
  await redis.hset(`user:${userId}`, {
    userId: userInfo.userId,
    membershipLevel: userInfo.membershipLevel,  // VIP 여부
    isVerified: userInfo.isVerified,
    // ❌ name, email, phone 제외 (개인정보 보호)
  });
  await redis.expire(`user:${userId}`, 1800); // 30분 TTL
}

// 예약 시점 (폭주 상황)
async function reserve(userId: string, seatId: string) {
  // Redis에서만 조회 (외부 호출 0)
  const userInfo = await redis.hgetall(`user:${userId}`);
  
  // Lua로 원자적 예약
  await redis.eval(luaScript);
}
3. 데이터 정합성 보장
Timestamp 기반 동기화
typescript
// Inventory Service가 재고 변경 시 이벤트 발행
await kafka.send({
  topic: 'inventory.changed',
  key: seatId,
  value: {
    seatId,
    available: 99,
    timestamp: Date.now() // 타임스탬프 포함
  }
});

// Booking Service가 수신하여 Redis 업데이트
const updateScript = `
  local key = KEYS[1]
  local new_stock = tonumber(ARGV[1])
  local new_ts = tonumber(ARGV[2])
  
  local current_ts = redis.call('HGET', key, 'updated_at')
  
  -- 오래된 데이터는 무시
  if not current_ts or tonumber(current_ts) < new_ts then
    redis.call('HSET', key, 'available', new_stock, 'updated_at', new_ts)
  end
  return 1
`;
효과: Eventual Consistency를 Strong Consistency처럼 처리
​

4. VWR 2단계 전략: 유량제어 + 순서 보장
구조
text
사용자 → [1단계: SQS FIFO 유량제어] → [2단계: 실제 예약 처리]
1단계: SQS FIFO로 유량제어 + 순서 보장
typescript
// API Gateway: 요청 즉시 SQS에 넣고 응답
export const handler = async (event) => {
  const { userId, eventId, seatId } = JSON.parse(event.body);
  const timestamp = Date.now();
  
  // SQS FIFO에 메시지 전송 (선착순 자동 보장)
  await sqs.send(new SendMessageCommand({
    QueueUrl: process.env.BOOKING_QUEUE_URL,
    MessageBody: JSON.stringify({ userId, eventId, seatId, timestamp }),
    MessageGroupId: eventId,
    MessageDeduplicationId: `${userId}-${timestamp}`
  }));
  
  // 대략적인 순번 계산
  const queueSize = await getQueueSize();
  
  // 즉시 응답 (백엔드 부하 0!)
  return {
    statusCode: 202,
    body: JSON.stringify({
      success: true,
      position: queueSize,
      estimatedWaitTime: Math.ceil(queueSize / 100), // 초당 100건 기준
      message: '예약 요청이 접수되었습니다'
    })
  };
};
특징:

✅ 순서 보장: SQS FIFO가 선착순 자동 관리

✅ 유량제어: Lambda Concurrency로 처리 속도 제한

✅ 백엔드 보호: 큐에 쌓아두고 천천히 처리

✅ ASG 시간 벌기: 2~3분 여유 확보

2단계: Lambda/Worker로 제한된 속도 처리
typescript
// Lambda Consumer (Concurrency 제한)
export const handler = async (event: SQSEvent) => {
  for (const record of event.Records) {
    const { userId, seatId } = JSON.parse(record.body);
    
    // Redis Lua로 예약 처리 (외부 호출 0)
    const result = await redis.eval(`
      local inv_key = KEYS[1]
      local available = tonumber(redis.call('HGET', inv_key, 'available'))
      
      if not available or available <= 0 then
        return {0, 'OUT_OF_STOCK'}
      end
      
      redis.call('HINCRBY', inv_key, 'available', -1)
      redis.call('HSET', 'booking:' .. ARGV[1], 'seat', ARGV[2])
      
      return {1, 'SUCCESS'}
    `, {
      keys: [`inv:${seatId}`],
      arguments: [userId, seatId]
    });
    
    // 성공/실패 알림 (SQS/WebSocket)
    await notifyUser(userId, result);
  }
};
Lambda 설정:

text
resource "aws_lambda_function" "booking_consumer" {
  function_name = "booking-consumer"
  
  # 동시 실행 10개로 제한 (유량제어!)
  reserved_concurrent_executions = 10
}

resource "aws_lambda_event_source_mapping" "sqs_trigger" {
  event_source_arn = aws_sqs_queue.booking_queue.arn
  function_name    = aws_lambda_function.booking_consumer.arn
  
  batch_size = 10  # 한 번에 10개씩
}
전체 처리량: 10개 Lambda × 10개 배치 = 초당 100건

5. 캐시 개인정보 보호
원칙
typescript
// ✅ 캐싱 가능 (비민감 데이터)
const safeCacheData = {
  userId: 'uuid-123',              // 식별자
  membershipLevel: 'VIP',          // 등급
  isVerified: true,                // 인증 여부
  cachedAt: Date.now()
};

// ❌ 캐싱 금지 (민감한 개인정보)
const sensitiveData = {
  name: '홍길동',
  email: 'user@example.com',
  phone: '010-1234-5678',
  creditCard: '****'
};
TTL 전략
typescript
const CACHE_TTL = {
  // 공개 데이터
  eventInfo: 86400,      // 24시간
  seatLayout: 43200,     // 12시간
  
  // 준민감 데이터
  userSession: 1800,     // 30분
  
  // 민감 작업
  bookingLock: 180,      // 3분
  
  // 개인정보: 최소화
  userMinimal: 60        // 1분 또는 캐싱 안 함
};
예약 완료 후 즉시 삭제
typescript
async function completeBooking(userId: string) {
  // 예약 처리
  const result = await processBooking(userId);
  
  // 캐시 즉시 삭제
  await redis.del(`user:${userId}`);
  
  return result;
}
6. DB 스케일아웃 불가 문제
해결: Hot/Cold Path 분리
text
Hot Path (예약 순간):
  요청 → Redis Lua (원자적 처리) → 즉시 응답
                ↓
         SQS FIFO Queue (비동기)
                ↓
Cold Path (후처리):
  Worker → RDS 영속화 (30초 후)
        → 결제 처리
        → 알림 발송
효과: RDS는 조회/분석/관리 용도로만, Write 부하 0

7. 골든타임 3초 대응
Pre-warming (티켓 오픈 5분 전)
typescript
async function warmupBeforeOpen(eventId: string, openTime: Date) {
  const fiveMinutesBefore = new Date(openTime.getTime() - 5 * 60000);
  
  await scheduler.scheduleAt(fiveMinutesBefore, async () => {
    // 1. 전체 좌석 Redis 적재
    const seats = await db.query('SELECT * FROM inventory WHERE event_id = $1', [eventId]);
    
    const pipeline = redis.pipeline();
    seats.forEach(seat => {
      pipeline.hset(`inv:${seat.seat_id}`, {
        available: seat.available,
        price: seat.price,
        updated_at: Date.now()
      });
    });
    await pipeline.exec();
    
    console.log(`Pre-warmed ${seats.length} seats`);
  });
}
Circuit Breaker (장애 즉시 차단)
typescript
import CircuitBreaker from 'opossum';

const breaker = new CircuitBreaker(bookingFunction, {
  timeout: 500,              // 500ms 이상 걸리면 실패
  errorThresholdPercentage: 50,  // 50% 실패 시 Open
  resetTimeout: 10000        // 10초 후 재시도
});

breaker.fallback(() => ({
  error: 'SYSTEM_OVERLOAD',
  message: '시스템 과부하입니다'
}));
8. AWS 비용 최적화
VPC Endpoint 최대 활용
text
# S3 Gateway Endpoint (무료)
resource "aws_vpc_endpoint" "s3" {
  vpc_id       = aws_vpc.main.id
  service_name = "com.amazonaws.ap-northeast-2.s3"
  route_table_ids = [aws_route_table.private.id]
}

# SQS Interface Endpoint
resource "aws_vpc_endpoint" "sqs" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.ap-northeast-2.sqs"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [aws_subnet.app_a.id]
  private_dns_enabled = true
}
예상 절감: NAT Gateway 비용 월 $5,000 → $500 (90% 절감)

최종 통합 아키텍처
text
┌────────────────────────────────────────────┐
│  티켓 오픈 5분 전: Pre-warming            │
│  ✅ 전체 좌석 → Redis                     │
│  ✅ 이벤트 메타 → Redis                   │
│  ❌ 개인정보 캐싱 안 함                   │
└────────────────┬───────────────────────────┘
                 ↓
┌────────────────────────────────────────────┐
│  10만 명 동시 접속                         │
└────────────────┬───────────────────────────┘
                 ↓
┌────────────────────────────────────────────┐
│  API Gateway (Public)                      │
│  - 요청 즉시 SQS FIFO에 넣음              │
│  - 202 응답: "523번째 대기 중"            │
│  - 백엔드 부하 0                          │
└────────────────┬───────────────────────────┘
                 ↓
┌────────────────────────────────────────────┐
│  SQS FIFO Queue                            │
│  ✅ 선착순 자동 보장                       │
│  ✅ 10만 메시지 버퍼링                     │
│  ✅ 내구성 (영구 저장)                     │
└────────────────┬───────────────────────────┘
                 ↓ (초당 100건만 처리)
┌────────────────────────────────────────────┐
│  Lambda Consumer (Concurrency 10)          │
│  - 각 Lambda: 10개 배치                   │
│  - 전체: 초당 100건 처리                  │
│  - ASG 시간 벌어줌 (2~3분)                │
└────────────────┬───────────────────────────┘
                 ↓
┌────────────────────────────────────────────┐
│  Redis Lua Script (Hot Path)               │
│  - 외부 호출 0                            │
│  - DB 쿼리 0                              │
│  - 재고 확인 + 예약 원자적 처리           │
│  - 즉시 응답 (10ms)                       │
└────────────────┬───────────────────────────┘
                 ↓ (비동기)
┌────────────────────────────────────────────┐
│  SQS → Worker (Cold Path)                  │
│  - RDS 영속화 (30초 후)                   │
│  - 결제 처리                              │
│  - 알림 발송                              │
└────────────────────────────────────────────┘