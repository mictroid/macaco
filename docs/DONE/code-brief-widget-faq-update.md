# Macaco — Help & About: Update Widget FAQ for 3 New Widgets (11 locales)

Covers `app/src/main/res/values*/strings.xml` (base + all 11 locales). Follow-on to
`code-brief-additional-widgets.md`: once Quick Add, Travel Stats, and Adventures Map Mini ship,
`help_faq_widget_a` will only describe "On This Day," and its App Lock caveat won't cover the two
new widgets that also surface personal data on the home screen. This brief updates the FAQ answer
only — no code changes, no legal-doc changes (Privacy Policy / Terms of Service don't need
updating: all three widgets read data already disclosed there, via the same Firestore path the
rest of the app already uses, with no new third party or permission involved).

**Sequencing:** land after `code-brief-additional-widgets.md` ships (or in the same PR) — this
brief describes widgets that don't exist yet otherwise.

---

## `help_faq_widget_a` — mention all 4 widgets, extend the App Lock note

**Problem:** Base `strings.xml` line 91:

```xml
<!-- BEFORE -->
<string name="help_faq_widget_a">Yes — long-press your home screen, choose Widgets, and add Macaco\'s \"On This Day\" widget. It shows a memory from this date in a previous year, or your latest entry. Note: the widget stays visible even when App Lock is on, so remove it if you\'d rather keep your journal off the home screen.</string>
```

Only mentions one widget, and the App Lock caveat won't apply to Quick Add (no journal data
shown) but does need to extend to Travel Stats and Adventures Map Mini (both show personal
counts on the home screen, same as On This Day).

**Fix:**

```xml
<!-- AFTER -->
<string name="help_faq_widget_a">Yes — long-press your home screen, choose Widgets, and add one of Macaco\'s widgets: \"On This Day\" (a memory from this date in a previous year, or your latest entry), \"Quick Add\" (a shortcut straight to a new entry), \"Travel Stats\" (your entry and place counts), or \"Adventures\" (how many places you\'ve mapped). Note: On This Day, Travel Stats, and Adventures stay visible even when App Lock is on, so remove them if you\'d rather keep your journal off the home screen — Quick Add shows no journal data, so it isn\'t affected.</string>
```

**File:** `app/src/main/res/values/strings.xml`

---

## Propagate to all 11 locales

Same key, same fix, in each locale file (line numbers per the current file):

### `values-fr/strings.xml` (line 408)

```xml
<string name="help_faq_widget_a">Oui : appuyez longuement sur l\'écran d\'accueil, choisissez Widgets et ajoutez l\'un des widgets de Macaco : « Ce jour-là » (un souvenir de cette date d\'une année précédente, ou votre dernière entrée), « Ajout rapide » (un raccourci vers une nouvelle entrée), « Statistiques de voyage » (le nombre d\'entrées et de lieux) ou « Aventures » (le nombre de lieux cartographiés). Remarque : Ce jour-là, Statistiques de voyage et Aventures restent visibles même lorsque le verrouillage de l\'app est activé — retirez-les si vous préférez garder votre journal hors de l\'écran d\'accueil. Ajout rapide n\'affiche aucune donnée du journal, il n\'est donc pas concerné.</string>
```

### `values-es/strings.xml` (line 408)

```xml
<string name="help_faq_widget_a">Sí: mantén pulsada la pantalla de inicio, elige Widgets y añade uno de los widgets de Macaco: «Tal día como hoy» (un recuerdo de esta fecha de un año anterior, o tu entrada más reciente), «Añadir rápido» (un acceso directo a una nueva entrada), «Estadísticas de viaje» (tu número de entradas y lugares) o «Aventuras» (cuántos lugares has mapeado). Nota: Tal día como hoy, Estadísticas de viaje y Aventuras siguen visibles aunque el bloqueo de la app esté activado; quítalos si prefieres mantener tu diario fuera de la pantalla de inicio. Añadir rápido no muestra ningún dato del diario, así que no se ve afectado.</string>
```

### `values-pl/strings.xml` (line 416)

```xml
<string name="help_faq_widget_a">Tak — przytrzymaj ekran główny, wybierz Widżety i dodaj jeden z widżetów Macaco: „Tego dnia” (wspomnienie z tej daty z poprzedniego roku lub najnowszy wpis), „Szybki wpis” (skrót do nowego wpisu), „Statystyki podróży” (liczba wpisów i miejsc) lub „Przygody” (liczba zmapowanych miejsc). Uwaga: „Tego dnia”, „Statystyki podróży” i „Przygody” pozostają widoczne nawet przy włączonej blokadzie aplikacji — usuń je, jeśli wolisz nie pokazywać dziennika na ekranie głównym. „Szybki wpis” nie pokazuje żadnych danych z dziennika, więc nie jest tym objęty.</string>
```

### `values-sv/strings.xml` (line 381)

```xml
<string name="help_faq_widget_a">Ja — håll in hemskärmen, välj Widgetar och lägg till en av Macacos widgetar: \"Denna dag\" (ett minne från detta datum ett tidigare år, eller din senaste anteckning), \"Snabbtillägg\" (en genväg direkt till en ny anteckning), \"Resestatistik\" (antal anteckningar och platser) eller \"Äventyr\" (hur många platser du kartlagt). Obs: Denna dag, Resestatistik och Äventyr syns även när applåset är på — ta bort dem om du hellre håller dagboken borta från hemskärmen. Snabbtillägg visar ingen dagboksdata, så det påverkas inte.</string>
```

### `values-ja/strings.xml` (line 404)

```xml
<string name="help_faq_widget_a">あります。ホーム画面を長押しして「ウィジェット」を選び、Macacoのウィジェットを追加してください：「あの日の思い出」（過去の同じ日付の思い出、または最新の記録）、「クイック追加」（新しい記録への近道）、「旅の統計」（記録数と訪れた場所の数）、「冒険」（マッピングした場所の数）。注意：「あの日の思い出」「旅の統計」「冒険」はアプリロックが有効でも表示されたままです。日記をホーム画面に出したくない場合はウィジェットを外してください。「クイック追加」は日記データを表示しないため影響を受けません。</string>
```

### `values-nl/strings.xml` (line 408)

```xml
<string name="help_faq_widget_a">Ja — houd je startscherm ingedrukt, kies Widgets en voeg een van Macaco\'s widgets toe: \"Op deze dag\" (een herinnering van deze datum uit een eerder jaar, of je nieuwste notitie), \"Snel toevoegen\" (een snelkoppeling naar een nieuwe notitie), \"Reisstatistieken\" (je aantal notities en plaatsen) of \"Avonturen\" (hoeveel plaatsen je in kaart hebt gebracht). Let op: Op deze dag, Reisstatistieken en Avonturen blijven zichtbaar ook als app-vergrendeling aanstaat — verwijder ze als je je dagboek liever niet op het startscherm hebt. Snel toevoegen toont geen dagboekgegevens, dus dat wordt niet beïnvloed.</string>
```

### `values-pt/strings.xml` (line 408)

```xml
<string name="help_faq_widget_a">Sim — mantém premido o ecrã inicial, escolhe Widgets e adiciona um dos widgets do Macaco: \"Neste dia\" (uma memória desta data de um ano anterior, ou a tua entrada mais recente), \"Adicionar rápido\" (um atalho direto para uma nova entrada), \"Estatísticas de viagem\" (o teu número de entradas e locais) ou \"Aventuras\" (quantos locais já mapeaste). Nota: Neste dia, Estatísticas de viagem e Aventuras continuam visíveis mesmo com o bloqueio da app ativo — remove-os se preferires manter o diário fora do ecrã inicial. Adicionar rápido não mostra nenhum dado do diário, por isso não é afetado.</string>
```

### `values-it/strings.xml` (line 408)

```xml
<string name="help_faq_widget_a">Sì: tieni premuta la schermata Home, scegli Widget e aggiungi uno dei widget di Macaco: \"Accadde oggi\" (un ricordo di questa data di un anno precedente, o la tua voce più recente), \"Aggiunta rapida\" (una scorciatoia per una nuova voce), \"Statistiche di viaggio\" (il numero di voci e luoghi) o \"Avventure\" (quanti luoghi hai mappato). Nota: Accadde oggi, Statistiche di viaggio e Avventure restano visibili anche con il blocco app attivo — rimuovili se preferisci tenere il diario fuori dalla schermata Home. Aggiunta rapida non mostra alcun dato del diario, quindi non ne è interessato.</string>
```

### `values-zh-rCN/strings.xml` (line 404)

```xml
<string name="help_faq_widget_a">有 — 长按主屏幕，选择"小组件"，添加 Macaco 的一个小组件："那年今日"（往年同一天的回忆，或你最新的日记）、"快速添加"（直达新建日记的快捷方式）、"旅行统计"（你的日记和地点数量）或"冒险地图"（你已标记的地点数量）。注意："那年今日""旅行统计"和"冒险地图"在开启应用锁后仍然可见；如果不想在主屏幕上展示日记，请移除它们。"快速添加"不显示任何日记数据，因此不受影响。</string>
```

### `values-de/strings.xml` (line 419)

```xml
<string name="help_faq_widget_a">Ja — halte deinen Startbildschirm gedrückt, wähle Widgets und füge eines von Macacos Widgets hinzu: „An diesem Tag\" (eine Erinnerung von diesem Datum aus einem früheren Jahr oder dein neuester Eintrag), „Schnell hinzufügen\" (eine Abkürzung direkt zu einem neuen Eintrag), „Reisestatistik\" (deine Anzahl an Einträgen und Orten) oder „Abenteuer\" (wie viele Orte du kartiert hast). Hinweis: An diesem Tag, Reisestatistik und Abenteuer bleiben auch bei aktivierter App-Sperre sichtbar — entferne sie, wenn dein Tagebuch nicht auf dem Startbildschirm erscheinen soll. Schnell hinzufügen zeigt keine Tagebuchdaten an, ist also nicht betroffen.</string>
```

---

## Scope note: legal docs untouched

Not touching `privacy-policy.html` or `terms-of-service.html`. Reasoning: all three new widgets
read only data already itemized in the Privacy Policy's "Information we collect" table (journal
entries: text, photos, locations, mood, tags — same Firestore documents, same per-user path).
None of them add a new third-party processor, a new permission, or a new data category — the
Adventures Map Mini was deliberately built as a decorative, non-geocoded card specifically to
avoid triggering a "real location data sent to a mapping service" disclosure. If the "real pins"
v2 mentioned in `code-brief-additional-widgets.md` §3 is ever built (Static Maps API and/or
per-widget-update `Geocoder` calls), revisit the Privacy Policy's third-party services table
(§4) at that point.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Update `help_faq_widget_a` to describe all 4 widgets + extend App Lock caveat (EN) | `values/strings.xml` |
| 2 | Same, translated | `values-fr/strings.xml` |
| 3 | Same, translated | `values-es/strings.xml` |
| 4 | Same, translated | `values-pl/strings.xml` |
| 5 | Same, translated | `values-sv/strings.xml` |
| 6 | Same, translated | `values-ja/strings.xml` |
| 7 | Same, translated | `values-nl/strings.xml` |
| 8 | Same, translated | `values-pt/strings.xml` |
| 9 | Same, translated | `values-it/strings.xml` |
| 10 | Same, translated | `values-zh-rCN/strings.xml` |
| 11 | Same, translated | `values-de/strings.xml` |
