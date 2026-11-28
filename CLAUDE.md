# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

JobBeacon notification service: Spring Boot 3.3.6 / Java 21 / Spring Cloud 2023.0.5 (Maven, no wrapper — use a system `mvn`). Consumes events from other services over RabbitMQ (topic exchange `jobbeacon.events`), stores notifications in its own Postgres database `jobbeacon_notification`, and delivers them by email and pushes them in realtime to open browsers over SSE (no WebSocket, no FCM). Registers in Eureka (`../Jobbeacon-discovery`, port 8761) as `jobbeacon-notification`.

**Current state:** the core is implemented (task `.work/2026-11-notification-base`). The producers in the backend and recommendation, the gateway route and the docker-compose wiring are not done yet. `README.md` is the spec — events, delivery guarantees, tables, the Strategy + Adapter layout. Keep the code in line with it.

## Commands

```bash
mvn spring-boot:run                 # run locally (profile defaults to "local"), port 8084
mvn -B -DskipTests package          # build target/jobbeacon-notification.jar
mvn test                            # *Test + *IT; IT tests use Testcontainers (Postgres, RabbitMQ) and need Docker
docker build -t jobbeacon-notification .
```

Deployed via `../infras-deployment/docker-compose.yml` (service `notification`, port `NOTIFICATION_PORT`, default 8084).

## Architecture (see README.md)

- Package root `com.jobbeacon.notification`: `event` (`NotificationEventListener` → `EventProcessor` → `EventHandler`s in `event/handler`), `contact`, `notification`, `preference`, `delivery` (`DeliveryWorker`, `ChannelSender`), `provider` (`EmailProvider` adapters), `realtime` (SSE), `config` (`@ConfigurationProperties` records, RabbitMQ topology), `security` (`@CurrentUid`), `web` (`ResponseGeneral`, `ApiException`, advice, i18n codes).
- `server.servlet.context-path` is `/jobbeacon-notification`; controllers map `/api/v1/...` under it.
- Producers (backend, recommendation) publish directly to RabbitMQ after commit — **no outbox**. This service must be idempotent: dedupe on `event_id` via `processed_event`, written in the same transaction as the handler's rows. The listener uses `acknowledge-mode: auto`, so a message is acked only after the transaction commits. Spring retry `max-attempts: 3` means 3 attempts in total (the first try plus 2 retries); after that the message is rejected to the DLQ `notification.dlq`.
- `user_contact` is a local copy (`firebase_uid`, `email`, `display_name`, `status`) synced only from `user.upserted` (full snapshot, last-write-wins, no `version` yet). The backend publishes it on user creation, display-name change and status change, and backfills existing users with a startup runner behind a flag. No Feign, no nightly reconciliation. Skip sending to non-`ACTIVE` users and `@firebase.local` addresses; a missing contact keeps the delivery `PENDING` and retries.
- Tables: `user_contact`, `notification` (one row per message to a user), `notification_delivery` (one row per email send; SSE/in-app have no delivery row), `notification_preference` (one row per user, `job_match_enabled` default true; no row = enabled; owned here, not synced from the backend), `processed_event`.
- Never send email inside the listener or a DB transaction: handlers only write `notification` + `notification_delivery(PENDING)`; `DeliveryWorker` claims due rows in a short transaction (`FOR UPDATE SKIP LOCKED`, attempts+1, `next_retry_at = now + lease`), sends outside any transaction, then records SENT, a backoff reschedule or FAILED.
- Realtime: `GET /api/v1/me/notifications/stream` returns an `SseEmitter`; `SseEmitterRegistry` maps `user_id → emitters` in memory; `RealtimePublisher` pushes `notification.created` after commit; heartbeat ~25s. SSE is only a nudge — the DB is the source of truth and the FE refetches on reconnect.
- Strategy: `EventHandler` per `event_type`, `ChannelSender` per channel (EMAIL only). Adapter: `EmailProvider` wraps `JavaMailSender` and maps errors to retryable vs permanent.
- Auth: `@CurrentUid String uid` reads the gateway's `X-User-Id` header and returns 401 when it is missing. That header is the **Firebase uid**. Map it to the backend user id with `UserContactService.findUserId` / `requireUserId` (the latter returns 404 `user_not_synced`). This service does not verify tokens, and the gateway does not send an internal secret yet.

# Chế độ điều phối (orchestrator)

Với task lớn, bạn là MAIN AGENT, vai trò điều phối. Không tự đọc hàng loạt file hay viết code lớn.

## Quy trình
1. Phân tích yêu cầu, chia thành các task nhỏ (mỗi task gói gọn trong vài file).
   Ghi vào .work/<task-dir>/PLAN.md với: id, mô tả, phạm vi file, phụ thuộc, trạng thái.
2. Với mỗi task:
    - Chưa rõ code → giao `explorer`.
    - Thực hiện → giao `implementer`, truyền task-id.
    - Xong → giao `reviewer`.
3. Sau mỗi lần sub-agent trả về: cập nhật trạng thái trong PLAN.md NGAY.
4. Quyết định kiến trúc/quy ước mới phát sinh → ghi vào .work/CONVENTIONS.md
   để các sub-agent sau dùng chung.

## Quy tắc
- Task không đụng chung file → có thể chạy song song.
- Task đụng chung file → chạy tuần tự.
- Không yêu cầu sub-agent trả về nội dung file đầy đủ; chỉ cần tóm tắt + đường dẫn report.
- Nếu context bị reset/compact: đọc lại PLAN.md và CONVENTIONS.md để khôi phục trạng thái.

## Quản lý workspace
- Mỗi task lớn mới: copy `.work/_template/` thành `.work/<YYYY-MM>-<slug>/` và thêm 1 dòng vào .work/INDEX.md.
- Chỉ làm việc trong thư mục của task hiện tại. Không đọc reports của task khác
  trừ khi được yêu cầu.
- Luôn đọc .work/CONVENTIONS.md và .work/DECISIONS.md (cấp project) trước khi lập plan.
- Khi task hoàn thành:
    1. Chuyển quy ước/quyết định có giá trị lâu dài lên file cấp project.
    2. Chuyển thư mục task vào .work/archive/.
    3. Cập nhật INDEX.md.
- Khi bắt đầu session mới: đọc INDEX.md để biết task nào đang ACTIVE, rồi đọc PLAN.md của task đó.
