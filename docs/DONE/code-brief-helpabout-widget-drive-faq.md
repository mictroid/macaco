# Macaco — Help & About: document the widget (incl. App Lock behavior), Drive-orphan behavior, camera-roll suggestions, email verification & renewal info

Adds four missing FAQ entries to `HelpAboutScreen.kt` and amends one existing answer: the
On This Day home-screen widget (which shipped with no in-app documentation, and whose visibility
is NOT covered by App Lock — a privacy nuance users should hear from us), the fact that deleting
an entry doesn't delete its media from the user's Google Drive folder, the camera-roll
suggested-entries banner (shipped with its own permission prompt but zero FAQ coverage), the
email-verification gate for email/password signups (vc72 — a support-ticket magnet: "I never got
the email"), and a one-sentence renewal date/reminder note appended to the existing billing
answer (vc72's subscription renewal info). Touches `HelpAboutScreen.kt` + `strings.xml` ×11
locales (8 new keys + 1 amended key).

**Background (read first):** the FAQ lives in the private `FAQ_SECTIONS` list at the top of
`HelpAboutScreen.kt` — `FaqSection(titleRes, icon, items)` where items are
`R.string.<q> to R.string.<a>` pairs. The widget and Drive findings come from
`docs/qa-report-2026-07-17.md` (D2, D7); Changes 3–5 come from the 2026-07-17 Help & About
audit against `docs/DONE/` and the vc72 worklog.

## Change 1 — widget FAQ in "Getting started"

**Problem:** the widget is undiscoverable in-app, and it renders an entry's title, location, and
photo on the home screen even while App Lock is armed (`OnThisDayWidgetProvider` runs outside the
Compose gate — by design, but undocumented).

**Fix:** add one Q/A pair directly after the existing On This Day item.

```kotlin
// BEFORE — FAQ_SECTIONS, Getting started section
            R.string.help_faq_on_this_day_q to R.string.help_faq_on_this_day_a,
            // NEW: entry search
            R.string.help_faq_search_q to R.string.help_faq_search_a,
```

```kotlin
// AFTER
            R.string.help_faq_on_this_day_q to R.string.help_faq_on_this_day_a,
            R.string.help_faq_widget_q to R.string.help_faq_widget_a,
            // NEW: entry search
            R.string.help_faq_search_q to R.string.help_faq_search_a,
```

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/HelpAboutScreen.kt`

## Change 2 — Drive-orphans FAQ in "Photos & videos"

**Problem:** deleting an entry (or the whole account) intentionally leaves already-backed-up media
in the user's own Drive "Macaco" folder. Users who check their Drive will wonder why.

**Fix:** add one Q/A pair directly after the Drive-connect item.

```kotlin
// BEFORE — FAQ_SECTIONS, media section
            R.string.help_faq_drive_connect_q to R.string.help_faq_drive_connect_a,
            R.string.help_faq_reorder_photos_q to R.string.help_faq_reorder_photos_a,
```

```kotlin
// AFTER
            R.string.help_faq_drive_connect_q to R.string.help_faq_drive_connect_a,
            R.string.help_faq_delete_drive_q to R.string.help_faq_delete_drive_a,
            R.string.help_faq_reorder_photos_q to R.string.help_faq_reorder_photos_a,
```

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/HelpAboutScreen.kt`

## Change 3 — camera-roll suggestions FAQ in "Getting started"

**Problem:** the camera-roll suggested-entries feature (`docs/DONE/code-brief-camera-roll-suggestions.md`)
shipped with a banner, a photo-permission prompt, and six `photo_suggestion_*` UI strings — but no
FAQ entry. Users will ask what the "New photos from Lisbon…" banner is, how to make it go away,
and why the app wants photo access.

**Fix:** add one Q/A pair at the end of the Getting started section, after the weather item.

```kotlin
// BEFORE — FAQ_SECTIONS, Getting started section (end)
            // NEW: weather stamp
            R.string.help_faq_weather_q to R.string.help_faq_weather_a,
        )
```

```kotlin
// AFTER
            // NEW: weather stamp
            R.string.help_faq_weather_q to R.string.help_faq_weather_a,
            // NEW: camera-roll suggested entries
            R.string.help_faq_suggestions_q to R.string.help_faq_suggestions_a,
        )
```

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/HelpAboutScreen.kt`

## Change 4 — email verification FAQ in "Account"

**Problem:** since vc72, email/password signups must click a Firebase verification link before
the journal opens (`docs/DONE/code-brief-email-verification.md`). The classic support question —
"I signed up but can't get in / never got the email" — has no in-app answer.

**Fix:** add one Q/A pair at the top of the Account section, before the delete-account item
(verification precedes deletion in an account's lifecycle).

```kotlin
// BEFORE — FAQ_SECTIONS, Account section
        listOf(
            R.string.help_faq_delete_account_q to R.string.help_faq_delete_account_a,
        )
```

```kotlin
// AFTER
        listOf(
            // NEW: email verification gate (vc72)
            R.string.help_faq_verify_email_q to R.string.help_faq_verify_email_a,
            R.string.help_faq_delete_account_q to R.string.help_faq_delete_account_a,
        )
```

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/HelpAboutScreen.kt`

## Change 5 — renewal date/reminder sentence appended to the billing answer

**Problem:** vc72 added a renewal date+price line to `SubscriptionInfoScreen` and a ~7-days-before
reminder notification for annual subscribers (`docs/DONE/code-brief-subscription-renewal-info.md`).
Minor, so no new Q/A — but the existing billing answer should mention it so subscribers know where
to look and aren't surprised by the notification.

**Fix:** append one sentence to the **existing** `help_faq_a_billing` value in all 11 catalogues.
No `.kt` change — the key is already wired into the Premium section
(`help_faq_cancel_plan_q to help_faq_a_billing`). Do not replace the existing translated copy;
append the per-locale sentence given in Change 6 to the end of the current value (no leading
space for ja / zh-rCN).

```xml
<!-- BEFORE — values/strings.xml -->
<string name="help_faq_a_billing">Subscriptions and purchases are billed by Google Play. Open Subscription to manage your plan, or go to Play Store → Payments &amp; subscriptions to cancel. For refunds, use Play Store → order history within 48 hours, or contact us.</string>
```

```xml
<!-- AFTER -->
<string name="help_faq_a_billing">Subscriptions and purchases are billed by Google Play. Open Subscription to manage your plan, or go to Play Store → Payments &amp; subscriptions to cancel. For refunds, use Play Store → order history within 48 hours, or contact us. Your renewal date and price are shown on the Subscription screen, and annual subscribers get a reminder about a week before renewal.</string>
```

**Files:** `res/values*/strings.xml` (×11)

## Change 6 — 8 new string keys + 1 amended key ×11 locales

Add to `values/strings.xml` near the other `help_faq_*` keys, and to each of the 10 translated
catalogues (all currently at 0 missing keys — keep it that way). Escape apostrophes as `\'`.
Each locale block below also ends with the **sentence to append** to that locale's existing
`help_faq_a_billing` (Change 5).

**English (`values/strings.xml`):**
```xml
<string name="help_faq_widget_q">Is there a home-screen widget?</string>
<string name="help_faq_widget_a">Yes — long-press your home screen, choose Widgets, and add Macaco\'s \"On This Day\" widget. It shows a memory from this date in a previous year, or your latest entry. Note: the widget stays visible even when App Lock is on, so remove it if you\'d rather keep your journal off the home screen.</string>
<string name="help_faq_delete_drive_q">If I delete an entry, is it removed from Google Drive?</string>
<string name="help_faq_delete_drive_a">Deleting an entry removes it from your journal, but photos and videos already backed up stay in the \"Macaco\" folder of your Google Drive, and photos stay in your device gallery. That folder is yours — you can tidy it up in Drive anytime.</string>
<string name="help_faq_suggestions_q">What is the \"New photos from…\" suggestion banner?</string>
<string name="help_faq_suggestions_a">When Macaco notices recent, geotagged photos in your gallery that aren\'t in any entry yet, it suggests starting one — with the title, place, date, and photos pre-filled. Tap the banner to accept, or dismiss it and it won\'t return for those photos. It needs photo access to work, and nothing leaves your device until you save.</string>
<string name="help_faq_verify_email_q">Why do I need to verify my email?</string>
<string name="help_faq_verify_email_a">If you sign up with email and password, we send a verification link to confirm the address is yours before your journal opens. Check spam if it hasn\'t arrived, or tap Resend on the verification screen. Google Sign-In accounts are already verified.</string>
```
Append to `help_faq_a_billing`:
` Your renewal date and price are shown on the Subscription screen, and annual subscribers get a reminder about a week before renewal.`

**German (`values-de`):**
```xml
<string name="help_faq_widget_q">Gibt es ein Startbildschirm-Widget?</string>
<string name="help_faq_widget_a">Ja — halte deinen Startbildschirm gedrückt, wähle Widgets und füge Macacos \"An diesem Tag\"-Widget hinzu. Es zeigt eine Erinnerung von diesem Datum aus einem früheren Jahr oder deinen neuesten Eintrag. Hinweis: Das Widget bleibt auch bei aktivierter App-Sperre sichtbar — entferne es, wenn dein Tagebuch nicht auf dem Startbildschirm erscheinen soll.</string>
<string name="help_faq_delete_drive_q">Wird ein gelöschter Eintrag auch aus Google Drive entfernt?</string>
<string name="help_faq_delete_drive_a">Beim Löschen verschwindet der Eintrag aus deinem Tagebuch, aber bereits gesicherte Fotos und Videos bleiben im \"Macaco\"-Ordner deines Google Drive, und Fotos bleiben in deiner Galerie. Der Ordner gehört dir — du kannst ihn jederzeit in Drive aufräumen.</string>
<string name="help_faq_suggestions_q">Was ist das Hinweisbanner \"Neue Fotos aus …\"?</string>
<string name="help_faq_suggestions_a">Wenn Macaco in deiner Galerie aktuelle Fotos mit Standort findet, die noch zu keinem Eintrag gehören, schlägt es einen neuen Eintrag vor — mit Titel, Ort, Datum und Fotos bereits ausgefüllt. Tippe auf das Banner, um ihn zu übernehmen, oder verwirf ihn — für diese Fotos erscheint er nicht erneut. Dafür ist Fotozugriff nötig; bis zum Speichern bleibt alles auf deinem Gerät.</string>
<string name="help_faq_verify_email_q">Warum muss ich meine E-Mail bestätigen?</string>
<string name="help_faq_verify_email_a">Bei der Registrierung mit E-Mail und Passwort senden wir dir einen Bestätigungslink, um sicherzugehen, dass die Adresse dir gehört, bevor sich dein Tagebuch öffnet. Prüfe den Spam-Ordner oder tippe auf dem Bestätigungsbildschirm auf „Erneut senden". Google-Konten sind bereits bestätigt.</string>
```
Append to `help_faq_a_billing`:
` Verlängerungsdatum und Preis siehst du im Abo-Bildschirm; bei Jahresabos erinnern wir dich etwa eine Woche vor der Verlängerung.`

**Spanish (`values-es`):**
```xml
<string name="help_faq_widget_q">¿Hay un widget para la pantalla de inicio?</string>
<string name="help_faq_widget_a">Sí: mantén pulsada la pantalla de inicio, elige Widgets y añade el widget \"Tal día como hoy\" de Macaco. Muestra un recuerdo de esta fecha de un año anterior o tu entrada más reciente. Nota: el widget sigue visible aunque el bloqueo de la app esté activado; quítalo si prefieres mantener tu diario fuera de la pantalla de inicio.</string>
<string name="help_faq_delete_drive_q">Si elimino una entrada, ¿se elimina de Google Drive?</string>
<string name="help_faq_delete_drive_a">Al eliminar una entrada desaparece de tu diario, pero las fotos y los vídeos ya respaldados permanecen en la carpeta \"Macaco\" de tu Google Drive, y las fotos permanecen en la galería del dispositivo. Esa carpeta es tuya: puedes ordenarla en Drive cuando quieras.</string>
<string name="help_faq_suggestions_q">¿Qué es el aviso \"Nuevas fotos de…\"?</string>
<string name="help_faq_suggestions_a">Cuando Macaco detecta en tu galería fotos recientes con ubicación que aún no están en ninguna entrada, te sugiere crear una — con el título, el lugar, la fecha y las fotos ya rellenados. Toca el aviso para aceptarlo o descártalo y no volverá a aparecer para esas fotos. Necesita acceso a tus fotos, y nada sale de tu dispositivo hasta que guardes.</string>
<string name="help_faq_verify_email_q">¿Por qué tengo que verificar mi correo?</string>
<string name="help_faq_verify_email_a">Si te registras con correo y contraseña, te enviamos un enlace de verificación para confirmar que la dirección es tuya antes de abrir tu diario. Revisa el spam si no llega, o toca Reenviar en la pantalla de verificación. Las cuentas de Google ya están verificadas.</string>
```
Append to `help_faq_a_billing`:
` La fecha y el precio de renovación aparecen en la pantalla de Suscripción, y los suscriptores anuales reciben un recordatorio una semana antes.`

**French (`values-fr`):**
```xml
<string name="help_faq_widget_q">Existe-t-il un widget d\'écran d\'accueil ?</string>
<string name="help_faq_widget_a">Oui : appuyez longuement sur l\'écran d\'accueil, choisissez Widgets et ajoutez le widget \"Ce jour-là\" de Macaco. Il affiche un souvenir de cette date d\'une année précédente, ou votre dernière entrée. Remarque : le widget reste visible même lorsque le verrouillage de l\'app est activé — retirez-le si vous préférez garder votre journal hors de l\'écran d\'accueil.</string>
<string name="help_faq_delete_drive_q">Si je supprime une entrée, est-elle retirée de Google Drive ?</string>
<string name="help_faq_delete_drive_a">La suppression retire l\'entrée de votre journal, mais les photos et vidéos déjà sauvegardées restent dans le dossier \"Macaco\" de votre Google Drive, et les photos restent dans la galerie de l\'appareil. Ce dossier vous appartient : vous pouvez le ranger dans Drive à tout moment.</string>
<string name="help_faq_suggestions_q">Qu\'est-ce que la bannière \"Nouvelles photos de…\" ?</string>
<string name="help_faq_suggestions_a">Quand Macaco repère dans votre galerie des photos récentes géolocalisées qui ne figurent dans aucune entrée, il vous propose d\'en créer une — titre, lieu, date et photos déjà préremplis. Touchez la bannière pour accepter, ou ignorez-la et elle ne reviendra pas pour ces photos. L\'accès aux photos est requis, et tout reste sur votre appareil jusqu\'à l\'enregistrement.</string>
<string name="help_faq_verify_email_q">Pourquoi dois-je vérifier mon e-mail ?</string>
<string name="help_faq_verify_email_a">Si vous vous inscrivez avec e-mail et mot de passe, nous envoyons un lien de vérification pour confirmer que l\'adresse est bien la vôtre avant d\'ouvrir votre journal. Vérifiez les spams s\'il n\'arrive pas, ou touchez Renvoyer sur l\'écran de vérification. Les comptes Google sont déjà vérifiés.</string>
```
Append to `help_faq_a_billing`:
` La date et le prix de renouvellement figurent sur l\'écran Abonnement, et les abonnés annuels reçoivent un rappel environ une semaine avant.`

**Italian (`values-it`):**
```xml
<string name="help_faq_widget_q">Esiste un widget per la schermata Home?</string>
<string name="help_faq_widget_a">Sì: tieni premuta la schermata Home, scegli Widget e aggiungi il widget \"Accadde oggi\" di Macaco. Mostra un ricordo di questa data di un anno precedente, o la tua voce più recente. Nota: il widget resta visibile anche con il blocco app attivo — rimuovilo se preferisci tenere il diario fuori dalla schermata Home.</string>
<string name="help_faq_delete_drive_q">Se elimino una voce, viene rimossa da Google Drive?</string>
<string name="help_faq_delete_drive_a">Eliminando una voce questa scompare dal diario, ma le foto e i video già salvati restano nella cartella \"Macaco\" del tuo Google Drive, e le foto restano nella galleria del dispositivo. Quella cartella è tua: puoi riordinarla in Drive quando vuoi.</string>
<string name="help_faq_suggestions_q">Cos\'è il banner \"Nuove foto da…\"?</string>
<string name="help_faq_suggestions_a">Quando Macaco trova nella galleria foto recenti geolocalizzate non ancora in nessuna voce, propone di crearne una — con titolo, luogo, data e foto già compilati. Tocca il banner per accettare, oppure ignoralo e non riapparirà per quelle foto. Serve l\'accesso alle foto e tutto resta sul dispositivo finché non salvi.</string>
<string name="help_faq_verify_email_q">Perché devo verificare la mia email?</string>
<string name="help_faq_verify_email_a">Se ti registri con email e password, ti inviamo un link di verifica per confermare che l\'indirizzo è tuo prima di aprire il diario. Controlla lo spam se non arriva, o tocca Reinvia nella schermata di verifica. Gli account Google sono già verificati.</string>
```
Append to `help_faq_a_billing`:
` Data e prezzo di rinnovo sono nella schermata Abbonamento; chi ha l\'abbonamento annuale riceve un promemoria circa una settimana prima.`

**Japanese (`values-ja`):**
```xml
<string name="help_faq_widget_q">ホーム画面ウィジェットはありますか？</string>
<string name="help_faq_widget_a">あります。ホーム画面を長押しして「ウィジェット」を選び、Macacoの「あの日の思い出」ウィジェットを追加してください。過去の同じ日付の思い出、または最新の記録を表示します。注意：アプリロックが有効でもウィジェットは表示されたままです。日記をホーム画面に出したくない場合はウィジェットを外してください。</string>
<string name="help_faq_delete_drive_q">記録を削除するとGoogle Driveからも削除されますか？</string>
<string name="help_faq_delete_drive_a">削除すると日記からは消えますが、バックアップ済みの写真や動画はGoogle Driveの「Macaco」フォルダに、写真は端末のギャラリーに残ります。フォルダはあなたのものなので、いつでもDriveで整理できます。</string>
<string name="help_faq_suggestions_q">「◯◯の新しい写真」というバナーは何ですか？</string>
<string name="help_faq_suggestions_a">ギャラリーに、まだどの記録にも入っていない位置情報付きの最近の写真があると、Macacoが記録の作成を提案します。タイトル・場所・日付・写真はあらかじめ入力済みです。バナーをタップして作成するか、閉じればその写真については再表示されません。写真へのアクセス許可が必要で、保存するまで内容は端末内にとどまります。</string>
<string name="help_faq_verify_email_q">なぜメールアドレスの確認が必要ですか？</string>
<string name="help_faq_verify_email_a">メールとパスワードで登録した場合、アドレスがご本人のものか確認するため、確認リンクをお送りします。リンクを開くまで日記は開けません。届かない場合は迷惑メールを確認するか、確認画面で「再送信」をタップしてください。Googleでログインした場合は確認済みです。</string>
```
Append to `help_faq_a_billing` (no leading space):
`更新日と料金はサブスクリプション画面に表示され、年間プランの方には更新の約1週間前に通知が届きます。`

**Dutch (`values-nl`):**
```xml
<string name="help_faq_widget_q">Is er een widget voor het startscherm?</string>
<string name="help_faq_widget_a">Ja — houd je startscherm ingedrukt, kies Widgets en voeg Macaco\'s \"Op deze dag\"-widget toe. Die toont een herinnering van deze datum uit een eerder jaar, of je nieuwste notitie. Let op: de widget blijft zichtbaar ook als app-vergrendeling aanstaat — verwijder hem als je je dagboek liever niet op het startscherm hebt.</string>
<string name="help_faq_delete_drive_q">Als ik een notitie verwijder, verdwijnt die dan uit Google Drive?</string>
<string name="help_faq_delete_drive_a">Bij verwijderen verdwijnt de notitie uit je dagboek, maar al geback-upte foto\'s en video\'s blijven in de map \"Macaco\" van je Google Drive staan, en foto\'s blijven in je galerij. Die map is van jou — je kunt hem in Drive altijd opruimen.</string>
<string name="help_faq_suggestions_q">Wat is de banner \"Nieuwe foto\'s uit…\"?</string>
<string name="help_faq_suggestions_a">Als Macaco in je galerij recente foto\'s met locatie vindt die nog in geen enkele notitie staan, stelt het voor er een te maken — met titel, plaats, datum en foto\'s al ingevuld. Tik op de banner om te accepteren, of wijs hem af en hij komt voor die foto\'s niet terug. Hiervoor is fototoegang nodig; tot je opslaat blijft alles op je toestel.</string>
<string name="help_faq_verify_email_q">Waarom moet ik mijn e-mailadres verifiëren?</string>
<string name="help_faq_verify_email_a">Als je je registreert met e-mail en wachtwoord sturen we een verificatielink om te bevestigen dat het adres van jou is voordat je dagboek opent. Controleer je spam als hij niet aankomt, of tik op Opnieuw versturen op het verificatiescherm. Google-accounts zijn al geverifieerd.</string>
```
Append to `help_faq_a_billing`:
` De verlengingsdatum en prijs staan op het Abonnement-scherm, en jaarabonnees krijgen ongeveer een week vooraf een herinnering.`

**Polish (`values-pl`):**
```xml
<string name="help_faq_widget_q">Czy jest widżet na ekran główny?</string>
<string name="help_faq_widget_a">Tak — przytrzymaj ekran główny, wybierz Widżety i dodaj widżet Macaco \"Tego dnia\". Pokazuje wspomnienie z tej daty z poprzedniego roku lub najnowszy wpis. Uwaga: widżet pozostaje widoczny nawet przy włączonej blokadzie aplikacji — usuń go, jeśli wolisz nie pokazywać dziennika na ekranie głównym.</string>
<string name="help_faq_delete_drive_q">Czy usunięcie wpisu usuwa go też z Google Drive?</string>
<string name="help_faq_delete_drive_a">Usunięcie wpisu usuwa go z dziennika, ale zapisane wcześniej zdjęcia i filmy pozostają w folderze \"Macaco\" na Twoim Google Drive, a zdjęcia pozostają w galerii urządzenia. Ten folder należy do Ciebie — możesz go uporządkować w Drive w każdej chwili.</string>
<string name="help_faq_suggestions_q">Czym jest baner \"Nowe zdjęcia z…\"?</string>
<string name="help_faq_suggestions_a">Gdy Macaco znajdzie w galerii niedawne zdjęcia z lokalizacją, których nie ma jeszcze w żadnym wpisie, zaproponuje utworzenie wpisu — z gotowym tytułem, miejscem, datą i zdjęciami. Stuknij baner, aby zaakceptować, albo go odrzuć — dla tych zdjęć już nie wróci. Wymaga dostępu do zdjęć; do momentu zapisania wszystko zostaje na urządzeniu.</string>
<string name="help_faq_verify_email_q">Dlaczego muszę zweryfikować adres e-mail?</string>
<string name="help_faq_verify_email_a">Przy rejestracji e-mailem i hasłem wysyłamy link weryfikacyjny, aby potwierdzić, że adres należy do Ciebie, zanim otworzy się dziennik. Sprawdź spam, jeśli nie dotarł, albo stuknij Wyślij ponownie na ekranie weryfikacji. Konta Google są już zweryfikowane.</string>
```
Append to `help_faq_a_billing`:
` Data i cena odnowienia są widoczne na ekranie Subskrypcja, a subskrybenci roczni dostają przypomnienie około tydzień wcześniej.`

**Portuguese (`values-pt`):**
```xml
<string name="help_faq_widget_q">Existe um widget para o ecrã inicial?</string>
<string name="help_faq_widget_a">Sim — mantém premido o ecrã inicial, escolhe Widgets e adiciona o widget \"Neste dia\" do Macaco. Mostra uma memória desta data de um ano anterior, ou a tua entrada mais recente. Nota: o widget continua visível mesmo com o bloqueio da app ativo — remove-o se preferires manter o diário fora do ecrã inicial.</string>
<string name="help_faq_delete_drive_q">Se eliminar uma entrada, ela é removida do Google Drive?</string>
<string name="help_faq_delete_drive_a">Ao eliminar, a entrada desaparece do diário, mas as fotos e vídeos já guardados permanecem na pasta \"Macaco\" do teu Google Drive, e as fotos permanecem na galeria do dispositivo. Essa pasta é tua — podes organizá-la no Drive quando quiseres.</string>
<string name="help_faq_suggestions_q">O que é o aviso \"Novas fotos de…\"?</string>
<string name="help_faq_suggestions_a">Quando o Macaco encontra na galeria fotos recentes com localização que ainda não estão em nenhuma entrada, sugere criar uma — com título, local, data e fotos já preenchidos. Toca no aviso para aceitar, ou dispensa-o e não volta a aparecer para essas fotos. Precisa de acesso às fotos, e tudo fica no teu dispositivo até guardares.</string>
<string name="help_faq_verify_email_q">Porque tenho de verificar o meu email?</string>
<string name="help_faq_verify_email_a">Se te registares com email e palavra-passe, enviamos um link de verificação para confirmar que o endereço é teu antes de abrir o diário. Vê o spam se não chegar, ou toca em Reenviar no ecrã de verificação. As contas Google já estão verificadas.</string>
```
Append to `help_faq_a_billing`:
` A data e o preço de renovação aparecem no ecrã Subscrição, e os subscritores anuais recebem um lembrete cerca de uma semana antes.`

**Swedish (`values-sv`):**
```xml
<string name="help_faq_widget_q">Finns det en widget för hemskärmen?</string>
<string name="help_faq_widget_a">Ja — håll in hemskärmen, välj Widgetar och lägg till Macacos \"Denna dag\"-widget. Den visar ett minne från detta datum ett tidigare år, eller din senaste anteckning. Obs: widgeten syns även när applåset är på — ta bort den om du hellre håller dagboken borta från hemskärmen.</string>
<string name="help_faq_delete_drive_q">Om jag raderar en anteckning, försvinner den från Google Drive?</string>
<string name="help_faq_delete_drive_a">När du raderar försvinner anteckningen ur dagboken, men redan säkerhetskopierade foton och videor ligger kvar i mappen \"Macaco\" på din Google Drive, och foton ligger kvar i galleriet. Mappen är din — du kan städa den i Drive när du vill.</string>
<string name="help_faq_suggestions_q">Vad är bannern \"Nya foton från…\"?</string>
<string name="help_faq_suggestions_a">När Macaco hittar nya platsmärkta foton i galleriet som inte finns i någon anteckning föreslår appen att du skapar en — med titel, plats, datum och foton redan ifyllda. Tryck på bannern för att acceptera, eller avvisa den så visas den inte igen för de fotona. Kräver fotoåtkomst; allt stannar på din enhet tills du sparar.</string>
<string name="help_faq_verify_email_q">Varför måste jag verifiera min e-post?</string>
<string name="help_faq_verify_email_a">Om du registrerar dig med e-post och lösenord skickar vi en verifieringslänk för att bekräfta att adressen är din innan dagboken öppnas. Kolla skräpposten om den inte kommer, eller tryck på Skicka igen på verifieringsskärmen. Google-konton är redan verifierade.</string>
```
Append to `help_faq_a_billing`:
` Förnyelsedatum och pris visas på Prenumerationsskärmen, och årsprenumeranter får en påminnelse ungefär en vecka innan.`

**Simplified Chinese (`values-zh-rCN`):**
```xml
<string name="help_faq_widget_q">有主屏幕小组件吗？</string>
<string name="help_faq_widget_a">有 — 长按主屏幕，选择"小组件"，添加 Macaco 的"那年今日"小组件。它会显示往年同一天的回忆，或你最新的日记。注意：即使开启了应用锁，小组件仍然可见；如果不想在主屏幕上展示日记，请移除小组件。</string>
<string name="help_faq_delete_drive_q">删除日记后，Google Drive 里的内容也会被删除吗？</string>
<string name="help_faq_delete_drive_a">删除日记只会将其从日记本中移除，已备份的照片和视频仍保留在你 Google Drive 的"Macaco"文件夹中，照片也仍在设备相册里。那个文件夹属于你，可以随时在 Drive 中整理。</string>
<string name="help_faq_suggestions_q">"来自某地的新照片"横幅是什么？</string>
<string name="help_faq_suggestions_a">当 Macaco 在相册中发现尚未加入任何日记、带位置信息的近期照片时，会建议你创建一篇日记——标题、地点、日期和照片都已自动填好。点按横幅即可创建，关闭后这批照片不会再次提示。此功能需要照片访问权限，保存之前所有内容都只保留在你的设备上。</string>
<string name="help_faq_verify_email_q">为什么需要验证邮箱？</string>
<string name="help_faq_verify_email_a">使用邮箱和密码注册时，我们会发送验证链接，确认邮箱属于你之后才能进入日记。如果没收到，请检查垃圾邮件，或在验证页面点按"重新发送"。使用 Google 登录的账号无需验证。</string>
```
Append to `help_faq_a_billing` (no leading space):
`续订日期和价格显示在订阅页面，年度订阅用户会在续订前约一周收到提醒。`

## Verification

`assembleDebug` (an unescaped apostrophe in fr/it/nl will break the build — that bit us in vc72's
`values-fr`). On-device: Help & About → Getting started shows the widget Q/A after On This Day
and the suggestions Q/A as the last item; Photos & videos shows the Drive Q/A after the connect
item; Account shows the verify-email Q/A before Delete account; Premium's "Change or cancel plan"
answer ends with the renewal sentence. Locale spot-check: switch in-app language to German and
French. Confirm the locale-gap script still reports 0 missing keys per locale, and that
`help_faq_a_billing` in every locale still contains its original copy with the sentence appended
(not replaced).

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Widget Q/A pair in Getting started | `ui/screens/HelpAboutScreen.kt` |
| 2 | Drive-orphans Q/A pair in Photos & videos | `ui/screens/HelpAboutScreen.kt` |
| 3 | Camera-roll suggestions Q/A pair in Getting started | `ui/screens/HelpAboutScreen.kt` |
| 4 | Email-verification Q/A pair in Account | `ui/screens/HelpAboutScreen.kt` |
| 5 | Renewal sentence appended to `help_faq_a_billing` | `res/values*/strings.xml` (×11) |
| 6 | 8 new `help_faq_*` keys + 1 amended key ×11 locales | `res/values*/strings.xml` |
