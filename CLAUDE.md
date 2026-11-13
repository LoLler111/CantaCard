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
- Fase 4 (catalogo) completata: `catalog/stores.json` (53 catene; ampliato a 81 con catene regionali, versione 3), `StorePickerActivity` con ricerca
  e "Altro negozio", negozio nel modulo (cambiabile), aggiornamento da GitHub. APK release 2,06 MB.
- Fase 5 (immagini) completata: icona dal sito scaricata da sola alla scelta del negozio (+ "Aggiorna
  icona"), foto della tessera (`scan.PhotoActivity`) e galleria con ritaglio (`ui.edit.CropDialog`),
  immagine condivisa da altre app (`ShareImageActivity`, legge anche il codice), scelta del colore.
  APK release 2,16 MB (+100 KB: soprattutto ImageCapture di CameraX).
- Fase 6 (backup) completata: `data.Backup` + `data.BackupFormat` (zip via SAF con `cantacard.json` + `images/<id>.webp`), menu ⋮ della lista ("Esporta backup", "Ripristina backup") e "Hai un backup? Ripristinalo" nella lista vuota; al ripristino `RestoreDialog` chiede "Aggiungi alle attuali" o "Sostituisci tutte". APK release 2,18 MB. Prossima: fase 6b, sincronizzazione.
- Icona: la tessera con la faccia che urla, inclinata di 8°, su verde acqua (vettoriale): icona
  adattiva + monocromatica + logo dell'intestazione. Le mani del concept "Carta canta" sono state
  tolte su richiesta dell'utente. APK release 2,06 MB.
- Repo: git locale su branch `main`, remote `origin` = https://github.com/LoLler111/CantaCard
  (pubblico, vuoto). **Push solo su richiesta esplicita dell'utente.**
- Identità git del repo (config locale, scelta dall'utente): `LoLler111`
  `<59455683+LoLler111@users.noreply.github.com>`.
- `applicationId`: **provvisorio `dev.cantacard`**, da decidere prima della pubblicazione (è permanente
  dopo). Il `namespace` Kotlin/R resta `dev.cantacard` anche se l'applicationId cambia.
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
| Backup | Zip scelto dall'utente via SAF, nessun permesso. Ripristino: se ci sono già tessere si chiede ogni volta "Aggiungi alle attuali" (stesso id: vince la versione con `updatedAt` più recente, a parità il telefono, statistiche d'uso unite; id diverso ma stesso numero+formato: doppione, resta il telefono) o "Sostituisci tutte" (scelta dell'utente). Niente password sul file e niente backup periodico (scelte dell'utente). Resta attivo anche l'Auto Backup di Android (`allowBackup`) | Zero dipendenze (`java.util.zip`); il formato tollera versioni future. |
| Sincronizzazione (fase 6b) | Un file di sincronizzazione scelto una volta dall'utente con il SAF (es. su Drive), aggiornato dall'app a ogni modifica e riletto all'apertura; due telefoni tenuti allineati (scelte dell'utente). Niente API di Google Drive né "Accedi con Google" | Nessuna libreria, nessun progetto Google Cloud, funziona con qualsiasi app di archiviazione. `updatedAt` per tessera c'è già; servirà un registro delle tessere cancellate. |
| Widget | `AppWidgetProvider` + `RemoteViews` classici, niente Glance | Peso. |
| Visualizzazione tessera | `ShowCardActivity` separata e leggera | Apertura diretta da shortcut/widget; luminosità e schermo acceso limitati a quella finestra. |
| Schermate | Un'Activity per schermata (lista, tessera, modifica, scanner), risultati via `ActivityResultContract` | Niente libreria di navigazione; ogni schermata si apre da sola (shortcut, condivisione). |
| Letture fotocamera | Formati senza checksum robusto (Code 39/93, Codabar, ITF) confermati su 2 frame uguali | Evita letture sbagliate. Un frame su due è provato ruotato di 90°. |
| minSdk / target | minSdk 26, compileSdk 37, targetSdk 36 | minSdk 26 copre la quasi totalità dei dispositivi attivi (shortcut, icone adattive, `java.time`). compileSdk 37 è imposto dalle librerie Compose recenti. |
| Toolchain | AGP 9.1.1 (Kotlin integrato in AGP), Gradle 9.3.1, Kotlin 2.4.21, Compose BOM 2026.09.00 | AGP 9.1 è l'ultimo supportato da Android Studio Panda 2 installato; per salire (9.5+) va aggiornato Studio. Versioni in `gradle/libs.versions.toml`. |
| Aggiornamento catalogo | Controllo automatico in background al massimo una volta a settimana, solo all'apertura della scelta negozio, silenzioso; più "Aggiorna catalogo" nel menu della stessa schermata (scelta dell'utente) | Nessuna rete all'avvio; senza rete si usa sempre la copia locale. Copia scaricata in `files/catalog/stores.json`, vince la `version` più alta. |
| Icona | Solo la tessera (banda magnetica, faccia corallo che urla) inclinata di 8° con ombra leggera; le mani del concept iniziale sono state scartate dall'utente. Vector drawable in coordinate px (tessera 315–937 × 255–708), scala 0,07 così gli angoli restano nella zona sicura: a 0,08 il launcher Pixel li tagliava. Niente PNG | Pochi KB, nitida a ogni densità. Prima della pubblicazione valutare di differenziare la faccia (somiglia a Pac-Man, marchio registrato). |
| Lingue | `localeFilters = ["it"]` | Elimina dall'APK le traduzioni delle librerie. Aggiungere ogni nuova lingua quando si traduce. |
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
- Salvataggio: `files/cards.json` = `{"version": 1, "cards": [...]}`; immagini in `files/images/<id>-<versione>.webp` (nome nuovo a ogni sostituzione, la vecchia si cancella; provvisorie in `cache/edit-images/`).
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
   dalla scansione prevale su quello suggerito dal catalogo.
4. **Immagine**, in ordine di preferenza:
   1. icona dal sito del negozio, scaricata una volta alla scelta: manifest PWA → apple-touch-icon →
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
  "color": "#RRGGBB", "format", "countries": ["IT"] }]}`.
- Incluso nell'APK; aggiornamento opzionale scaricando lo stesso file raw da questo repo GitHub.
  Si usa la copia con `version` più alta tra inclusa e scaricata; senza rete si usa sempre la copia locale.
- Prima versione: 40–50 catene italiane (supermercati, profumerie, elettronica, abbigliamento,
  carburanti). **Se un dominio o un formato non è certo, lasciarlo vuoto/segnalarlo: mai inventarlo.**
- Ampliamento (versione 3, scelta dell'utente): 27 catene regionali e di settore aggiunte solo se il dominio risponde con il sito della catena **e** il sito cita la tessera fedeltà (es. Il Castoro "Fidelity Card", Pewex "Carta IN", Oasi Tigre "Carta Unika", Sigma "Prèmiaty"). Scartate per dominio incerto o irraggiungibile: Sisa, Tosano, Elite, Tuodì, U2, Sidis, Mercatò, Scarpe&Scarpe; GameStop Italia ora è GameLife. Colori: da `theme-color` o dai colori più usati nel sito; dove non c'era un colore chiaro (Ethos, Kiabi) grigio neutro `#37474F`.
- Stato attuale: `format` vuoto per tutti (nessuno certo; vale quello scansionato). `domain` vuoto per
  Maxi Zoo (confluita in Arcaplanet, sito che non risponde) ed Esso (in Italia nessun sito; la
  fedeltà passa da PAYBACK). Trony = `trony.it` dalla versione 2. I colori sono approssimati dal sito o
  dal marchio. Chi aggiorna il catalogo deve alzare `version`; il test `bundledCatalogIsValid` lo valida.

## Permessi

Solo `CAMERA` (scansione e foto) e `INTERNET` (icone e catalogo, sempre opzionali).
Nessun permesso storage.

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
- La release è firmata con la chiave di debug solo per provarla in locale: prima della
  pubblicazione serve la chiave vera (fuori dal repo).
- Le build di debug mostrano nella lista vuota il pulsante "Aggiungi tessere di prova".
