# Macaco — Locale Catalogues: Complete All 10 Translations (Paywall + Swedish Backlog)

Adds every missing string to the 10 translated locale catalogues. No Kotlin changes — this brief
touches only `app/src/main/res/values-*/strings.xml`. Closes findings S1 + S2 from
`docs/qa-report-2026-07-11.md` (verified against source 2026-07-11: base catalogue has 368 keys;
`de` is missing 18, `es/fr/it/ja/nl/pl/pt/zh-rCN` 20 each, `sv` 47).

Why it matters: the multi-plan paywall (`PurchaseScreen.kt`) references ~18 `purchase_*` keys that
exist only in the base catalogue, so the most conversion-sensitive screen renders half-English in
every translated market. Swedish is additionally missing the map, reminder-notification, trip-field,
and share strings.

---

## Change 1 — Add the missing keys to each locale

All translations below are ready to paste. Rules:

- Insert each block **inside** the existing `<resources>` element of the named file. Placement
  next to related keys (e.g. other `purchase_*` keys) is preferred but not required.
- Keep placeholders exactly as-is: `%s`, `%1$d`, `%1$s`, `%2$d`.
- Apostrophes are already escaped (`\'`) — keep the escaping.
- `values-de` already has `settings_drive_connected_as` / `settings_drive_disconnect`; its block
  correctly omits them. Every other locale gets 20 keys. `values-sv` gets 47 (20 + 27 backlog).
- After adding, verify each locale compiles (`assembleDebug`) — a stray unescaped apostrophe is
  the usual failure.

### `values-de/strings.xml` (18 keys)

```xml
<string name="help_feedback_feature_subject">Macaco – Funktionswunsch</string>
<string name="help_feedback_issue_subject">Macaco – Problembericht</string>
<string name="purchase_best_value">Bestes Angebot</string>
<string name="purchase_cancel_anytime">Jederzeit kündbar</string>
<string name="purchase_cta_annual">7 Tage gratis testen</string>
<string name="purchase_cta_lifetime">Lifetime kaufen — %s</string>
<string name="purchase_cta_monthly">Monatlich starten — %s</string>
<string name="purchase_free_trial">7 Tage gratis, danach %s / Jahr</string>
<string name="purchase_most_popular">Am beliebtesten</string>
<string name="purchase_once">einmalig</string>
<string name="purchase_own_forever">Einmal zahlen, für immer nutzen</string>
<string name="purchase_per_month">/ Monat</string>
<string name="purchase_per_year">/ Jahr</string>
<string name="purchase_plan_annual">Jährlich</string>
<string name="purchase_plan_lifetime">Lifetime</string>
<string name="purchase_plan_monthly">Monatlich</string>
<string name="purchase_save_50">Spare 50 % gegenüber monatlich</string>
<string name="purchase_trial_note">Während der Testphase jederzeit kündbar – es wird nichts berechnet</string>
```

### `values-es/strings.xml` (20 keys)

```xml
<string name="help_feedback_feature_subject">Macaco – Sugerencia de función</string>
<string name="help_feedback_issue_subject">Macaco – Informe de problema</string>
<string name="purchase_best_value">Mejor oferta</string>
<string name="purchase_cancel_anytime">Cancela cuando quieras</string>
<string name="purchase_cta_annual">Prueba 7 días gratis</string>
<string name="purchase_cta_lifetime">Compra de por vida — %s</string>
<string name="purchase_cta_monthly">Empezar mensual — %s</string>
<string name="purchase_free_trial">7 días gratis, luego %s / año</string>
<string name="purchase_most_popular">Más popular</string>
<string name="purchase_once">pago único</string>
<string name="purchase_own_forever">Paga una vez y es tuyo para siempre</string>
<string name="purchase_per_month">/ mes</string>
<string name="purchase_per_year">/ año</string>
<string name="purchase_plan_annual">Anual</string>
<string name="purchase_plan_lifetime">De por vida</string>
<string name="purchase_plan_monthly">Mensual</string>
<string name="purchase_save_50">Ahorra un 50 % frente al plan mensual</string>
<string name="purchase_trial_note">Cancela durante la prueba y no se te cobrará nada</string>
<string name="settings_drive_connected_as">Conectado como %s</string>
<string name="settings_drive_disconnect">Desconectar</string>
```

### `values-fr/strings.xml` (20 keys)

```xml
<string name="help_feedback_feature_subject">Macaco – Suggestion de fonctionnalité</string>
<string name="help_feedback_issue_subject">Macaco – Signalement de problème</string>
<string name="purchase_best_value">Meilleure offre</string>
<string name="purchase_cancel_anytime">Annulable à tout moment</string>
<string name="purchase_cta_annual">Essai gratuit de 7 jours</string>
<string name="purchase_cta_lifetime">Accès à vie — %s</string>
<string name="purchase_cta_monthly">Commencer en mensuel — %s</string>
<string name="purchase_free_trial">7 jours gratuits, puis %s / an</string>
<string name="purchase_most_popular">Le plus populaire</string>
<string name="purchase_once">paiement unique</string>
<string name="purchase_own_forever">Payez une fois, gardez-le pour toujours</string>
<string name="purchase_per_month">/ mois</string>
<string name="purchase_per_year">/ an</string>
<string name="purchase_plan_annual">Annuel</string>
<string name="purchase_plan_lifetime">À vie</string>
<string name="purchase_plan_monthly">Mensuel</string>
<string name="purchase_save_50">Économisez 50 % par rapport au mensuel</string>
<string name="purchase_trial_note">Annulez pendant l\'essai et vous ne serez pas débité</string>
<string name="settings_drive_connected_as">Connecté en tant que %s</string>
<string name="settings_drive_disconnect">Déconnecter</string>
```

### `values-it/strings.xml` (20 keys)

```xml
<string name="help_feedback_feature_subject">Macaco – Richiesta funzionalità</string>
<string name="help_feedback_issue_subject">Macaco – Segnalazione problema</string>
<string name="purchase_best_value">Migliore offerta</string>
<string name="purchase_cancel_anytime">Disdici quando vuoi</string>
<string name="purchase_cta_annual">Prova gratis per 7 giorni</string>
<string name="purchase_cta_lifetime">Acquista A vita — %s</string>
<string name="purchase_cta_monthly">Inizia il mensile — %s</string>
<string name="purchase_free_trial">7 giorni gratis, poi %s / anno</string>
<string name="purchase_most_popular">Il più popolare</string>
<string name="purchase_once">una tantum</string>
<string name="purchase_own_forever">Paghi una volta, è tuo per sempre</string>
<string name="purchase_per_month">/ mese</string>
<string name="purchase_per_year">/ anno</string>
<string name="purchase_plan_annual">Annuale</string>
<string name="purchase_plan_lifetime">A vita</string>
<string name="purchase_plan_monthly">Mensile</string>
<string name="purchase_save_50">Risparmia il 50% rispetto al mensile</string>
<string name="purchase_trial_note">Disdici durante la prova e non ti verrà addebitato nulla</string>
<string name="settings_drive_connected_as">Connesso come %s</string>
<string name="settings_drive_disconnect">Disconnetti</string>
```

### `values-ja/strings.xml` (20 keys)

```xml
<string name="help_feedback_feature_subject">Macaco – 機能リクエスト</string>
<string name="help_feedback_issue_subject">Macaco – 不具合報告</string>
<string name="purchase_best_value">一番お得</string>
<string name="purchase_cancel_anytime">いつでもキャンセル可能</string>
<string name="purchase_cta_annual">7日間無料で試す</string>
<string name="purchase_cta_lifetime">買い切りを購入 — %s</string>
<string name="purchase_cta_monthly">月額プランを開始 — %s</string>
<string name="purchase_free_trial">7日間無料、その後 %s / 年</string>
<string name="purchase_most_popular">一番人気</string>
<string name="purchase_once">一回払い</string>
<string name="purchase_own_forever">一度の支払いでずっと使える</string>
<string name="purchase_per_month">/ 月</string>
<string name="purchase_per_year">/ 年</string>
<string name="purchase_plan_annual">年額</string>
<string name="purchase_plan_lifetime">買い切り</string>
<string name="purchase_plan_monthly">月額</string>
<string name="purchase_save_50">月額より50%お得</string>
<string name="purchase_trial_note">無料期間中にキャンセルすれば料金はかかりません</string>
<string name="settings_drive_connected_as">%s として接続中</string>
<string name="settings_drive_disconnect">接続を解除</string>
```

### `values-nl/strings.xml` (20 keys)

```xml
<string name="help_feedback_feature_subject">Macaco – Functieverzoek</string>
<string name="help_feedback_issue_subject">Macaco – Probleemmelding</string>
<string name="purchase_best_value">Beste deal</string>
<string name="purchase_cancel_anytime">Altijd opzegbaar</string>
<string name="purchase_cta_annual">Probeer 7 dagen gratis</string>
<string name="purchase_cta_lifetime">Koop Lifetime — %s</string>
<string name="purchase_cta_monthly">Start maandelijks — %s</string>
<string name="purchase_free_trial">7 dagen gratis, daarna %s / jaar</string>
<string name="purchase_most_popular">Populairste keuze</string>
<string name="purchase_once">eenmalig</string>
<string name="purchase_own_forever">Eén keer betalen, voor altijd van jou</string>
<string name="purchase_per_month">/ maand</string>
<string name="purchase_per_year">/ jaar</string>
<string name="purchase_plan_annual">Jaarlijks</string>
<string name="purchase_plan_lifetime">Lifetime</string>
<string name="purchase_plan_monthly">Maandelijks</string>
<string name="purchase_save_50">Bespaar 50% t.o.v. maandelijks</string>
<string name="purchase_trial_note">Zeg op tijdens de proefperiode en je betaalt niets</string>
<string name="settings_drive_connected_as">Verbonden als %s</string>
<string name="settings_drive_disconnect">Verbinding verbreken</string>
```

### `values-pl/strings.xml` (20 keys)

```xml
<string name="help_feedback_feature_subject">Macaco – Propozycja funkcji</string>
<string name="help_feedback_issue_subject">Macaco – Zgłoszenie problemu</string>
<string name="purchase_best_value">Najlepsza oferta</string>
<string name="purchase_cancel_anytime">Anuluj w każdej chwili</string>
<string name="purchase_cta_annual">Wypróbuj 7 dni za darmo</string>
<string name="purchase_cta_lifetime">Kup dożywotni — %s</string>
<string name="purchase_cta_monthly">Zacznij miesięczny — %s</string>
<string name="purchase_free_trial">7 dni za darmo, potem %s / rok</string>
<string name="purchase_most_popular">Najpopularniejszy</string>
<string name="purchase_once">jednorazowo</string>
<string name="purchase_own_forever">Zapłać raz, korzystaj na zawsze</string>
<string name="purchase_per_month">/ miesiąc</string>
<string name="purchase_per_year">/ rok</string>
<string name="purchase_plan_annual">Roczny</string>
<string name="purchase_plan_lifetime">Dożywotni</string>
<string name="purchase_plan_monthly">Miesięczny</string>
<string name="purchase_save_50">Oszczędź 50% względem miesięcznego</string>
<string name="purchase_trial_note">Anuluj w okresie próbnym, a nic nie zapłacisz</string>
<string name="settings_drive_connected_as">Połączono jako %s</string>
<string name="settings_drive_disconnect">Odłącz</string>
```

### `values-pt/strings.xml` (20 keys)

```xml
<string name="help_feedback_feature_subject">Macaco – Sugestão de recurso</string>
<string name="help_feedback_issue_subject">Macaco – Relato de problema</string>
<string name="purchase_best_value">Melhor oferta</string>
<string name="purchase_cancel_anytime">Cancele quando quiser</string>
<string name="purchase_cta_annual">Experimente 7 dias grátis</string>
<string name="purchase_cta_lifetime">Comprar Vitalício — %s</string>
<string name="purchase_cta_monthly">Começar Mensal — %s</string>
<string name="purchase_free_trial">7 dias grátis, depois %s / ano</string>
<string name="purchase_most_popular">Mais popular</string>
<string name="purchase_once">pagamento único</string>
<string name="purchase_own_forever">Pague uma vez, seu para sempre</string>
<string name="purchase_per_month">/ mês</string>
<string name="purchase_per_year">/ ano</string>
<string name="purchase_plan_annual">Anual</string>
<string name="purchase_plan_lifetime">Vitalício</string>
<string name="purchase_plan_monthly">Mensal</string>
<string name="purchase_save_50">Economize 50% em relação ao mensal</string>
<string name="purchase_trial_note">Cancele durante o teste e nada será cobrado</string>
<string name="settings_drive_connected_as">Conectado como %s</string>
<string name="settings_drive_disconnect">Desconectar</string>
```

### `values-zh-rCN/strings.xml` (20 keys)

```xml
<string name="help_feedback_feature_subject">Macaco – 功能建议</string>
<string name="help_feedback_issue_subject">Macaco – 问题反馈</string>
<string name="purchase_best_value">超值之选</string>
<string name="purchase_cancel_anytime">随时取消</string>
<string name="purchase_cta_annual">免费试用7天</string>
<string name="purchase_cta_lifetime">购买终身版 — %s</string>
<string name="purchase_cta_monthly">开通月度版 — %s</string>
<string name="purchase_free_trial">免费7天，之后 %s / 年</string>
<string name="purchase_most_popular">最受欢迎</string>
<string name="purchase_once">一次性付款</string>
<string name="purchase_own_forever">一次付费，永久拥有</string>
<string name="purchase_per_month">/ 月</string>
<string name="purchase_per_year">/ 年</string>
<string name="purchase_plan_annual">年度版</string>
<string name="purchase_plan_lifetime">终身版</string>
<string name="purchase_plan_monthly">月度版</string>
<string name="purchase_save_50">比月付节省50%</string>
<string name="purchase_trial_note">试用期内随时取消，不会产生任何费用</string>
<string name="settings_drive_connected_as">已连接为 %s</string>
<string name="settings_drive_disconnect">断开连接</string>
```

### `values-sv/strings.xml` (47 keys — 20 paywall/common + 27 backlog)

```xml
<string name="help_feedback_feature_subject">Macaco – Funktionsförslag</string>
<string name="help_feedback_issue_subject">Macaco – Felrapport</string>
<string name="purchase_best_value">Bästa erbjudandet</string>
<string name="purchase_cancel_anytime">Avsluta när du vill</string>
<string name="purchase_cta_annual">Prova gratis i 7 dagar</string>
<string name="purchase_cta_lifetime">Köp Livstid — %s</string>
<string name="purchase_cta_monthly">Starta månadsvis — %s</string>
<string name="purchase_free_trial">7 dagar gratis, sedan %s / år</string>
<string name="purchase_most_popular">Mest populär</string>
<string name="purchase_once">engångsbetalning</string>
<string name="purchase_own_forever">Betala en gång, ditt för alltid</string>
<string name="purchase_per_month">/ månad</string>
<string name="purchase_per_year">/ år</string>
<string name="purchase_plan_annual">Årsvis</string>
<string name="purchase_plan_lifetime">Livstid</string>
<string name="purchase_plan_monthly">Månadsvis</string>
<string name="purchase_save_50">Spara 50 % jämfört med månadsvis</string>
<string name="purchase_trial_note">Avsluta under provperioden så debiteras du inget</string>
<string name="settings_drive_connected_as">Ansluten som %s</string>
<string name="settings_drive_disconnect">Koppla från</string>
<string name="drawer_not_signed_in">Inte inloggad</string>
<string name="entry_share_caption_copied">Bildtext kopierad — klistra in den i fotots bildtext</string>
<string name="entry_share_chooser">Dela ditt minne</string>
<string name="entry_share_credit">— delat från Macaco</string>
<string name="map_adventures_title">Äventyr</string>
<string name="map_locations_mapped">%1$d av %2$d platser på kartan</string>
<string name="map_marker_snippet_many">%1$d minnen · tryck för att öppna</string>
<string name="map_marker_snippet_one">1 minne · tryck för att öppna</string>
<string name="map_no_locations_subtitle">Lägg till en plats i dina dagboksinlägg\nså visas de på kartan.</string>
<string name="map_no_locations_title">Inga platser ännu</string>
<string name="new_entry_trip_clear_cd">Rensa resa</string>
<string name="new_entry_trip_label">Resa</string>
<string name="new_entry_trip_placeholder">t.ex. Thailand 2026</string>
<string name="profile_member_since">Medlem sedan %1$s</string>
<string name="purchase_footer_no_fees">Inga dolda avgifter. Avsluta när du vill.</string>
<string name="reminder_action_add">+ Lägg till minne</string>
<string name="reminder_action_snooze">Påminn mig senare</string>
<string name="reminder_channel_desc">Regelbundna påminnelser om att logga ett nytt reseminne</string>
<string name="reminder_channel_name">Resepåminnelser</string>
<string name="reminder_copy_count_body">Du har varit någonstans fantastiskt. Macaco väntar.</string>
<string name="reminder_copy_count_title">%1$d minnen och det fortsätter 📖</string>
<string name="reminder_copy_days_body">Låt inte idag suddas ut. Vad är din historia?</string>
<string name="reminder_copy_days_title">Ditt senaste minne var för %1$d dagar sedan…</string>
<string name="reminder_copy_location_body">Res fritt. Glöm inget. Logga idag.</string>
<string name="reminder_copy_location_title">Tänker du fortfarande på %1$s? 🌏</string>
<string name="reminder_copy_new_body">Vart tog Macaco dig idag?</string>
<string name="reminder_copy_new_title">Res fritt. Glöm inget. 🐒</string>
```

---

## Change 2 — Verify completeness after the edit

Run this check (or equivalent) and confirm every locale reports 0 missing keys:

```bash
python3 - <<'EOF'
import re
def keys(p): return set(re.findall(r'<string name="([^"]+)"', open(p, encoding='utf-8').read()))
base = keys('app/src/main/res/values/strings.xml')
for l in ['de','es','fr','it','ja','nl','pl','pt','sv','zh-rCN']:
    m = base - keys(f'app/src/main/res/values-{l}/strings.xml')
    print(l, len(m), sorted(m)[:5])
EOF
```

Then `./gradlew assembleDebug` to catch any XML/escaping error.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add 18 missing keys (paywall + feedback subjects) | `values-de/strings.xml` |
| 2 | Add 20 missing keys (above + 2 Drive-settings keys) | `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`, `values-zh-rCN` `/strings.xml` |
| 3 | Add 47 missing keys (20 common + 27 Swedish backlog) | `values-sv/strings.xml` |
| 4 | Run completeness check + `assembleDebug` | — |
