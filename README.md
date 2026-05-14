# Thread affiliate draft tool

Java backend nho de quan ly link affiliate Shopee va tao comment draft theo template:

```text
[context] [link_affiliate]
```

> Luu y: tool nay chi tao draft de review/copy thu cong. Backend khong luu user/password Threads, khong tu dong comment, scrape, hay spam len Threads. Neu can tich hop dang bai that, hay dung API chinh thuc va chi dang trong pham vi tai khoan/noi dung co quyen.

## Cau truc code

```text
src/main/java/com/threadtool/
  ThreadToolApplication.java        # bootstrap server
  api/ApiHandler.java               # route HTTP va request parsing
  domain/                          # AffiliateLink, CommentTemplate, DraftComment...
  service/ApplicationState.java     # business logic in-memory
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

Kiem tra health:

```bash
curl http://localhost:8080/health
```

## API

### Xem config

```bash
curl http://localhost:8080/api/config
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

### Threads automation bi chan

Nhung endpoint lien quan den user/password, search viral tren Threads, va auto-comment se tra ve `403`:

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
