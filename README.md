# CantaCard

App Android ultra leggera per le tessere fedeltà: mostra a schermo il codice a barre o QR da far
leggere in cassa, subito, anche senza connessione.

- **Sempre offline**: mostrare una tessera non richiede mai rete, account o aggiornamenti.
- **Leggera**: APK di circa 2 MB, avvio istantaneo.
- **Niente account, pubblicità, tracciamento o server**: le tessere restano sul telefono.

## Funzioni

- Aggiunta con la fotocamera (scansione offline), da una foto o a mano, con scelta del formato
  (EAN, Code 128, QR e gli altri formati comuni).
- Catalogo di catene italiane con ricerca; per i negozi non in elenco c'è "Altro negozio".
- Codice a tutto schermo con luminosità al massimo e schermo sempre acceso finché è aperto.
- Icona del negozio scaricata dal suo sito (a scelta), foto della tessera o iniziale colorata.
- Foto del retro, data di scadenza (con avviso nella lista), etichette e note.
- Preferite in cima, poi le tessere usate più di recente, per nome o nell'ordine che scegli tu;
  ricerca e filtro per etichetta; archivio per le tessere che usi di rado.
- In cassa si scorre da una tessera all'altra; i codici a barre si possono girare di 90° per i
  lettori più vecchi.
- Backup e ripristino su un file `.zip` salvato dove vuoi (Drive, OneDrive, memoria del telefono…).
- Sincronizzazione tra due telefoni tramite un file condiviso, per esempio su Drive.
- Importazione delle tessere da [Catima](https://catima.app/) (file esportato da Catima, senza
  password).
- Scorciatoie tenendo premuta l'icona, icona della tessera in Home, due widget e il riquadro
  "Tessere" nelle impostazioni rapide.
- In italiano e in inglese.

## Installazione

Scarica il file `.apk` dall'ultima versione nella pagina
[Releases](https://github.com/LoLler111/CantaCard/releases) e aprilo dal telefono. Android chiede
di consentire l'installazione da quella app (il browser o il gestore file): è normale per le app
installate fuori dal Play Store.

Requisiti: Android 8.0 o successivo.

Per ricevere gli aggiornamenti in automatico puoi usare [Obtainium](https://obtainium.imranr.dev/)
con l'indirizzo di questo repository.

Le versioni 0.x sono anteprime: non ancora provate su tutti i telefoni.

## Permessi

- **Fotocamera**: solo per scansionare il codice o fotografare la tessera, chiesto la prima volta
  che la usi.
- **Internet**: solo per scaricare le icone dei negozi, gli aggiornamenti del catalogo e per sapere
  se c'è una nuova versione, sempre facoltativi. Nessun dato viene inviato.

Nessun permesso di accesso ai file: backup e sincronizzazione usano il selettore di file di Android.

## Catalogo negozi

Il catalogo è in [`catalog/stores.json`](catalog/stores.json): è incluso nell'app e lei lo
riscarica da qui al massimo una volta a settimana. Segnalazioni e correzioni (dominio, colore, nuove
catene) sono benvenute nelle Issues. L'app non contiene loghi dei negozi: le icone si scaricano dai
siti, solo se lo scegli, e restano sul telefono.

## Compilare

Serve l'Android SDK (Android Studio). Dalla cartella del progetto:

```
./gradlew testDebugUnitTest assembleRelease
```

Senza la chiave di firma dell'autore la release viene firmata con la chiave di debug.

## Licenza

Copyright © 2026 LoLler111

Questo programma è software libero: puoi ridistribuirlo e/o modificarlo secondo i termini della
GNU General Public License versione 3, pubblicata dalla Free Software Foundation. È distribuito
senza alcuna garanzia. Il testo completo è nel file [LICENSE](LICENSE).
