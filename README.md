# Thread affiliate draft tool

Java backend nho de quan ly link affiliate Shopee, tao comment draft theo template, va publish bai viet len Threads bang Threads Graph API chinh thuc.

```text
[context] [link_affiliate]
```

> Luu y: backend khong luu user/password Threads, khong tu dong comment vao bai cua nguoi khac, khong scrape/search viral tren Threads. Phan publish chi dung Threads Graph API voi access token cua tai khoan duoc cap quyen.

## Cau truc code

```text
src/main/java/com/threadtool/
  ThreadToolApplication.java        # bootstrap server
  api/ApiHandler.java               # route HTTP va request parsing
  domain/                          # AffiliateLink, CommentTemplate, DraftComment...
  service/ApplicationState.java     # business logic in-memory
  service/HttpThreadsGraphClient.java
  service/ThreadsPublishService.java
  service/ThreadsAutomationGuard.java
  util/Json.java                    # JSON parser/stringifier nho
  error/ApiException.java
```

## Yeu cau

- JDK 21+
- Khong can Maven/Gradle

## Chay local

```bash
javac -d out $(rg --files -g '*.java' src/main/java)
java -cp out com.threadtool.ThreadToolApplication 8080
```

Co the cau hinh Threads Graph API qua bien moi truong:

```bash
export THREADS_USER_ID="9213298915445740"
export THREADS_ACCESS_TOKEN="your_threads_access_token"
java -cp out com.threadtool.ThreadToolApplication 8080
```

Kiem tra health:

```bash
curl http://localhost:8080/health
```

## API

### Xem config

```bash
curl http://localhost:8080/api/config
```

### Cau hinh Threads Graph API

Dung access token Graph API chinh thuc, khong dung user/password Threads.

```bash
curl -X POST http://localhost:8080/api/config/threads-graph \
  -H 'Content-Type: application/json' \
  -d '{
    "threadsUserId":"9213298915445740",
    "accessToken":"your_threads_access_token"
  }'
```

Kiem tra config se chi tra ve token dang redacted:

```bash
curl http://localhost:8080/api/config/threads-graph
```

### Cap nhat template

Template bat buoc co ca `[context]` va `[link_affiliate]`.

```bash
curl -X PUT http://localhost:8080/api/config/template \
  -H 'Content-Type: application/json' \
  -d '{"template":"[context] [link_affiliate]"}'
```

### Them template theo ngon ngu

Template co `language`, vi du `vi`, `en`, hoac `any`. Khi tao draft tu candidate, backend se uu tien template active trung ngon ngu.

```bash
curl -X POST http://localhost:8080/api/config/templates \
  -H 'Content-Type: application/json' \
  -d '{
    "label":"vietnamese-soft",
    "language":"vi",
    "template":"[context] Link minh de day nha: [link_affiliate]",
    "active":true
  }'
```

Lay danh sach template:

```bash
curl http://localhost:8080/api/config/templates
```

### Them/cap nhat link affiliate

```bash
curl -X POST http://localhost:8080/api/config/affiliate-links \
  -H 'Content-Type: application/json' \
  -d '{"label":"shopee-main","url":"https://s.shopee.vn/your-affiliate-link","active":true}'
```

### Tao draft comment

```bash
curl -X POST http://localhost:8080/api/drafts \
  -H 'Content-Type: application/json' \
  -d '{
    "postUrl":"https://www.threads.net/@example/post/123",
    "context":"San pham nay dang hot, ban nao can thi xem o day:",
    "affiliateLabel":"shopee-main",
    "language":"vi",
    "templateLabel":"vietnamese-soft"
  }'
```

Response se co `commentText`:

```text
San pham nay dang hot, ban nao can thi xem o day: https://s.shopee.vn/your-affiliate-link
```

### Nhap candidate viral post va tao draft

Endpoint nay danh cho luong hop le: nguoi dung hoac he thong noi bo dua URL/context vao backend. Backend khong tu quet Threads.

```bash
curl -X POST http://localhost:8080/api/candidates \
  -H 'Content-Type: application/json' \
  -d '{
    "postUrl":"https://www.threads.net/@example/post/viral",
    "context":"Bai nay dang viral ve deal nay",
    "language":"vi",
    "engagementScore":12000,
    "affiliateLabel":"shopee-main"
  }'
```

Lay danh sach candidate:

```bash
curl http://localhost:8080/api/candidates
```

### Duyet / tu choi draft

```bash
curl -X POST http://localhost:8080/api/drafts/1/approve
curl -X POST http://localhost:8080/api/drafts/1/reject
```

### Export draft da duyet

```bash
curl -X POST http://localhost:8080/api/export \
  -H 'Content-Type: application/json' \
  -d '{"status":"APPROVED"}'
```

## Publish bai viet Threads theo workflow n8n

Tat ca endpoint publish co the dung credentials da cau hinh qua `/api/config/threads-graph`, hoac truyen truc tiep `threadsUserId` va `accessToken` trong body.

### Post text

Tuong duong `POST /{threads_user_id}/threads` voi `media_type=TEXT`, sau do `POST /threads_publish`.

```bash
curl -X POST http://localhost:8080/api/threads/publish/text \
  -H 'Content-Type: application/json' \
  -d '{
    "text":"Noi dung bai viet Threads"
  }'
```

### Post single image

```bash
curl -X POST http://localhost:8080/api/threads/publish/image \
  -H 'Content-Type: application/json' \
  -d '{
    "text":"Caption anh",
    "imageUrl":"https://example.com/image.jpg"
  }'
```

### Post carousel nhieu anh

Backend se tao container con `is_carousel_item=true` cho tung anh, gom `children`, roi publish container cha.

```bash
curl -X POST http://localhost:8080/api/threads/publish/carousel \
  -H 'Content-Type: application/json' \
  -d '{
    "text":"Caption carousel",
    "imageUrls":[
      "https://example.com/1.jpg",
      "https://example.com/2.jpg"
    ]
  }'
```

Co the truyen `imageUrls` la chuoi phan tach bang dau phay neu lay tu Google Sheets:

```json
{"imageUrls":"https://example.com/1.jpg, https://example.com/2.jpg"}
```

### Post video

Mac dinh backend tao video container va publish ngay. Neu muon doi Graph API xu ly video xong truoc khi publish, bat `waitForReady`.

```bash
curl -X POST http://localhost:8080/api/threads/publish/video \
  -H 'Content-Type: application/json' \
  -d '{
    "text":"Caption video",
    "videoUrl":"https://example.com/video.mp4",
    "waitForReady":true,
    "maxStatusChecks":6,
    "statusCheckIntervalMillis":10000
  }'
```

### Check status / publish container co san

Neu muon tach workflow giong n8n:

```bash
curl -X POST http://localhost:8080/api/threads/containers/status \
  -H 'Content-Type: application/json' \
  -d '{"creationId":"container_id"}'

curl -X POST http://localhost:8080/api/threads/publish/container \
  -H 'Content-Type: application/json' \
  -d '{"creationId":"container_id"}'
```

### Threads automation bi chan

Nhung endpoint lien quan den user/password, search viral tren Threads, va auto-comment vao bai cua nguoi khac se tra ve `403`:

```bash
curl -X POST http://localhost:8080/api/config/threads-credentials
curl -X POST http://localhost:8080/api/threads/login
curl -X POST http://localhost:8080/api/threads/search-viral
curl -X POST http://localhost:8080/api/threads/comment
```

## Test

```bash
javac -d out $(rg --files -g '*.java' src/main/java src/test/java)
java -cp out com.threadtool.ThreadToolApplicationTest
```
