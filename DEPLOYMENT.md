# Bezpečné nasazení existujícího Docker obrazu na Render

Produkční tajné hodnoty nepatří do Dockerfile, image vrstev, `render.yaml`, zdrojového kódu ani Git historie. Render je předá kontejneru až při běhu.

> **Důležité:** obraz `docker.io/utrh/elektronickydenik:latest` musí být po těchto bezpečnostních změnách znovu sestaven a publikován. Nasazení starého obrazu nezpřístupní opravy, které existují pouze ve zdrojovém kódu. Pro produkci preferujte neměnný tag nebo digest, například `docker.io/utrh/elektronickydenik@sha256:<ověřený-digest>`, ne pohyblivý `latest`.

## 1. Bezpečnostní preflight

1. Vytvořte a obnovou ověřte zálohu produkční databáze.
2. Na kopii databáze spusťte image s `JPA_DDL_AUTO=validate`. Produkce nemá používat `update`, `create` ani `create-drop`.
3. Ověřte, že image je pro `linux/amd64`, naslouchá na `0.0.0.0:$PORT` a neobsahuje `.env`, klíče ani produkční properties.
4. Vygenerujte nový `REMEMBER_ME_KEY` z alespoň 32 kryptograficky náhodných bajtů. Nepoužívejte heslo uživatele ani znovu použitý aplikační secret.
5. Preferujte dedikovaný SSH privátní klíč s minimálními právy před heslem. Nezávisle ověřte SSH host key a připravte řádek `known_hosts`.
6. Nejprve nasaďte staging proti kopii databáze.

## 2. Vytvoření image-backed Web Service

1. Přihlaste se do **Render Dashboard**.
2. Klikněte **+ New** → **Web Service**.
3. V části **Source Code** zvolte **Existing Image**.
4. Do **Image URL** vložte `docker.io/utrh/elektronickydenik:latest` (lépe ověřený digest).
5. Je-li Docker Hub repository veřejný, credential nechte prázdný. Je-li privátní, klikněte **Add credential**, vyberte Docker Hub a vložte Docker Hub username a access token s pouze read oprávněním. Token nepatří mezi aplikační env vars.
6. Po ověření obrazu klikněte **Connect**.
7. Nastavte název, region (ideálně stejný jako databáze) a plán. V **Advanced** nastavte **Health Check Path** na `/health`. Docker Command nechte prázdný; použije se `ENTRYPOINT` z image.
8. Před prvním ostrým deployem vložte runtime konfiguraci podle další části.

Image-backed služba sama nezachytí změnu tagu `latest`. Novou verzi spusťte přes **Manual Deploy** → **Deploy latest reference**. Digest poskytuje spolehlivější audit i rollback; staré digesty musí zůstat v Docker Hubu.

## 3. Ruční vložení proměnných v Render UI

Otevřete vytvořenou službu a v levém menu klikněte **Environment**. V části **Environment Variables** použijte **+ Add Environment Variable**, vyplňte `Key` a `Value` pro každý řádek níže a nakonec zvolte **Save and deploy**. Funkci **Add from .env** lze použít, ale lokální `.env` se nikdy neuploaduje do Gitu ani nepřidává do image.

### Povinné runtime hodnoty

| Key | Hodnota / účel | Citlivé |
|---|---|---|
| `DATASOURCE_URL` | JDBC URL; při SSH tunelu `jdbc:mysql://127.0.0.1:3306/<db>?serverTimezone=UTC` | ano |
| `DATASOURCE_USER` | databázový účet s nejmenšími nutnými právy | ano |
| `DATASOURCE_PASSWORD` | databázové heslo | ano |
| `JPA_DDL_AUTO` | přesně `validate` | ne |
| `MAIL_HOST` | SMTP host | ne |
| `MAIL_PORT` | obvykle `587` | ne |
| `MAIL_USERNAME` | SMTP uživatel | ano |
| `MAIL_PASSWORD` | SMTP heslo/token | ano |
| `REMEMBER_ME_KEY` | stabilní náhodný secret, minimálně 32 náhodných bajtů | ano |
| `SESSION_COOKIE_SECURE` | přesně `true` | ne |
| `SESSION_COOKIE_NAME` | přesně `__Host-JSESSIONID` | ne |
| `SESSION_COOKIE_SAME_SITE` | přesně `strict` | ne |
| `SESSION_TIMEOUT` | doporučeno `20m` | ne |
| `APPLICATION_LOG_LEVEL` | `INFO` | ne |

`PORT` Render nastavuje automaticky. Aplikace jeho hodnotu respektuje; není nutné ji ručně přidávat.

### Pokud databáze vyžaduje SSH tunel

| Key | Doporučená hodnota |
|---|---|
| `SSH_ENABLED` | `true` |
| `SSH_HOST` | hostname SSH serveru |
| `SSH_PORT` | `22` |
| `SSH_USER` | dedikovaný omezený účet |
| `SSH_PRIVATE_KEY_FILE` | `/etc/secrets/ssh_private_key` |
| `SSH_REMOTE_DB_HOST` | obvykle `127.0.0.1` |
| `SSH_REMOTE_DB_PORT` | `3306` |
| `SSH_LOCAL_PORT` | `3306` |
| `SSH_CONNECT_TIMEOUT_MS` | `10000` |
| `SSH_CONNECT_ATTEMPTS` | `6` |
| `SSH_RECONNECT_INTERVAL_MS` | `15000` |
| `SSH_STRICT_HOST_KEY_CHECKING` | přesně `yes` |
| `SSH_KNOWN_HOSTS_FILE` | `/etc/secrets/ssh_known_hosts` |

V téže stránce **Environment** přejděte do **Secret Files** → **+ Add Secret File**:

- Filename `ssh_private_key`: vložte obsah dedikovaného privátního klíče.
- Filename `ssh_known_hosts`: vložte nezávisle ověřený `known_hosts` řádek.

Render je v Docker službě zpřístupní jako `/etc/secrets/<filename>`. Pokud výjimečně použijete heslo, nepřidávejte `SSH_PRIVATE_KEY_FILE`, ale vložte `SSH_PASSWORD` jako env var. Nikdy nepoužívejte obě metody bez důvodu.

Volitelné bootstrap hodnoty `SUPER_ADMIN_EMAIL` a `SUPER_ADMIN_INITIAL_PASSWORD` nastavte jen pro první start. Jakmile je účet vytvořen a přihlášení ověřeno, odstraňte zejména počáteční heslo z Environment a zvolte **Save and deploy**.

## 4. Smoke test a provoz

Po deployi zkontrolujte **Logs** a health endpoint. Ověřte přihlášení, odhlášení, registraci, reset hesla, oprávnění k cizímu vozidlu, CSRF odmítnutí POST bez tokenu, offline sync, e-mail a atributy cookie `Secure; HttpOnly; SameSite=Strict`. Chybová odpověď nesmí obsahovat stack trace, SQL parametry, hesla, cookies ani osobní údaje.

Při problému vraťte předchozí image digest. Databázová změna musí mít vlastní, předem otestovaný rollback; rollback image automaticky nevrátí databázi.

## 5. Git a secrets před pushnutím

`.gitignore` chrání pouze nové netrackované soubory. Neodstraňuje obsah ze starších commitů. Audit historie tohoto repository odhalil dříve commitovaný inicializační credential, proto před veřejným sdílením:

1. Považujte dřívější credential a související účet za kompromitovaný a okamžitě je změňte/odvolejte.
2. Proveďte plný secret scan všech větví a tagů (např. Gitleaks).
3. Vyčistěte historii nástrojem `git filter-repo` podle přesně potvrzených nálezů. Jde o přepis historie: koordinujte force-push, znovu vytvořte tagy a nechte všechny spolupracovníky repository čerstvě naklonovat.
4. Po přepisu scan zopakujte. Teprve nulový výsledek je podmínkou sdílení.
5. Na GitHubu zapněte secret scanning/push protection, Dependabot security updates a ochranu `main` s povinným CI.

Před každým commitem použijte alespoň:

```powershell
git status --short
git diff --check
git diff --cached
```

Nestageujte `.env`, produkční properties, logy, databázové dumpy, IDE složky, lokální JDK ani build výstupy. Skutečné secrets neposílejte ani v issue, e-mailu, chatu nebo diagnostickém logu.
