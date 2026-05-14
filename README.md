# Thread affiliate draft tool

Java backend nho de quan ly link affiliate Shopee va tao comment draft theo template:

```text
[context] [link_affiliate]
```

> Luu y: tool nay chi tao draft de review/copy thu cong. Backend khong tu dong comment, scrape, hay spam len Threads. Neu can tich hop dang bai that, hay dung API chinh thuc va chi dang trong pham vi tai khoan/noi dung co quyen.

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
    "affiliateLabel":"shopee-main"
  }'
```

Response se co `commentText`:

```text
San pham nay dang hot, ban nao can thi xem o day: https://s.shopee.vn/your-affiliate-link
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

## Test

```bash
javac -d out $(rg --files -g '*.java' src/main/java src/test/java)
java -cp out com.threadtool.ThreadToolApplicationTest
```
