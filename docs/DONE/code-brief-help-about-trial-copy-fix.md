# Macaco — Help & About: Fix Stale "Free Trial" FAQ Copy (11 locales)

Covers `app/src/main/res/values*/strings.xml` (base + all 11 locale files). Two FAQ strings in
the Premium section of Help & About still say only the Annual plan has a free trial. That's now
wrong — `code-brief-paywall-dynamic-trial.md` (already shipped, see `docs/DONE/`) confirms Play
Console has an **active 7-day-free-trial offer on both the `annual` and `monthly` base plans**,
and `PurchaseScreen.kt` was updated to read trial eligibility dynamically per-package from
RevenueCat instead of hardcoding it to Annual only. The in-app FAQ copy was never updated to
match and now actively misinforms users (a user on the Monthly plan reading this FAQ would
wrongly conclude they have no trial and no refund window).

---

## 1. `help_faq_free_trial_a` — says only Annual has a trial

**Problem:** Base `strings.xml` line 70:

```xml
<!-- BEFORE -->
<string name="help_faq_free_trial_a">Yes — the Annual plan includes a 7-day free trial. You won\'t be charged until the trial ends, and you can cancel at any time during the trial without being billed. Monthly and Lifetime plans do not include a trial period.</string>
```

**Fix:** Update to reflect both Monthly and Annual having the trial, Lifetime still excluded
(Lifetime is a one-time purchase — no trial applies there per RevenueCat config):

```xml
<!-- AFTER -->
<string name="help_faq_free_trial_a">Yes — both the Monthly and Annual plans include a 7-day free trial. You won\'t be charged until the trial ends, and you can cancel at any time during the trial without being billed. The Lifetime plan does not include a trial period.</string>
```

## 2. `help_faq_premium_benefits_a` — same stale "Annual (with 7-day free trial)" parenthetical

**Problem:** Base `strings.xml` line 84:

```xml
<!-- BEFORE -->
<string name="help_faq_premium_benefits_a">Premium unlocks unlimited journal entries, Adventure Reel video slideshows, printed photo books, Google Drive photo backup, and full backup/restore to a file. The Adventures map, swiping between entries, and custom themes are free for everyone. Available as Monthly, Annual (with 7-day free trial), or Lifetime.</string>
```

**Fix:**

```xml
<!-- AFTER -->
<string name="help_faq_premium_benefits_a">Premium unlocks unlimited journal entries, Adventure Reel video slideshows, printed photo books, Google Drive photo backup, and full backup/restore to a file. The Adventures map, swiping between entries, and custom themes are free for everyone. Available as Monthly or Annual (both with a 7-day free trial), or Lifetime.</string>
```

---

## 3. Propagate to all 11 locales

Same two string keys, same fix, in each locale file. Verified current (stale) values below —
replace with the corrected translations.

### `values-fr/strings.xml` (lines 387, 401)

```xml
<string name="help_faq_free_trial_a">Oui — les forfaits Mensuel et Annuel incluent tous deux un essai gratuit de 7 jours. Vous ne serez pas facturé avant la fin de l\'essai, et vous pouvez annuler à tout moment pendant l\'essai sans être facturé. Le forfait À vie ne comprend pas de période d\'essai.</string>
<string name="help_faq_premium_benefits_a">Premium débloque les entrées illimitées, les diaporamas vidéo Adventure Reel, les livres photo imprimés, la sauvegarde des photos sur Google Drive et la sauvegarde/restauration complète dans un fichier. La carte des Aventures, le balayage entre les entrées et les thèmes personnalisés sont gratuits pour tout le monde. Disponible en formule Mensuelle ou Annuelle (toutes deux avec essai gratuit de 7 jours), ou À vie.</string>
```

### `values-es/strings.xml` (lines 387, 401)

```xml
<string name="help_faq_free_trial_a">Sí: los planes Mensual y Anual incluyen una prueba gratuita de 7 días. No se te cobrará hasta que termine la prueba y puedes cancelar en cualquier momento durante la prueba sin que se te facture. El plan De por vida no incluye periodo de prueba.</string>
<string name="help_faq_premium_benefits_a">Premium desbloquea entradas ilimitadas, los vídeos de Adventure Reel, libros de fotos impresos, la copia de seguridad de fotos en Google Drive y la copia de seguridad y restauración completa en un archivo. El mapa de Aventuras, deslizar entre entradas y los temas personalizados son gratis para todos. Disponible como Mensual o Anual (ambos con prueba gratuita de 7 días), o De por vida.</string>
```

### `values-pl/strings.xml` (lines 395, 409)

```xml
<string name="help_faq_free_trial_a">Tak — zarówno plan miesięczny, jak i roczny obejmują 7-dniowy bezpłatny okres próbny. Opłata zostanie pobrana dopiero po zakończeniu okresu próbnego, a w jego trakcie możesz anulować w dowolnym momencie bez obciążenia. Plan dożywotni nie obejmuje okresu próbnego.</string>
<string name="help_faq_premium_benefits_a">Premium odblokowuje nieograniczone wpisy, pokazy wideo Adventure Reel, drukowane fotoksiążki, kopię zapasową zdjęć w Google Drive oraz pełną kopię zapasową/przywracanie do pliku. Mapa Przygód, przesuwanie między wpisami i własne motywy są bezpłatne dla wszystkich. Dostępne jako plan miesięczny lub roczny (oba z 7-dniowym bezpłatnym okresem próbnym) lub dożywotni.</string>
```

### `values-sv/strings.xml` (lines 360, 374)

```xml
<string name="help_faq_free_trial_a">Ja — både månads- och årsplanen inkluderar en 7 dagars gratis provperiod. Du debiteras inte förrän provperioden tar slut, och du kan avbryta när som helst under provperioden utan att debiteras. Livstidsplanen innehåller ingen provperiod.</string>
<string name="help_faq_premium_benefits_a">Premium låser upp obegränsade inlägg, Adventure Reel-videobildspel, tryckta fotoböcker, fotosäkerhetskopiering via Google Drive samt fullständig säkerhetskopiering/återställning till en fil. Äventyrskartan, svep mellan inlägg och anpassade teman är gratis för alla. Tillgängligt som Månads- eller Årsabonnemang (båda med 7 dagars gratis provperiod) eller Livstidsabonnemang.</string>
```

### `values-ja/strings.xml` (lines 383, 397)

```xml
<string name="help_faq_free_trial_a">はい — 月額プランと年間プランのどちらにも7日間の無料トライアルが含まれます。トライアルが終了するまで料金は発生せず、トライアル期間中はいつでもキャンセルでき、請求されません。買い切りプランにトライアル期間はありません。</string>
<string name="help_faq_premium_benefits_a">プレミアムでは、無制限の記録、アドベンチャーリールの動画スライドショー、フォトブックの印刷、Google Driveへの写真バックアップ、ファイルへの完全なバックアップ／復元が利用できます。アドベンチャーマップ、記録間のスワイプ、カスタムテーマはすべてのユーザーに無料で提供されます。月額・年額（どちらも7日間の無料トライアル付き）・買い切りから選べます。</string>
```

### `values-nl/strings.xml` (lines 387, 401)

```xml
<string name="help_faq_free_trial_a">Ja — zowel het Maand- als het Jaarabonnement bevatten een gratis proefperiode van 7 dagen. Er worden pas kosten in rekening gebracht nadat de proefperiode is afgelopen, en je kunt tijdens de proefperiode op elk moment opzeggen zonder kosten. Het Lifetime-abonnement heeft geen proefperiode.</string>
<string name="help_faq_premium_benefits_a">Premium ontgrendelt onbeperkte items, Adventure Reel-videodiavoorstellingen, gedrukte fotoboeken, fotoback-up via Google Drive en volledige back-up/herstel naar een bestand. De Avonturenkaart, vegen tussen items en aangepaste thema\'s zijn voor iedereen gratis. Beschikbaar als Maandelijks of Jaarlijks (beide met gratis proefperiode van 7 dagen), of Lifetime.</string>
```

### `values-pt/strings.xml` (lines 387, 401)

```xml
<string name="help_faq_free_trial_a">Sim — os planos Mensal e Anual incluem um teste gratuito de 7 dias. Você não será cobrado até o fim do teste e pode cancelar a qualquer momento durante o teste sem ser cobrado. O plano Vitalício não inclui período de teste.</string>
<string name="help_faq_premium_benefits_a">O Premium desbloqueia entradas ilimitadas, as apresentações de vídeo Adventure Reel, livros de fotos impressos, backup de fotos no Google Drive e o backup/restauração completos em um arquivo. O mapa de Aventuras, deslizar entre entradas e temas personalizados são gratuitos para todos. Disponível como Mensal ou Anual (ambos com teste gratuito de 7 dias), ou Vitalício.</string>
```

### `values-it/strings.xml` (lines 387, 401)

```xml
<string name="help_faq_free_trial_a">Sì: sia il piano Mensile sia quello Annuale includono una prova gratuita di 7 giorni. Non ti verrà addebitato nulla fino al termine della prova e puoi annullare in qualsiasi momento durante la prova senza alcun addebito. Il piano A vita non include un periodo di prova.</string>
<string name="help_faq_premium_benefits_a">Premium sblocca voci illimitate, le presentazioni video Adventure Reel, i libri fotografici stampati, il backup delle foto su Google Drive e il backup/ripristino completo su file. La mappa delle Avventure, lo scorrimento tra le voci e i temi personalizzati sono gratuiti per tutti. Disponibile come Mensile o Annuale (entrambi con prova gratuita di 7 giorni), o A vita.</string>
```

### `values-zh-rCN/strings.xml` (lines 383, 397)

```xml
<string name="help_faq_free_trial_a">有——月度方案和年度方案均包含 7 天免费试用。试用结束前不会向你收费，并且你可以在试用期间随时取消而不会被扣费。终身方案不包含试用期。</string>
<string name="help_faq_premium_benefits_a">高级版解锁无限日志条目、冒险短片视频幻灯片、印刷相册、通过 Google Drive 进行照片备份，以及完整的文件备份与恢复。冒险地图、在条目间滑动和自定义主题所有用户均可免费使用。提供月度或年度方案（均含 7 天免费试用），或终身方案。</string>
```

### `values-de/strings.xml` (lines 398, 412)

```xml
<string name="help_faq_free_trial_a">Ja — sowohl der Monats- als auch der Jahresplan enthalten eine 7-tägige kostenlose Testphase. Dir wird erst nach Ablauf der Testphase etwas berechnet, und du kannst jederzeit während der Testphase kündigen, ohne dass Kosten entstehen. Der Lifetime-Plan enthält keine Testphase.</string>
<string name="help_faq_premium_benefits_a">Premium schaltet unbegrenzte Einträge, Adventure-Reel-Video-Diashows, gedruckte Fotobücher, Foto-Backup über Google Drive sowie die vollständige Sicherung/Wiederherstellung als Datei frei. Die Abenteuerkarte, das Wischen zwischen Einträgen und eigene Designs sind für alle kostenlos. Verfügbar als Monats- oder Jahresabo (beide mit 7-tägiger kostenloser Testphase) oder als Lifetime-Abo.</string>
```

---

## Scope note

`PurchaseScreen.kt` (the actual paywall) already reads trial eligibility dynamically per package
from RevenueCat (`code-brief-paywall-dynamic-trial.md`, shipped) — **not touched by this brief**.
This brief only fixes the static Help & About FAQ strings, which don't read from RevenueCat and
were left behind when the trial was extended to Monthly.

If RevenueCat's offer configuration changes again (e.g. trial removed from Monthly, or added to
Lifetime), these two FAQ strings will go stale again since they're hardcoded — flagged here for
awareness, not in scope to fix now.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Fix `help_faq_free_trial_a` to say Monthly + Annual both have 7-day trial (EN) | `values/strings.xml` |
| 2 | Fix `help_faq_premium_benefits_a` parenthetical to match (EN) | `values/strings.xml` |
| 3 | Same two strings, translated | `values-fr/strings.xml` |
| 4 | Same two strings, translated | `values-es/strings.xml` |
| 5 | Same two strings, translated | `values-pl/strings.xml` |
| 6 | Same two strings, translated | `values-sv/strings.xml` |
| 7 | Same two strings, translated | `values-ja/strings.xml` |
| 8 | Same two strings, translated | `values-nl/strings.xml` |
| 9 | Same two strings, translated | `values-pt/strings.xml` |
| 10 | Same two strings, translated | `values-it/strings.xml` |
| 11 | Same two strings, translated | `values-zh-rCN/strings.xml` |
| 12 | Same two strings, translated | `values-de/strings.xml` |
