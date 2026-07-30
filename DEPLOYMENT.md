# Bezpečné nasazení na Render

Tento dokument neobsahuje produkční hodnoty. Hesla, privátní klíče, databázové
údaje a soubor `ssh_known_hosts` nikdy necommitujte.

## 1. Povinný preflight

1. Vytvořte a ověřte obnovitelnou zálohu produkční databáze.
2. Na kopii databáze ověřte, že aplikace startuje s `JPA_DDL_AUTO=validate`.
   Pokud validace selže, připravte explicitní databázovou migraci; v produkci
   nepoužívejte automatické `update`.
3. Od poskytovatele SSH nezávisle získejte otisk hostitelského ED25519 klíče.
   Teprve po porovnání otisku připravte standardní `known_hosts` řádek.
4. Preferujte samostatný SSH klíč určený jen pro Render před sdíleným heslem.
   Účet musí mít pouze právo vytvořit TCP forward k databázi.
5. Vygenerujte nový náhodný `REMEMBER_ME_KEY` s alespoň 32 náhodnými bajty.
6. Proveďte první nasazení na staging službu a databázovou kopii.

## 2. Render

Repozitář obsahuje `render.yaml`. U existující služby lze stejné hodnoty
nastavit ručně v Dashboardu, nebo službu spravovat jako Blueprint.

V Render Environment nastavte hodnoty uvedené v `.env.example`. Citlivé
hodnoty patří pouze do Render Environment:

- `SSH_HOST`, `SSH_USER` a dočasně `SSH_PASSWORD`, nebo raději
  `SSH_PRIVATE_KEY_FILE=/etc/secrets/ssh_private_key`;
- `DATASOURCE_URL`, `DATASOURCE_USER`, `DATASOURCE_PASSWORD`;
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`;
- stabilní náhodný `REMEMBER_ME_KEY`.

V Render Secret Files vytvořte:

- `/etc/secrets/ssh_known_hosts` s nezávisle ověřeným host key;
- volitelně `/etc/secrets/ssh_private_key` s dedikovaným privátním klíčem.

Nastavení služby:

- runtime: Docker;
- health check: `/health`;
- auto-deploy: až po úspěšných CI kontrolách;
- `JPA_DDL_AUTO=validate`;
- `SSH_STRICT_HOST_KEY_CHECKING=yes`;
- `SESSION_COOKIE_SECURE=true`.

Po prvním vytvoření superadmina odstraňte
`SUPER_ADMIN_INITIAL_PASSWORD` z Render Environment.

## 3. Bezpečný GitHub postup

Pracujte na samostatné větvi a stageujte interaktivně. V tomto repozitáři
nepoužívejte naslepo `git add .`.

```powershell
git switch -c codex/deployment-hardening
git status --short
git diff --check
git add -p
git diff --cached --stat
git diff --cached
git commit -m "Harden Render deployment and application security"
git push -u origin codex/deployment-hardening
```

Na GitHubu otevřete pull request. Před sloučením musí projít workflow `CI`.
V nastavení repozitáře zapněte push protection, secret scanning, Dependabot
alerts/security updates a ochranu větve `main` s povinnou CI kontrolou.

## 4. Nasazení a rollback

1. Nasaďte staging proti databázové kopii.
2. Ověřte přihlášení, registraci, reset hesla, práci s autorizovaným i
   neautorizovaným strojem, synchronizaci a odeslání e-mailu.
3. Sledujte log od navázání SSH tunelu až po `Started AuthdemoApplication`.
4. Produkční deploy spusťte mimo špičku a sledujte chybovost i odezvu.
5. Pokud nový deploy neprojde health checkem nebo funkčními smoke testy,
   vraťte předchozí Render deploy. Databázové změny musí mít samostatný,
   předem otestovaný rollback.

## 5. Logy pro diagnostiku

Sdílejte úsek od `Connecting SSH tunnel` po `Application run failed` nebo
`Started AuthdemoApplication`. Před odesláním odstraňte hesla, celé JDBC URL,
e-maily, ověřovací kódy, cookies a osobní údaje. Stack trace a časové značky
ponechte.
