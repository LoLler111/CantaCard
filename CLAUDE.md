# CantaCard

App Android ultra leggera per le tessere fedeltà: mostra a schermo il codice (barcode/QR) da far
leggere in cassa. Alternativa minimale a Stocard (diventata pesante dopo l'acquisizione da parte di
Klarna e che spesso chiede aggiornamenti proprio alla cassa).

## Principi (non negoziabili)

- **Offline-first**: mostrare una tessera non deve MAI richiedere rete, login o aggiornamenti.
- **Avvio istantaneo, APK minimo**: R8/minify + shrinkResources attivi, dipendenze ridotte al minimo.
  Ogni nuova dipendenza va giustificata col suo peso sull'APK; si preferiscono le API del framework.
- **Nessun backend proprio**, nessun account, nessuna analytics, nessuna pubblicità.
- **Nessun logo di marchi incluso nell'APK** (le icone dei negozi si scaricano a runtime, su scelta
  dell'utente, e restano solo in locale).

## Stato

- Fase 1 (scheletro + modello dati) completata: APK release 1,13 MB.
- Fase 2 (visualizzazione) completata: `ShowCardActivity` con codice ZXing a scala intera e quiet
  zone, luminosità massima, schermo acceso, preferita ed eliminazione. APK release 1,25 MB.
- Fase 3 (aggiunta/scansione + modifica) completata: `EditCardActivity` (aggiungi e modifica),
  `scan.ScanActivity` (CameraX + ZXing, torcia, permesso chiesto solo lì), lettura da foto via
  Photo Picker. APK release 2,0 MB (CameraX è la parte più pesante del dex).
- Fase 4 (catalogo) completata: `catalog/stores.json` (53 catene; ampliato a 81 con catene regionali, versione 3; a 95 nella versione 4), `StorePickerActivity` con ricerca
  e "Altro negozio", negozio nel modulo (cambiabile), aggiornamento da GitHub. APK release 2,06 MB.
- Fase 5 (immagini) completata: icona dal sito scaricata da sola alla scelta del negozio (+ "Aggiorna
  icona"), foto della tessera (`scan.PhotoActivity`) e galleria con ritaglio (`ui.edit.CropDialog`),
  immagine condivisa da altre app (`ShareImageActivity`, legge anche il codice), scelta del colore.
  APK release 2,16 MB (+100 KB: soprattutto ImageCapture di CameraX).
- Fase 6 (backup) completata: `data.Backup` + `data.BackupFormat` (zip via SAF con `cantacard.json` + `images/<id>.webp`), menu ⋮ della lista ("Esporta backup", "Ripristina backup") e "Hai un backup? Ripristinalo" nella lista vuota; al ripristino `RestoreDialog` chiede "Aggiungi alle attuali" o "Sostituisci tutte". APK release 2,18 MB.
- Fase 6b (sincronizzazione) completata: `data.Sync` (file scelto con il SAF, permesso persistente, all'apertura e 2 s dopo ogni modifica, file riscritto solo se cambia), `data.SyncMerge` (regole, testate), cancellate in `cards.json` (`"deleted"`, tenute un anno), zip in comune con il backup (`data.BackupZip`), `ui.list.SyncDialog`. APK release 2,20 MB.
- Fase 7 (accesso rapido) completata: scorciatoie dinamiche (`shortcuts.CardShortcuts`), "Aggiungi alla schermata Home" nella schermata della tessera, widget "Tessere preferite" e "Tessera" (`widget.Widgets`, `widget.CardWidgetConfigActivity`), tessere disegnate come bitmap (`image.CardBitmaps`), aggiornati da `QuickAccess` solo quando cambia qualcosa di visibile. APK release 2,21 MB. Tutte le fasi previste sono fatte.
- Aggiornamento dell'app (13/11/2026): `update.AppUpdater` controlla GitHub Releases a ogni avvio e, se c'è una versione più nuova, mostra in cima alla lista la riga "Nuova versione disponibile" (`ui.list.UpdateRow`, sempre visibile, non chiudibile); toccandola si apre nel browser il link diretto dell'APK. La v0.1.2 lo installava dall'app con il `PackageInstaller`, ma Play Protect la segnalava come dannosa: dalla 0.1.3 l'app non installa più niente. Provato sull'emulatore: riga e apertura di Chrome sul link giusto (il download in Chrome no, era fermo alla prima configurazione). APK release 2,21 MB.
- Versione 0.2.0 (16/11/2026, richiesta dell'utente): nuova grafica del modulo di aggiunta/modifica
  (intestazione verde con l'anteprima della tessera a cavallo del bordo, sezioni in riquadri
  arrotondati `ui.edit.FormSection`, campi pieni `FormTextField`, formati come pillole, "Salva" grande
  in basso); foto del retro, scadenza (calendario Material 3), etichette, archivio e ordine
  personalizzato (vedi "Modello dati"); avviso "Scade tra N giorni" sulle tessere entro 30 giorni;
  scorrimento tra le tessere in `ShowCardActivity` (`HorizontalPager`, stesso ordine della lista,
  fissato all'apertura); codice a barre ruotato di 90° ("Ruota il codice"); menu ⋮ nella tessera
  (Aggiungi a Home, Archivia, Elimina); riquadro "Tessere" nelle impostazioni rapide
  (`CardTileService`, apre la prima tessera, con il telefono bloccato chiede lo sblocco);
  importazione da Catima (`data.CatimaCsv` + `data.CatimaImport`, zip o CSV, riusa il ripristino in
  modalità "Aggiungi"); "Segnala un problema" (issue GitHub precompilata con versione, Android e
  modello, inviata solo dall'utente); traduzione inglese (`values-en`, l'italiano resta la lingua
  predefinita); test di compatibilità dei dati (`DataCompatibilityTest`, file in
  `src/test/resources/compat/`); `ETag` nel controllo aggiornamenti (`update.UpdateCache`).
  APK release 2,37 MB (+156 KB). **Stocard**: importazione non fatta, serve un file esportato vero
  per conoscerne il formato.
- Sottotitolo della lista (19/11/2026, richiesta dell'utente): al posto di "sempre offline", con la
  sincronizzazione attiva "N tessere · salvate oggi/ieri alle HH:MM" (o "il 17 nov alle …"; ultima
  sincronizzazione riuscita), altrimenti il link "Fai il backup delle tessere"; tutti e due aprono
  `SyncDialog`, che da spento si intitola "Backup e sincronizzazione". Stato letto in
  `HeaderSubtitle` (non in `MainActivity`), così `Sync` resta creato in background da `onStart`.
  Provato sull'emulatore (link, finestra, "salvate oggi alle …" dopo aver creato il file). Uscito
  nella **0.2.1** (20/11/2026, pre-release). APK release 2,37 MB.
- Icona: la tessera con la faccia che urla, inclinata di 8°, su verde acqua (vettoriale): icona
  adattiva + monocromatica + logo dell'intestazione. Le mani del concept "Carta canta" sono state
  tolte su richiesta dell'utente. APK release 2,06 MB. Dal 13/11/2026 bocca a spicchio curvo (disegno
  dell'utente, per non somigliare a Pac-Man), tessera dell'icona più grande e logo a 52 dp.
- Repo: git locale su branch `main`, remote `origin` = https://github.com/LoLler111/CantaCard
  (pubblico). **Push solo su richiesta esplicita dell'utente.** Licenza GPL-3.0 (`LICENSE`,
  scelta dell'utente), `README.md` in italiano.
- Distribuzione: per ora solo **GitHub Releases** (scelta dell'utente), APK release firmato con la
  chiave vera, chiamato `CantaCard-<versionName>.apk`. Le 0.x escono come pre-release finché non è
  provata su telefoni veri. Per una nuova versione: alzare `versionCode` (+1) e `versionName` in
  `app/build.gradle.kts`, build e test, verificare il certificato con `apksigner`, commit, push,
  poi `gh release create v<versionName>` con l'APK e le note in italiano (il tag deve essere
  esattamente `v<versionName>` e l'APK un asset `.apk`: li legge il controllo aggiornamenti
  dell'app, che avvisa subito tutti, pre-release comprese). Prima release: v0.1.0
  (13/11/2026). Play Store, F-Droid e Obtainium-friendly in seguito, da decidere.
- Identità git del repo (config locale, scelta dall'utente): `LoLler111`
  `<59455683+LoLler111@users.noreply.github.com>`.
- `applicationId`: **`io.github.loller111.cantacard`, definitivo** (scelto dall'utente il 13/11/2026:
  convenzione per chi non ha un dominio, legato all'account GitHub). Non cambiarlo mai: dopo la
  pubblicazione sarebbe un'altra app. Il `namespace` Kotlin/R resta `dev.cantacard` (invisibile).
- URL di aggiornamento del catalogo:
  `https://raw.githubusercontent.com/LoLler111/CantaCard/main/catalog/stores.json`.

## Decisioni prese

| Tema | Decisione | Motivo |
|---|---|---|
| Linguaggio / UI | Kotlin + Jetpack Compose (Material 3), confermato dall'utente | Costa ~1,5–2 MB stimati rispetto alle View del framework, accettati per velocità di sviluppo (da misurare dopo la fase 1). Niente `material-icons-extended`: solo vector drawable XML propri. Baseline profile (`profileinstaller`) per l'avvio a freddo. |
| Grafica | Palette fissa del marchio (verde acqua `#0F766E` + corallo `#FF7A59`), niente colori dinamici; tessere a tinta piatta nel colore del negozio, angoli arrotondati, senza sfumature, riflessi né ombre colorate (scelta dell'utente, per accordarle all'intestazione) con `ui.components.CardArt`; intestazioni in verde pieno senza sfumatura (`headerColor`) con la tessera dell'icona a colori come logo; schermata di avvio tutta verde (`Theme.CantaCard.Launch`, solo per MainActivity, che in `onCreate` torna a `Theme.CantaCard`); tutto in Compose/Canvas, nessun font o immagine aggiunti | L'utente ha chiesto una grafica più accattivante senza appesantire l'app (costo misurato: ~20 KB). Colori in `ui/Theme.kt`. |
| Persistenza | **JSON locale** (`org.json` del framework + `android.util.AtomicFile`), niente Room | Poche decine di record letti tutti in memoria: 0 KB di dipendenze, nessun KSP, nessuna apertura DB all'avvio, scrittura atomica. Lo stesso formato serve per il backup. |
| Generazione codici | ZXing `core` | Leggero, nessuna dipendenza Android. |
| Scansione | ZXing `core` (già presente) + CameraX 1.6 (`camera-camera2`, `camera-lifecycle`, `camera-compose`) | ML Kit con modello incluso costa ~2,4 MB in più; la variante via Play Services/Google Code Scanner scarica il modello e dipende da GMS (non offline). ZXing basta per tessere piatte (EAN/Code128/QR). Si aggiunge anche "scansiona da immagine" via Photo Picker, a costo zero. |
| Navigazione | Stato Compose semplice (sealed class + `BackHandler`), niente Navigation Compose | Meno dipendenze. |
| DI | Manuale (container nell'`Application`), niente Hilt/Koin | Meno dipendenze e avvio più rapido. |
| Rete | `HttpURLConnection` del framework, niente OkHttp/Retrofit/Jsoup | La rete serve solo per icone e catalogo; parsing HTML minimo dei tag `<link>`. |
| Immagini | Loader proprio (`image.ImageLoader`, LruCache 8 MB), niente Coil/Glide; ritaglio con composable proprio, niente uCrop. Icone ≤ 256 px; foto della tessera ritagliata al rapporto ID-1 e larga 512 px (riempie la tessera in lista, ~20–40 KB). Il tipo si deduce dal rapporto (largo = foto a tutta tessera, altrimenti icona nel tondo). Icona scaricata da sola quando si sceglie un negozio dal catalogo (scelta dell'utente) e mai sopra una foto; pulsante "Aggiorna icona" per riscaricarla (scelta dell'utente). Cache per dominio (`image.SiteIconCache`, `cache/site-icons/<dominio>.webp`, scelta dell'utente): la scelta del negozio la riusa per 30 giorni, poi la riscarica; senza rete usa la copia vecchia; "Aggiorna icona" scavalca sempre la cache. Il fetcher prova anche `www.<dominio>` se il dominio nudo non dà la pagina, legge solo i primi 256 KB della pagina (i `<link>` stanno in `<head>`; MediaWorld e Toys Center superano 1 MB) e, se il sito ha solo una favicon piccola (12–31 px, es. Esselunga 16×16), la usa ingrandita di un fattore intero senza filtro (pixel netti). Verifica sull'emulatore (13/11/2026): icona per 71 negozi su 79 con dominio; restano senza Douglas e Bricocenter (anti-bot), KIKO, Piazza Italia e Pittarello (solo SVG), Trony (certificato non accettato). Niente aggiramenti delle protezioni anti-bot dei siti (niente User-Agent finti da Chrome, niente servizi di terzi): chi blocca resta senza icona. Foto con `ImageCapture` di CameraX già inclusa | Peso; nessun permesso storage. |
| Colore automatico | Dall'icona del negozio (`image.IconColors`, scelta dell'utente del 14/11/2026), poi dal catalogo, poi dal nome. Si ignorano pixel trasparenti, bianchi, neri e grigi; vince la tinta con più superficie (fasce di 15°, pesate per saturazione), media pesata per saturazione²; meno del 4% di colore = nessun colore (logo in bianco e nero). Se il colore del catalogo (saturo) è almeno il 20% del colore del logo vince lui: nei loghi a due colori resta il colore del marchio (Lidl blu, non giallo). Solo per le icone, mai per le foto. Una tessera salvata ha il colore automatico se è quello del nome, del catalogo (attuale o `formerColors`) o della sua icona salvata (letta in background all'apertura del modulo); il colore automatico cambiato da solo non conta come modifica. Le tessere già salvate prendono il nuovo colore quando si salva il modulo o con "Aggiorna icona" | Nessuna libreria (niente `androidx.palette`), APK invariato; il colore corrisponde al logo anche per "Altro negozio" con icona e per i negozi futuri. |
| Backup | Zip scelto dall'utente via SAF, nessun permesso. Ripristino: se ci sono già tessere si chiede ogni volta "Aggiungi alle attuali" (stesso id: vince la versione con `updatedAt` più recente, a parità il telefono, statistiche d'uso unite; id diverso ma stesso numero+formato: doppione, resta il telefono) o "Sostituisci tutte" (scelta dell'utente). Niente password sul file e niente backup periodico (scelte dell'utente). Resta attivo anche l'Auto Backup di Android (`allowBackup`) | Zero dipendenze (`java.util.zip`); il formato tollera versioni future. |
| Sincronizzazione (fase 6b) | Un file di sincronizzazione scelto una volta dall'utente con il SAF (es. su Drive), aggiornato dall'app a ogni modifica e riletto all'apertura; due telefoni tenuti allineati (scelte dell'utente). Niente API di Google Drive né "Accedi con Google". Regole (`SyncMerge`): stesso id vince `updatedAt` più recente (a parità il telefono), statistiche d'uso unite; una cancellazione vince se successiva all'ultima modifica; stessa tessera aggiunta su due telefoni (id diversi, stesso codice, nuova da entrambe le parti) resta la creata prima. Le aperture in cassa non fanno partire la sincronizzazione. Un file che non è di CantaCard non viene mai sovrascritto | Nessuna libreria, nessun progetto Google Cloud, funziona con qualsiasi app di archiviazione. Ogni telefono tiene tutte le tessere: il file serve solo allo scambio, quindi una scrittura persa si recupera alla sincronizzazione successiva. |
| Accesso rapido | Scorciatoie: preferite, poi le più usate di recente, al massimo 4 (`CardQuery.sorted`), id = id della tessera, così anche quelle fissate in Home si aggiornano o si disattivano se la tessera sparisce. Due widget (scelta dell'utente): "Tessere preferite" (stesso ordine, fino a 2 righe da 4 secondo la dimensione) e "Tessera" (una a scelta, riconfigurabile). `ShowCardActivity` ha `taskAffinity` propria: aperta da scorciatoie e widget non chiude la lista o un modulo aperti. Widget con `AppWidgetProvider` + `RemoteViews` classici, niente Glance; nelle anteprime solo viste supportate dai RemoteViews (niente `View` generico) | Peso. |
| Visualizzazione tessera | `ShowCardActivity` separata e leggera | Apertura diretta da shortcut/widget; luminosità e schermo acceso limitati a quella finestra. |
| Schermate | Un'Activity per schermata (lista, tessera, modifica, scanner), risultati via `ActivityResultContract` | Niente libreria di navigazione; ogni schermata si apre da sola (shortcut, condivisione). |
| Letture fotocamera | Formati senza checksum robusto (Code 39/93, Codabar, ITF) confermati su 2 frame uguali | Evita letture sbagliate. Un frame su due è provato ruotato di 90°. |
| minSdk / target | minSdk 26, compileSdk 37, targetSdk 36 | minSdk 26 copre la quasi totalità dei dispositivi attivi (shortcut, icone adattive, `java.time`). compileSdk 37 è imposto dalle librerie Compose recenti. |
| Toolchain | AGP 9.1.1 (Kotlin integrato in AGP), Gradle 9.3.1, Kotlin 2.4.21, Compose BOM 2026.09.00 | AGP 9.1 è l'ultimo supportato da Android Studio Panda 2 installato; per salire (9.5+) va aggiornato Studio. Versioni in `gradle/libs.versions.toml`. |
| Aggiornamento app | Controllo a ogni avvio dell'app (una volta per processo, da `MainActivity.onStart`, in background a lista già mostrata) su `api.github.com/repos/LoLler111/CantaCard/releases`, **pre-release comprese**, bozze escluse; vale il `tag_name` (`v<versionName>`) più alto con un asset `.apk`. Riga sempre visibile (non chiudibile), **fissa sotto l'intestazione, fuori dalla griglia** (come primo elemento della griglia arrivava a lista già mostrata e la griglia, che resta ancorata alla prima tessera, la lasciava fuori schermo sotto l'intestazione: corretto nella 0.1.5), nascosta durante una ricerca. Al tocco si apre con `ACTION_VIEW` il `browser_download_url` dell'APK letto al controllo (mai un link fisso nel codice: si installa sempre direttamente l'ultima versione, saltando le intermedie); download e installazione li fanno il browser e Android. Senza rete o con errori il controllo non mostra nulla (scelte dell'utente) | Eccezione voluta alla regola "nessuna rete all'avvio": non blocca mai la lista né la visualizzazione delle tessere. Niente installazione dall'app né `REQUEST_INSTALL_PACKAGES`: nella 0.1.2 facevano segnalare l'app da Play Protect come dannosa (scelta dell'utente dopo la segnalazione). Ogni versione deve saper leggere i dati di qualsiasi versione precedente, perché si possono saltare aggiornamenti. Per Play Store/F-Droid la riga andrà nascosta in quelle build. |
| Aggiornamento catalogo | Controllo automatico in background al massimo una volta a settimana, solo all'apertura della scelta negozio, silenzioso; più "Aggiorna catalogo" nel menu della stessa schermata (scelta dell'utente) | Nessuna rete all'avvio; senza rete si usa sempre la copia locale. Copia scaricata in `files/catalog/stores.json`, vince la `version` più alta. |
| Icona | Solo la tessera (banda magnetica, faccia corallo che urla) inclinata di 8° con ombra leggera; le mani del concept iniziale sono state scartate dall'utente. Vector drawable in coordinate px (tessera 315–937 × 255–708), scala 0,085 (translate 0,79 / 13,07, centro a 54,54): a 0,07 la tessera sembrava piccola sul telefono (osservazione dell'utente), a 0,09 l'angolo in alto a destra arrivava a filo del tondo del launcher Pixel. Bocca a spicchio **curvo** (disegno dell'utente): faccia `M639,485 A99,99 0 1,0 646,610 A65,65 0 0,1 639,485 Z`, occhio in 557,501; lo spicchio dritto con la punta al centro somigliava a Pac-Man (marchio registrato Bandai Namco). Logo dell'intestazione `ic_logo` mostrato a 52 dp. Niente PNG | Pochi KB, nitida a ogni densità. |
| Lingue | Italiano predefinito (`values/`), inglese in `values-en/`; `localeFilters = ["it", "en"]` | Elimina dall'APK le altre traduzioni delle librerie. Aggiungere ogni nuova lingua quando si traduce; ogni stringa nuova va scritta in entrambi i file. |
| Ordine della lista | Preferite sempre in cima, poi "Usate di recente" (predefinito), "Per nome" o "Personalizzato" (`SortOrder`, preferenza solo locale in `ListPrefs`). Riordino trascinando per la maniglia (`ui.list.ReorderList`, senza librerie), preferite e altre separatamente. `Card.rank` è un `Double`: spostare una tessera tra due altre le dà la posizione a metà, così cambia (e si sincronizza) solo lei; si rinumera solo se mancano posizioni o non c'è spazio (`CardQuery.reorder`) | Nessuna libreria, sincronizzazione semplice. |
| Scorciatoie, widget, riquadro | Escludono le archiviate (`CardQuery.quickAccess`); scorciatoie e widget restano su preferite + uso recente qualunque sia l'ordine della lista; il riquadro rapido segue l'ordine della lista | |
| Importazione da altre app | Catima: colonne riconosciute per nome (formato 1 e 2), gruppi → etichette, `barcodeid` come contenuto del codice (il `cardid` diverso finisce nella nota), immagini `card_<id>_front/back/icon.png` convertite in WebP. Zip con password non supportati (`java.util.zip`): si chiede di esportare senza. Stocard: da fare solo con un file vero | Zero dipendenze. |
| Moduli | Un solo modulo `:app` | Progetto piccolo, multi-modulo non porta benefici. |

## Struttura prevista

```
CantaCard/
├── CLAUDE.md
├── catalog/
│   └── stores.json            # sorgente unica del catalogo: inclusa negli asset dell'APK
│                              # (srcDir Gradle) e servita raw da GitHub per l'aggiornamento
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/values/strings.xml   # tutte le stringhe UI, in italiano
│       └── java/dev/cantacard/
│           ├── CantaCardApp.kt      # Application + container dipendenze
│           ├── MainActivity.kt      # lista, aggiunta, impostazioni
│           ├── ShowCardActivity.kt  # visualizzazione tessera
│           ├── data/                # Card, CardRepository (JSON), Catalog, Backup (zip)
│           ├── barcode/             # generazione e scansione (ZXing)
│           ├── image/               # favicon fetcher, ridimensionamento WebP, loader, crop
│           ├── ui/                  # schermate Compose, tema
│           ├── shortcuts/           # app shortcuts dinamiche
│           └── widget/              # widget RemoteViews
├── gradle/libs.versions.toml
└── build.gradle.kts / settings.gradle.kts / gradlew
```

## Modello dati

- `Card`: `id` (UUID), `name`, `storeId?`, `number`, `format` (nome dell'enum ZXing, es. `"EAN_13"`,
  mai l'ordinal), `color`, `image?` (nome file), `favorite`, `lastUsed`, `useCount`, `createdAt`, `note?`, `updatedAt` (ultima modifica dell'utente: salvataggio e preferita, non l'apertura in cassa; 0 = sconosciuta).
  Dalla 0.2.0: `backImage?` (foto del retro, `files/images/<id>-back-<versione>.webp`, nel backup
  `images/<id>-back.webp`), `expiry?` (data ISO `yyyy-MM-dd`), `labels` (lista, senza doppioni),
  `archived`, `rank?` (ordine personalizzato). Scritti nel JSON solo se diversi dal default: le
  versioni precedenti li ignorano. **Ogni cambio di formato: aggiungere i file della nuova versione
  in `src/test/resources/compat/` e i test in `DataCompatibilityTest`, senza togliere i vecchi.**
- Salvataggio: `files/cards.json` = `{"version": 1, "cards": [...], "deleted": [{"id", "at"}]}` (cancellate, per la sincronizzazione); immagini in `files/images/<id>-<versione>.webp` (nome nuovo a ogni sostituzione, la vecchia si cancella; provvisorie in `cache/edit-images/`).
- Lettura tollerante: campi sconosciuti ignorati, campi mancanti con default, un record corrotto non
  deve bloccare gli altri.
- Si salva **sempre** il formato del codice oltre al numero.

## Funzioni

1. **Lista tessere**: griglia/lista con immagine o iniziale colorata, ricerca, preferite in cima,
   poi ordinamento per uso recente.
2. **Visualizzazione**: codice grande su sfondo bianco con quiet zone abbondante, luminosità al massimo
   (`WindowManager.LayoutParams.screenBrightness = 1f`, nessun permesso) e `FLAG_KEEP_SCREEN_ON`
   finché è aperta, numero leggibile sotto.
3. **Aggiunta**: negozio da catalogo con ricerca (stile Google Wallet) con voce "Altro negozio" sempre
   visibile; poi scansione offline o inserimento manuale con scelta del formato. Il formato rilevato
   dalla scansione prevale su quello suggerito dal catalogo. **Doppioni** (dalla 0.1.6, richiesta
   dell'utente): prima di salvare, se c'è già una tessera con lo stesso numero (spazi e maiuscole
   ignorati) per lo stesso negozio (stesso `storeId`, oppure stesso nome per "Altro negozio"),
   il dialogo "Tessera già presente" chiede "Aggiungi comunque" / "Annulla" (`CardQuery.findDuplicate`).
   In modifica avvisa solo se è la modifica a creare il doppione.
4. **Immagine**, in ordine di preferenza:
   1. icona dal sito del negozio, scaricata una volta alla scelta: prima l'eventuale `icon` del
      catalogo, poi manifest PWA → apple-touch-icon →
      `link rel="icon"` → `/favicon.ico` (solo HTTPS, timeout brevi, limite di dimensione, SVG scartati);
   2. foto del fronte della tessera con ritaglio;
   3. galleria tramite Photo Picker (nessun permesso storage);
   4. immagine condivisa da altre app (`ACTION_SEND` `image/*`);
   5. ripiego: iniziale su colore scelto dall'utente.

   Sempre ridimensionata a ~256 px WebP e salvata in locale.
   **Vietato**: scraping di Google ed endpoint favicon non ufficiali (Google s2, DuckDuckGo, ecc.).
5. **Backup/ripristino** via Storage Access Framework (l'utente sceglie dove: Drive, OneDrive, ...).
   File `.zip` con `cantacard.json` (`formatVersion`, `exportedAt`, tessere) + `images/<id>.webp`.
   Il ripristino deve gestire versioni future del formato senza crash (campi sconosciuti ignorati,
   errori isolati per singola tessera, avviso se `formatVersion` è più recente).
6. **Accesso rapido**: app shortcuts dinamiche per le preferite, poi widget.

## Catalogo negozi

- `catalog/stores.json`: `{"version": N, "stores": [{ "id", "name", "aliases": [], "domain",
  "color": "#RRGGBB", "format", "countries": ["IT"], "icon"? }]}`.
- `icon` (facoltativo, dalla versione 5, scelta dell'utente): indirizzo HTTPS di un'immagine del logo
  **sul sito del negozio** (stesso dominio o sottodominio, mai servizi di terzi, niente SVG), provato
  prima della ricerca automatica; serve per i siti che dichiarano solo icone SVG. Bordi trasparenti
  tagliati e logo centrato su un quadrato trasparente (resta un'icona nel tondo bianco, non una foto).
  Stessa cache per dominio delle altre icone. Un indirizzo non valido viene ignorato (il test
  `bundledCatalogIsValid` lo segnala). Oggi solo CTS (`cts_logo_350x100.png`, l'`og:image` del sito).
  Le versioni dell'app prima della 0.1.6 ignorano il campo.
- Incluso nell'APK; aggiornamento opzionale scaricando lo stesso file raw da questo repo GitHub.
  Si usa la copia con `version` più alta tra inclusa e scaricata; senza rete si usa sempre la copia locale.
- Prima versione: 40–50 catene italiane (supermercati, profumerie, elettronica, abbigliamento,
  carburanti). **Se un dominio o un formato non è certo, lasciarlo vuoto/segnalarlo: mai inventarlo.**
- Ampliamento (versione 3, scelta dell'utente): 27 catene regionali e di settore aggiunte solo se il dominio risponde con il sito della catena **e** il sito cita la tessera fedeltà (es. Il Castoro "Fidelity Card", Pewex "Carta IN", Oasi Tigre "Carta Unika", Sigma "Prèmiaty"). Scartate per dominio incerto o irraggiungibile: Sisa, Tosano, Elite, Tuodì, U2, Sidis, Mercatò, Scarpe&Scarpe; GameStop Italia ora è GameLife. Colori: da `theme-color` o dai colori più usati nel sito; dove non c'era un colore chiaro (Ethos, Kiabi) grigio neutro `#37474F`.
- Versione 4 (13/11/2026, elenco dell'utente, stesse regole): PittaRosso, Action (carta cliente digitale nell'app), UCI Cinemas (myUCI), Sport85 (85FAN), Stroili, Nuvolari, Casabalò e le insegne del consorzio romano GROS con tessera verificata (CTS, PIM, IperTriscount, DEM, MA, Sacoph, Idromarket; con Castoro e Pewex hanno l'alias "Gros"). Scartate: Top Supermercati, Effepiù e Ipercarni (GROS, nessuna tessera citata nel sito), Scarpe&Scarpe (certificato del sito non verificabile), Stellazeta (nessun sito ufficiale trovato; `stellazeta.com` rimanda a un altro sito). Panorama era già alias di Pam, Conbipel già presente.
- Versione 5 (14/11/2026): `icon` per CTS, il cui sito offre il logo solo come SVG (anche come
  `/favicon.ico`). KIKO, Piazza Italia e Pittarello restano senza: nessun PNG del logo sul loro sito.
  Nella stessa versione colori corretti per 25 negozi il cui colore non corrispondeva al logo
  (segnalazione dell'utente), confrontandolo con il colore calcolato dall'icona del sito con lo
  stesso algoritmo dell'app: Esselunga, Pam, Despar, Famila, Alì, A&O, Dok, Pan, Idromarket, Tigotà,
  Acqua & Sapone, Ethos, Bottega Verde, Yves Rocher, Unieuro, Trony, OVS, Upim, Tezenis, Conbipel,
  Sport85, Risparmio Casa, Action, Casabalò, Enilive. Lasciati com'erano i loghi a due colori in cui il
  colore del catalogo c'è già (Lidl, Conad, MD, Pewex, UCI, DEM, Sigma, Oasi Tigre) e quelli non
  verificabili (sito che non risponde a un client generico o icona solo SVG/WebP: Bennet, Aldi, Unes,
  Iperal, Sole 365, CTS, Sephora, Douglas, KIKO, Marionnaud, H&M, Decathlon, Piazza Italia, Kiabi,
  Pittarello, PittaRosso, Leroy Merlin, Bricocenter) o dubbi (Stroili, Kasanova, Arcaplanet, Sacoph,
  Decò, Toys Center). `formerColors`: i colori precedenti di un negozio, così una tessera salvata col
  vecchio colore automatico resta "automatica" e segue quello nuovo (**quando si cambia un colore,
  aggiungere il vecchio a `formerColors`**). Le versioni precedenti dell'app ignorano il campo.
- Stato attuale: `format` vuoto per tutti (nessuno certo; vale quello scansionato). `domain` vuoto per
  Maxi Zoo (confluita in Arcaplanet, sito che non risponde) ed Esso (in Italia nessun sito; la
  fedeltà passa da PAYBACK). Trony = `trony.it` dalla versione 2. I colori sono approssimati dal sito o
  dal marchio. Chi aggiorna il catalogo deve alzare `version`; il test `bundledCatalogIsValid` lo valida.

## Permessi

Solo `CAMERA` (scansione e foto) e `INTERNET` (icone, catalogo e controllo aggiornamenti, sempre
opzionali). Nessun permesso storage. **Mai `REQUEST_INSTALL_PACKAGES`**: con la 0.1.2, che lo usava
per installare gli aggiornamenti, Play Protect segnalava l'app come dannosa (tolto nella 0.1.3).

## Modo di lavorare

- Fasi piccole e funzionanti, **un commit per fase**:
  1. scheletro + modello dati → 2. visualizzazione → 3. aggiunta/scansione → 4. catalogo →
  5. immagini → 6. backup → 7. shortcut/widget.
- Prima di ogni fase: descrivere in breve cosa si farà. Fermarsi e chiedere quando c'è una scelta
  non già decisa qui.
- Testo UI in italiano, sempre in `res/values/strings.xml` (mai stringhe hardcoded nei composable).
- Dopo ogni fase controllare la dimensione dell'APK release.

## Ambiente di sviluppo (Windows)

- Android Studio 2025.3 installato; JDK: usare la JBR di Studio
  (`JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`, `java` non è nel PATH).
- SDK in `%LOCALAPPDATA%\Android\Sdk` (platform 36 / 36.1, build-tools 36.1.0), AVD `Medium_Phone_API_36.1`.
- `local.properties` (non versionato) contiene `sdk.dir=C:/Users/Utente/AppData/Local/Android/Sdk`.
- Build da riga di comando: `./gradlew testDebugUnitTest assembleDebug assembleRelease`.
- Firma della release: chiave vera creata dall'utente il 13/11/2026, **fuori dal repo** in
  `%USERPROFILE%\cantacard-keys\` (`cantacard-release.jks`, alias `cantacard`, RSA 4096, CN=LoLler111,
  validità 10000 giorni) con `keystore.properties` (password: non leggerlo mai, la usa solo Gradle;
  percorso alternativo con la variabile `CANTACARD_SIGNING`). Senza quel file la release si firma con
  la chiave di debug e Gradle avvisa: quell'APK non va mai distribuito. Impronte del certificato
  (servono per OAuth/Play): SHA-1 `6c:ae:06:2b:7f:dd:4d:71:f1:b8:81:56:9a:dc:98:dd:61:8a:86:e1`,
  SHA-256 `46:21:95:2d:9c:35:0b:f3:6e:c9:4d:22:35:94:45:3b:87:45:74:96:e7:73:67:8c:b8:ea:10:e9:0e:80:a6:e5`.
  Con il Play Store (Play App Signing) questa diventa la chiave di caricamento, salvo caricare la
  propria come chiave di firma: da decidere quando si sceglie la distribuzione.
- Le build di debug mostrano nella lista vuota il pulsante "Aggiungi tessere di prova".
