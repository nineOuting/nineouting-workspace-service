# nineouting-workspace-service

워크스페이스·멤버·초대·프로젝트 폴더·폴더 권한을 맡는 서비스.

- 설계(데이터·API·권한·에러 코드): [nineouting-docs/docs/services/workspace-service.md](https://github.com/nineOuting/nineouting-docs/blob/main/docs/services/workspace-service.md)
- 요구사항: REQ-015~017, 024~025, 028~045, 114, 116~117
- 포트: `8082`

## 로컬 실행

필요한 것: JDK 21, Docker, 실행 중인 user-service(8081)

1. `~/.gradle/gradle.properties`에 `gpr.user`, `gpr.key`(read:packages 토큰) — 공통 모듈을 받기 위해
2. `docker compose up -d` — MySQL(`localhost:3308`), Kafka(`localhost:9092`)
3. `./gradlew bootRun --args='--spring.profiles.active=local'`
4. 확인: `http://localhost:8082/actuator/health`

## 테스트

```bash
./gradlew test   # Testcontainers로 MySQL·Kafka를 띄운다. user-service는 가짜로 바꿔 쓴다
```

## 환경 변수

| 이름 | 기본값 | 설명 |
|------|--------|------|
| `SERVER_PORT` | `8082` | |
| `DB_URL` | `jdbc:mysql://localhost:3308/workspace_db` | |
| `DB_USERNAME` / `DB_PASSWORD` | `workspace_service` | |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | 참여 요청·승인, 폴더 삭제 이벤트 |
| `JWT_JWKS_URI` | `http://localhost:8081/.well-known/jwks.json` | access 토큰 검증 공개키 |
| `USER_SERVICE_URL` | `http://localhost:8081` | 사용자 이름·이메일 조회 (공통 모듈 `client-common`) |
| `FRONTEND_BASE_URL` | `http://localhost:5173` | 초대 링크 주소 |
| `SPRING_PROFILES_ACTIVE` | — | `local`: 사람이 읽는 로그, 추적 전송 끔 |

## Docker 이미지

```bash
docker build --secret id=gpr_actor,env=GITHUB_ACTOR --secret id=gpr_token,env=GPR_TOKEN -t workspace-service .
```
