# JobBeacon notification

Service thông báo của JobBeacon. Các service khác publish event lên RabbitMQ, service này nhận event, lưu thông báo vào DB riêng và gửi qua các kênh: email, in-app, và đẩy realtime xuống trình duyệt đang mở bằng SSE. Service chỉ làm notification: không tính toán nghiệp vụ, không giữ dữ liệu gốc của service khác.

> Trạng thái: phần lõi đã implement (task `2026-11-notification-base`): consumer, `user_contact`, digest, gửi email, inbox API, preference, SSE. Phía producer (backend, recommendation), route gateway và docker-compose chưa làm, xem "Việc ngoài repo" trong PLAN của task.

## Vị trí trong hệ thống

```
                    ┌──────────── RabbitMQ: exchange jobbeacon.events (topic) ────────────┐
                    │                                                                      │
Jobbeacon-be ─ publish ──▶ user.upserted / user.registered ...                            │
                    │                                                                      ▼
recommendation ─ publish ▶ recommendation.job-match.digest              jobbeacon-notification
                    │                                                     ├─ consumer → notification + delivery
                    └──────────────────────────────────────────────────── ├─ worker gửi email (SMTP)
                                                                          ├─ API inbox cho FE
                                                                          └─ SSE stream realtime
FE ──(gateway)──▶ GET /api/v1/me/notifications, GET /api/v1/me/notifications/stream (SSE)
```

- **RabbitMQ là event bus.** Service này không làm broker hay event store chung, chỉ consume những event cần báo cho user.
- **Producer không biết consumer.** Producer publish lên exchange `jobbeacon.events` với routing key, notification-service tự bind queue theo các routing key nó quan tâm. Thêm loại thông báo mới thì producer publish event mới, service này thêm binding và template, không phải sửa producer khác.
- **Database riêng** `jobbeacon_notification` (database-per-service), dùng chung Postgres instance với các service khác.

### Vì sao tách thành service riêng

- Nhiều producer: recommendation (job phù hợp hằng ngày), backend (job đã lưu sắp hết hạn, email chào mừng).
- Cô lập lỗi: SMTP chậm, lỗi hay bị rate limit không ảnh hưởng BE và recommendation. Event chờ trong queue, gửi lại sau.
- Tải khác biệt: dồn lúc digest chạy (8h sáng), có retry/backoff, scale độc lập.
- Gom kênh realtime (SSE) về một chỗ: FE chỉ giữ một kết nối tới một service cho mọi loại thông báo.
- Thêm kênh sau này (push, Telegram) mà không đụng service khác.

## Giao tiếp với các service

### Event envelope

Mọi event dùng chung một envelope:

```json
{
  "event_id": "0b6c...",
  "event_type": "JOB_MATCH_DIGEST",
  "occurred_at": "2026-11-21T08:00:00+07:00",
  "producer": "jobbeacon-recommendation",
  "user_id": "5f1e...",
  "payload": { }
}
```

- `event_id`: UUID producer sinh lúc tạo event, dùng để chống xử lý trùng.
- `user_id`: user nhận thông báo (null với event không gắn user).

### Danh sách event

| Routing key | Producer | Payload | Notification-service làm gì |
|---|---|---|---|
| `user.upserted` | backend | `user_id, firebase_uid, email, display_name, status` (toàn bộ thông tin hiện tại) | Upsert `user_contact` |
| `recommendation.job-match.digest` | recommendation | `digest_key, jobs[{job_id, title, company, url}]` | Tạo thông báo job phù hợp, gửi email |
| `user.registered` | backend | `user_id, display_name` (chỉ phát khi tạo user mới, backfill không phát) | Tạo thông báo chào mừng `WELCOME` (in-app + email), dedupe `WELCOME:<user_id>` |
| `saved-job.expiring` | backend | `jobs[{job_id, title, company, expires_at}]` | Thông báo in-app + email (dự kiến) |

### Đảm bảo giao nhận

Producer publish thẳng lên RabbitMQ, không dùng outbox. Event notification là thông tin phụ: mất một event không làm sai dữ liệu nghiệp vụ, nên chấp nhận đánh đổi này để giảm độ phức tạp. Mất event user thì sửa bằng cách chạy lại backfill (xem dưới).

**Phía producer**

- **Publish sau khi commit:** phát Spring event trong transaction, gửi RabbitMQ trong `@TransactionalEventListener(phase = AFTER_COMMIT)`. Transaction rollback thì không có event nào đi.
- **Publisher confirms + retry:** bật `publisher-confirm-type: correlated`, gửi lỗi thì retry vài lần, hết lượt thì log ERROR.
- **Exchange, queue durable và message persistent:** RabbitMQ restart không mất message đang nằm trong queue.

Khe hở còn lại: app crash ngay sau commit trước khi gửi, hoặc RabbitMQ chết lâu hơn thời gian retry, thì event mất.

| Event | Mất thì sao | Xử lý |
|---|---|---|
| `recommendation.job-match.digest` | User không nhận mail hôm đó, danh sách job vẫn có trên web | Chấp nhận, hôm sau job chạy lại |
| `saved-job.expiring` | Thiếu một lần nhắc | Chấp nhận |
| `user.upserted` | `user_contact` thiếu hoặc tên/status cũ | Giới hạn đã biết; chạy lại backfill để sửa |

**Phía consumer**

- **Idempotent:** RabbitMQ giao at-least-once (consumer crash trước khi ack thì message được giao lại; producer retry cũng có thể gửi trùng). Mỗi event xử lý xong được ghi vào `processed_event (event_id)`, event đã có thì bỏ qua.
- **Ack sau khi xử lý xong:** listener chạy với `acknowledge-mode: auto`, container chỉ ack khi method trả về, tức sau khi transaction xử lý đã commit. Lỗi giữa chừng thì message không bị ack.
- **Lỗi xử lý:** retry vài lần, sau đó chuyển sang dead letter queue `notification.dlq` để xem lại.

### Dữ liệu user: sync qua event

Event digest chỉ có `user_id`, không có email. Service giữ một bản sao nhỏ là bảng `user_contact`, cập nhật từ event `user.upserted` của backend. Không gọi đồng bộ sang backend (không Feign): khi gửi mail chỉ đọc DB của mình, nên backend tắt vẫn gửi được.

**Một event, mang toàn bộ thông tin hiện tại** (không tách `registered` / `name-changed` / `locked`):

```json
{ "user_id": "...", "firebase_uid": "Xy12...", "email": "a@x.com", "display_name": "A", "status": "ACTIVE" }
```

Notification chỉ có một handler, lần nào cũng ghi đè cả dòng `user_contact`.

`firebase_uid` cần vì gateway gửi `X-User-Id` là **Firebase uid**, còn event dùng **id user của backend** (UUID). API của FE tra `firebase_uid → user_id` qua `user_contact`. User chưa sync (event chưa tới): inbox trả rỗng, preference trả mặc định, còn các thao tác cần user (lưu preference, đánh dấu đã đọc một thông báo, đánh dấu tất cả đã đọc `read-all`, mở stream) trả 404 `user_not_synced`, FE thử lại sau.

**Backend phát event khi nào**

| Tình huống | Chỗ trong backend | Phát |
|---|---|---|
| User mới | `UserSyncServiceImpl.syncFromPrincipal`, nhánh tạo user | Có |
| Đăng nhập, `display_name` khác giá trị cũ | `updateExistingUser` | Có |
| Đăng nhập, tên không đổi | `updateExistingUser` | **Không** — hàm này chạy ở mọi request, phải so trước khi phát |
| Khóa / mở khóa tài khoản | Chưa có, thêm sau | Có, cùng hàm publish |

Email đăng ký không đổi nên không cần so email.

**Backfill user đã có:** `UserContactBackfillRunner` ở backend (giống `search/JobSearchBackfillRunner`), chạy khi `USER_CONTACT_BACKFILL_ON_STARTUP=true`, duyệt `users` theo trang và publish `user.upserted` cho từng user. Thứ tự triển khai lần đầu: chạy notification trước (để queue đã bind, nếu không message bị RabbitMQ bỏ), rồi khởi động backend với cờ bật, xong đặt lại `false`. Chạy lại nhiều lần vẫn an toàn (upsert cùng dữ liệu). Đây cũng là cách sửa khi nghi mất event.

**Phía notification**

- Upsert `user_contact` (last-write-wins).
- Gửi email bỏ qua user có `status` khác `ACTIVE` và địa chỉ `@firebase.local` (backend sinh email giả khi Firebase không trả email) → delivery `FAILED` kèm lý do.
- Chưa có `user_contact` (event user chưa tới) → delivery giữ `PENDING`, retry theo backoff; quá số lần → `FAILED`.

**Giới hạn đã biết (chấp nhận ở quy mô hiện tại)**

- Không có `version`: hai event của cùng user đến sai thứ tự (vd. đổi tên và khóa gần như cùng lúc) thì bản cũ có thể ghi đè bản mới. Thêm sau nếu cần: `version` = `updated_at` của backend (phải bỏ `updatable = false` ở entity `User`), chỉ ghi khi version mới lớn hơn.
- Không có job đối soát: mất event thì `user_contact` sai đến lần thay đổi kế tiếp hoặc lần chạy backfill.
- Dữ liệu trễ vài giây so với backend (eventual consistency).

### API cho FE (qua gateway)

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/api/v1/me/notifications` | Danh sách thông báo in-app, phân trang |
| `GET` | `/api/v1/me/notifications/unread-count` | Số thông báo chưa đọc |
| `PATCH` | `/api/v1/me/notifications/{id}/read` | Đánh dấu đã đọc |
| `PATCH` | `/api/v1/me/notifications/read-all` | Đánh dấu tất cả đã đọc (user chưa sync → 404 `user_not_synced`) |
| `GET` | `/api/v1/me/notifications/stream` | SSE stream, nhận thông báo mới realtime |
| `GET` | `/api/v1/me/notification-preferences` | `{ "job_match_enabled": true }` |
| `PUT` | `/api/v1/me/notification-preferences` | Bật/tắt nhận thông báo job phù hợp (upsert) |

## Luồng ví dụ: job phù hợp hằng ngày

```
1. recommendation @Scheduled 8h: tính top-N job cho từng candidate
2. recommendation, 1 transaction: lưu recommendation_snapshot
3. sau commit: publish → jobbeacon.events, routing key recommendation.job-match.digest (chờ confirm, retry)
4. notification consumer:
     event_id đã xử lý?            → bỏ qua
     job_match_enabled = false?    → chỉ ghi processed_event, ack (không notification, email, SSE)
     còn lại, 1 transaction: notification + notification_delivery(EMAIL, PENDING) + processed_event
     sau commit: đẩy notification.created xuống SSE của user (nếu đang online)
5. delivery worker (mỗi 5s):
     claim, 1 transaction ngắn: chọn PENDING đến hạn (FOR UPDATE SKIP LOCKED), tăng attempts, đẩy next_retry_at = now + lease (5 phút)
     ngoài transaction: kiểm tra user_contact → render template → SMTP
     thành công → SENT; lỗi tạm → next_retry_at = now + backoff (30s nhân đôi, tối đa 1h); quá 6 lần hoặc lỗi vĩnh viễn → FAILED
     crash giữa chừng → hết lease thì dòng tự được lấy lại
```

FE vẫn đọc danh sách job phù hợp trực tiếp từ recommendation. Thông báo chỉ báo là có job mới và dẫn link về trang đó.

## Realtime: SSE

Thông báo mới được đẩy xuống trình duyệt đang mở qua Server-Sent Events. Chọn SSE thay vì WebSocket hay FCM push:

| | SSE | WebSocket | FCM push |
|---|---|---|---|
| Chiều dữ liệu | Server → client, đúng nhu cầu | Hai chiều, thừa | Server → thiết bị |
| Auth qua gateway | HTTP thường, header `Authorization` như mọi API | Trình duyệt không gắn được header vào handshake, phải truyền token qua query/CONNECT | Không liên quan, nhưng cần quản lý device token |
| Gateway | Route HTTP thường, tăng timeout | Route `ws://`, xử lý upgrade | Không liên quan |
| Khi đóng tab | Không nhận (email bù) | Không nhận | Vẫn nhận |
| Độ phức tạp | Thấp | Trung bình–cao | Trung bình–cao |

```
FE ──GET /api/v1/me/notifications/stream (Authorization)──▶ gateway ──▶ notification-service
                                                                         SseEmitterRegistry: user_id → [emitter...]
handler tạo notification ─▶ commit ─▶ RealtimePublisher ─▶ emitter của user ─▶ event: notification.created
```

- **SSE chỉ là lời nhắc, DB mới là nguồn dữ liệu.** Mất kết nối không mất thông báo: khi (re)connect, FE gọi `GET /notifications` và `unread-count` để lấy phần bỏ lỡ. Vì vậy SSE không cần `notification_delivery`.
- **Đẩy sau commit** (`@TransactionalEventListener(AFTER_COMMIT)`), không đẩy trong transaction.
- **Nhiều tab / thiết bị:** một user có nhiều emitter; tab đóng thì gỡ emitter khi `onCompletion` / `onTimeout` / `onError`.
- **Heartbeat** mỗi ~25 giây (comment `:ping`) để proxy không cắt kết nối vì im lặng quá lâu.
- **Gateway phải tắt `response-timeout` cho route stream, nếu không kết nối SSE sẽ bị cắt.** `Jobbeacon-gateway` đặt `spring.cloud.gateway.httpclient.response-timeout: 60s` cho mọi route; một stream SSE không bao giờ "trả xong", nên sau 60 giây gateway sẽ đóng kết nối (heartbeat không cứu được). Thêm route riêng, ưu tiên cao hơn route chung, giống `backend-chat-stream` đang làm cho chat:

  ```yaml
  - id: notification-stream
    uri: lb://${NOTIFICATION_SERVICE_ID:jobbeacon-notification}
    order: -1
    predicates:
      - Path=/jobbeacon-notification/api/v1/me/notifications/stream
    metadata:
      response-timeout: -1      # không giới hạn
  - id: notification
    uri: lb://${NOTIFICATION_SERVICE_ID:jobbeacon-notification}
    predicates:
      - Path=/jobbeacon-notification/api/**,/jobbeacon-notification/actuator/health
  ```

  Nếu có proxy khác đứng trước gateway (nginx), cũng phải tăng `proxy_read_timeout` và tắt `proxy_buffering` cho path này.
- **FE** dùng `@microsoft/fetch-event-source` (EventSource gốc không gắn được header `Authorization`), tự kết nối lại khi rớt.
- **Một instance:** registry nằm trong bộ nhớ là đủ. **Mở rộng nhiều instance:** mỗi instance bind một queue riêng (exclusive, auto-delete) vào exchange fanout `notification.realtime`; instance tạo thông báo publish lên đó, instance nào giữ emitter của user thì đẩy xuống.

## Thiết kế code: Strategy + Adapter

Hai chỗ thay đổi nhiều nhất là **loại event** và **kênh gửi / nhà cung cấp**. Tách chúng ra để thêm mới mà không sửa code cũ (open/closed).

```
RabbitMQ ─▶ EventProcessor  ──(theo event_type)──▶ EventHandler          ← Strategy
                                                    ├─ JobMatchDigestHandler
                                                    ├─ UserUpsertedHandler
                                                    └─ SavedJobExpiringHandler
                                                         │ tạo notification + delivery
                                                         ├─ sau commit ─▶ RealtimePublisher ─▶ SseEmitterRegistry
                                                         ▼
DeliveryWorker ──(theo channel)──▶ ChannelSender                         ← Strategy
                                    └─ EmailChannelSender ─▶ EmailProvider        ← Adapter
                                                              ├─ SmtpEmailProvider (JavaMailSender)
                                                              └─ (sau này) SendGridEmailProvider
```

**Strategy: chọn cách xử lý lúc chạy**

- `EventHandler`: mỗi `event_type` một class (`eventType()`, `handle(envelope)`). `EventProcessor` nhận tất cả handler qua Spring (`List<EventHandler>`), dựng map `event_type → handler` (hai handler cùng type thì lỗi lúc khởi động). Event mới chỉ cần thêm một handler và binding queue. Không có handler thì log WARN và ack.
- `ChannelSender`: mỗi `channel` một class (hiện chỉ EMAIL) (`channel()`, `send(notification, contact)`). `DeliveryWorker` lấy sender theo `delivery.channel`, không biết chi tiết từng kênh.

**Adapter: bọc thư viện / dịch vụ bên ngoài**

- `EmailProvider` là interface của service với input đơn giản (`to`, `subject`, `html`). Mỗi adapter chuyển sang API riêng (`JavaMailSender`, SDK SendGrid) và chuyển lỗi của thư viện thành lỗi chung của service: `RetryableDeliveryException` (timeout, 5xx) hoặc `PermanentDeliveryException` (địa chỉ sai).
- Đổi nhà cung cấp (SMTP → SendGrid) chỉ thay adapter, chọn bằng cấu hình (`@ConditionalOnProperty`). Sender, worker, handler không đổi.
- Event từ RabbitMQ cũng được chuyển thành object nội bộ ngay ở listener, nên đổi format event của producer chỉ sửa một chỗ.

Phân vai: Strategy quyết định **làm gì** (kênh nào, loại event nào); Adapter quyết định **gọi bên ngoài thế nào**. Test cũng dễ: mock `EmailProvider` để test `EmailChannelSender` mà không cần SMTP.

## Database (`jobbeacon_notification`)

```
user_contact 1 ──── * notification 1 ──── * notification_delivery
user_contact 1 ──── 0..1 notification_preference
processed_event (độc lập)
```

| Bảng | Cột chính | Ghi chú |
|---|---|---|
| `user_contact` | `user_id` PK, `firebase_uid` unique, `email`, `display_name`, `status`, `updated_at` | Bản sao từ backend (`updated_at` là lúc service này ghi) |
| `notification` | `id`, `user_id`, `type`, `title`, `body`, `payload` jsonb, `dedupe_key` unique, `source_event_id`, `read_at`, `created_at` | Thông báo in-app. `dedupe_key` ví dụ `JOB_MATCH:2026-11-21:<user_id>`; `source_event_id` là `event_id` của event đã tạo dòng này (để truy vết) |
| `notification_delivery` | `id`, `notification_id`, `channel` (EMAIL), `status` (PENDING, SENT, FAILED), `attempts`, `next_retry_at`, `last_error`, `sent_at` | Mỗi kênh gửi một dòng; worker gửi email đọc bảng này |
| `notification_preference` | `user_id` PK, `job_match_enabled` (mặc định `TRUE`), `updated_at` | Không có dòng nghĩa là đang bật. Chỉ tạo khi user sửa |
| `processed_event` | `event_id` PK, `event_type`, `processed_at` | Chống xử lý event trùng |

Trạng thái gửi tách sang `notification_delivery` thay vì gộp vào `notification`:

- Email có vòng đời riêng (PENDING → SENT/FAILED, số lần thử, lỗi cuối) mà nội dung thông báo không có. Email lỗi không làm thông báo in-app bị coi là lỗi.
- In-app và SSE không cần "gửi": dòng `notification` tồn tại là user đã thấy được, SSE chỉ là lời nhắc. Chỉ kênh ra ngoài (email) mới cần delivery.
- Worker gửi chỉ quét bảng nhỏ `notification_delivery` theo `next_retry_at` của các dòng `PENDING` (index partial), không đụng bảng `notification` mà FE đang đọc.
- Thêm kênh mới chỉ thêm giá trị `channel`, không thêm cột.

### Bật/tắt thông báo job phù hợp

User chỉ có một tùy chọn: nhận hay không nhận thông báo job phù hợp. Cờ áp dụng cho cả in-app, email và SSE.

Cờ nằm ở bảng `notification_preference` của service này, không đặt ở backend:

- "Có muốn nhận thông báo không" là hành vi của tính năng thông báo, nên thuộc về service thực thi nó. Handler đọc DB của chính mình, không cần sync, không bị trễ: user vừa tắt là không bị gửi nữa.
- Đặt ở `users` hay `user_job_preference` của backend thì phải đưa cờ vào event `user.upserted` để sync sang; mất hoặc trễ event là gửi nhầm cho user đã tắt, và mỗi tùy chọn mới đều phải sửa schema + event của backend.
- Không gộp vào `user_contact`: bảng đó là bản sao dữ liệu backend, bị ghi đè mỗi khi nhận event hoặc chạy backfill.
- Recommendation vẫn publish digest cho mọi user (vẫn phải tính danh sách cho web); notification lọc. `open_to_work` của backend là chuyện khác (user có đang tìm việc), nếu cần thì recommendation lọc khi tính.
- Mở rộng: thêm tùy chọn mới thì thêm một cột boolean; khi nào thật sự cần chia theo kênh mới chuyển sang dạng type × channel.

Index: `notification (user_id, created_at desc)`, `notification (user_id) where read_at is null`, `notification_delivery (next_retry_at) where status = 'PENDING'` (partial, chỉ chứa dòng worker cần quét).

## Chạy local

```bash
mvn spring-boot:run
```

Cần Postgres, RabbitMQ và một SMTP server (local dùng Mailpit: SMTP `1025`, web `8025`). Tạo database một lần:

```sql
CREATE DATABASE jobbeacon_notification;
```

Liquibase tạo bảng lúc khởi động; queue, DLQ và binding được khai báo khi kết nối RabbitMQ. Mặc định: cổng `8084`, context path `/jobbeacon-notification` (API là `/jobbeacon-notification/api/v1/...`, giống các service khác sau gateway).

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `PORT` | `8084` | Cổng HTTP |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/jobbeacon_notification`, `postgres`, `postgres` | DB của service |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `localhost`, `5672`, `guest`, `guest` | RabbitMQ |
| `EVENTS_EXCHANGE` / `EVENTS_QUEUE` / `EVENTS_DLQ` | `jobbeacon.events`, `notification.events`, `notification.dlq` | Exchange chung, queue và DLQ của service |
| `EVENT_MAX_ATTEMPTS` | `3` | Số lần thử xử lý một message trước khi vào DLQ |
| `MAIL_HOST` / `MAIL_PORT` | `localhost`, `1025` | SMTP |
| `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_SMTP_AUTH` / `MAIL_STARTTLS` | rỗng, rỗng, `false`, `false` | Đăng nhập SMTP thật (Gmail, SendGrid SMTP...) |
| `MAIL_FROM` | `JobBeacon <no-reply@jobbeacon.local>` | Người gửi. Bắt buộc: được validate khi khởi động, để trống hoặc sai định dạng thì service không start |
| `FRONTEND_BASE_URL` | `http://localhost:3000` | Link trong email |
| `DELIVERY_ENABLED` | `true` | Tắt worker gửi email |
| `DELIVERY_POLL_INTERVAL` / `DELIVERY_BATCH_SIZE` | `PT5S`, `50` | Chu kỳ và cỡ lô của worker |
| `DELIVERY_MAX_ATTEMPTS` / `DELIVERY_INITIAL_BACKOFF` / `DELIVERY_MAX_BACKOFF` / `DELIVERY_LEASE` | `6`, `PT30S`, `PT1H`, `PT5M` | Lịch retry |
| `SSE_EMITTER_TIMEOUT` / `SSE_HEARTBEAT_INTERVAL` | `PT30M`, `PT25S` | Kết nối SSE tự đóng sau 30 phút (FE kết nối lại), ping mỗi 25s |
| `EUREKA_URL` / `EUREKA_ENABLED` | `http://localhost:8761/eureka/`, `true` | Discovery |

Thử nhanh không cần backend: publish tay lên exchange `jobbeacon.events` (RabbitMQ management UI `http://localhost:15672`) một `user.upserted` rồi một `recommendation.job-match.digest`, xem email trong Mailpit.

## Test

```bash
mvn test
```

Các test `*IT` dùng Testcontainers (Postgres, RabbitMQ), cần Docker. `JobMatchDigestFlowIT` chạy cả luồng từ RabbitMQ tới email (mock `EmailProvider`) và SSE; `NotificationApiIT` kiểm tra các API.

## Lưu ý vận hành

- Message vào `notification.dlq` không tự xử lý lại: xem trong management UI, sửa nguyên nhân rồi shovel/publish lại.
- Auth tin header `X-User-Id` của gateway (gateway xóa mọi `X-User-*` client gửi lên), nên service chỉ được truy cập qua gateway, không mở cổng ra ngoài.
- Registry SSE nằm trong bộ nhớ: chỉ chạy một instance, hoặc thêm fanout như mục Realtime.
