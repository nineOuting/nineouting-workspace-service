# nineouting-workspace-service

워크스페이스·멤버·초대·프로젝트 폴더·폴더 권한을 맡는 서비스.

- 설계(데이터·API·권한·에러 코드): [nineouting-docs/docs/services/workspace-service.md](https://github.com/nineOuting/nineouting-docs/blob/main/docs/services/workspace-service.md)
- 요구사항: REQ-015~017, 024~025, 028~047, 058
- 포트: `8082`

## 로컬 실행

필요한 것: JDK 21, Docker, 실행 중인 user-service(8081)

1. `~/.gradle/gradle.properties`에 `gpr.user`, `gpr.key`(read:packages 토큰) — 공통 모듈을 받기 위해
2. `docker compose up -d` — PostgreSQL(`localhost:5434`), LocalStack SNS·SQS(`localhost:4566`, 토픽·큐는 `docker/localstack/init-sns-sqs.sh`)
3. `./gradlew bootRun --args='--spring.profiles.active=local'`
4. 확인: `http://localhost:8082/actuator/health`

## 테스트

```bash
./gradlew test   # Testcontainers로 PostgreSQL·LocalStack(SNS·SQS)을 띄운다. user-service는 가짜로 바꿔 쓴다
```

## 환경 변수

| 이름 | 기본값 | 설명 |
|------|--------|------|
| `SERVER_PORT` | `8082` | |
| `DB_URL` | `jdbc:postgresql://localhost:5434/workspace_db` | |
| `DB_USERNAME` / `DB_PASSWORD` | `workspace_service` | |
| `SNS_TOPIC_PREFIX` | `arn:aws:sns:ap-northeast-2:000000000000:nineouting-local-` | 참여 요청·승인, 이메일 초대, 폴더·워크스페이스 삭제 이벤트를 보낼 SNS 토픽 ARN 앞부분 (뒤에 이벤트 이름이 붙는다) |
| `AWS_ENDPOINT_URL` | — | 비우면 AWS. LocalStack이면 `http://localhost:4566` (`local` 프로필은 자동) |
| `MESSAGING_ACCESS_KEY` / `MESSAGING_SECRET_KEY` | — | 비우면 AWS 기본 자격 증명(EKS는 IRSA). LocalStack은 `test` (`local` 프로필은 자동) |
| `JWT_JWKS_URI` | `http://localhost:8081/.well-known/jwks.json` | access 토큰 검증 공개키 |
| `USER_SERVICE_URL` | `http://localhost:8081` | 사용자 이름·이메일 조회 (공통 모듈 `client-common`) |
| `FRONTEND_BASE_URL` | `http://localhost:5173` | 초대 링크 주소 (이메일 초대 링크는 notification이 만든다) |
| `SPRING_PROFILES_ACTIVE` | — | `local`: 사람이 읽는 로그, 추적 전송 끔 |

## Docker 이미지

```bash
docker build --secret id=gpr_actor,env=GITHUB_ACTOR --secret id=gpr_token,env=GPR_TOKEN -t workspace-service .
```
