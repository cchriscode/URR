# Lambda@Edge Queue Entry Token Validator

CloudFront viewer-request 함수로, 대기열 진입 토큰을 검증하여 예매 API 접근을 제어합니다.

## 작동 원리

1. CloudFront에서 요청이 들어오면 이 Lambda@Edge 함수가 viewer-request 이벤트로 실행됩니다
2. `/api/reservations/**`, `/api/tickets/**` 등 보호된 경로인 경우 쿠키에서 `tiketi-entry-token` 확인
3. JWT 서명 검증 + 만료 시간 확인
4. 유효하면 → ALB로 요청 전달
5. 무효/없으면 → `/queue/{eventId}` 로 302 리다이렉트

## 환경 변수

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `QUEUE_ENTRY_TOKEN_SECRET` | HMAC-SHA256 서명 키 (ticket-service의 `queue.entry-token.secret`와 동일해야 함) | `dev-secret-key-change-in-production-min-32-chars` |

## 배포

### 1. AWS Lambda 함수 생성

```bash
cd lambda/edge-queue-check
zip -r function.zip index.js

aws lambda create-function \
  --function-name tiketi-edge-queue-check \
  --runtime nodejs20.x \
  --role arn:aws:iam::ACCOUNT_ID:role/lambda-edge-role \
  --handler index.handler \
  --zip-file fileb://function.zip \
  --region us-east-1
```

**주의**: Lambda@Edge는 반드시 `us-east-1` 리전에 생성해야 합니다.

### 2. 환경 변수 설정

Lambda@Edge는 환경 변수를 지원하지 않으므로, 코드 내에서 직접 하드코딩하거나 AWS Secrets Manager + 코드 수정 필요:

**Option A**: 코드에 하드코딩 (간단, 보안 낮음)
```javascript
const SECRET = 'your-production-secret-here';
```

**Option B**: Secrets Manager (권장, 복잡)
- Lambda@Edge는 Secrets Manager API 호출 시 지연 발생 → CloudFront 타임아웃 위험
- 대신 빌드 시점에 secret을 코드에 삽입하는 방식 권장

### 3. CloudFront 배포에 연결

```bash
# 버전 퍼블리시
aws lambda publish-version \
  --function-name tiketi-edge-queue-check \
  --region us-east-1

# CloudFront 배포 업데이트 (ARN은 위 명령어 결과에서 확인)
aws cloudfront update-distribution \
  --id YOUR_DISTRIBUTION_ID \
  --distribution-config file://cloudfront-config.json
```

`cloudfront-config.json` 예시:
```json
{
  "DefaultCacheBehavior": {
    "LambdaFunctionAssociations": {
      "Quantity": 1,
      "Items": [{
        "LambdaFunctionARN": "arn:aws:lambda:us-east-1:ACCOUNT_ID:function:tiketi-edge-queue-check:VERSION",
        "EventType": "viewer-request",
        "IncludeBody": false
      }]
    }
  }
}
```

## 테스트

### 로컬 테스트

```javascript
// test.js
const handler = require('./index').handler;

const event = {
  Records: [{
    cf: {
      request: {
        uri: '/api/reservations',
        headers: {
          cookie: [{
            value: 'tiketi-entry-token=eyJhbGciOiJIUzI1NiJ9...'
          }]
        }
      }
    }
  }]
};

handler(event).then(result => console.log(result));
```

### CloudFront 테스트

```bash
# 토큰 없이 보호된 경로 요청 → 302 리다이렉트
curl -i https://your-cloudfront-domain/api/reservations

# 유효한 토큰과 함께 요청 → 200 OK
curl -i -H "Cookie: tiketi-entry-token=VALID_JWT" \
  https://your-cloudfront-domain/api/reservations
```

## 보호 대상 경로

- `/api/reservations/**`
- `/api/tickets/**`
- `/api/seats/**`
- `/api/admin/**`

## 예외 경로 (토큰 불필요)

- `/api/queue/**` - 대기열 API
- `/api/auth/**` - 인증 API
- `/api/events/**` - 이벤트 조회
- `/api/stats/**` - 통계
- `/health`, `/actuator` - 헬스체크

## 주의사항

1. **Lambda@Edge 제약사항**
   - 실행 시간 5초 제한
   - 메모리 128MB 제한
   - 환경 변수 미지원
   - us-east-1 리전만 지원

2. **JWT Secret 동기화**
   - ticket-service의 `QUEUE_ENTRY_TOKEN_SECRET` 환경변수와 Lambda 코드의 `SECRET` 값이 반드시 일치해야 함
   - Production 배포 전 secret 변경 필수

3. **CloudFront 캐시 주의**
   - 302 리다이렉트 응답은 캐시하지 않도록 `Cache-Control: no-store` 헤더 포함됨
   - 정상 요청(200)은 CloudFront 캐시 정책에 따름

4. **로깅**
   - Lambda@Edge 로그는 CloudWatch Logs에 기록되지만, 각 엣지 로케이션마다 별도 로그 그룹 생성
   - 디버깅 시 모든 리전의 로그 그룹 확인 필요
