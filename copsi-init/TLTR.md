# copsi-init – TL;DR

Um einen neuen SDA-Service in OCP4 zu deployen, werden zwei Scripts aus dem Projekt `copsi-init` ausgeführt.

---

## Schritt 1 – Im Code-Repo ausführen

```shell
git clone ssh://git@git.system.local:7999/elpa/copsi-init.git &&\
cd copsi-init &&\
chmod +x init-copsi.sh &&\
rm -rf .git &&\
./init-copsi.sh &&\
cd .. &&\
rm -rf copsi-init
```

Am Ende gibt das Script drei Git-Links aus – einen pro Umgebung:

```
======================================================================
  ✅ ✅ ✅  Fertig!  ✅ ✅ ✅
======================================================================

  Commit : 05ec3e1b5519f98de92f57057b2b068829148fe8

Git-Links (nach erfolgreichem Jenkins-Build verwendbar):

  tst:  https://git.system.local/scm/elpa/hint.git//copsi/tst?ref=05ec3e1b5519f98de92f57057b2b068829148fe8
  abn:  https://git.system.local/scm/elpa/hint.git//copsi/abn?ref=05ec3e1b5519f98de92f57057b2b068829148fe8
  prod: https://git.system.local/scm/elpa/hint.git//copsi/prod?ref=05ec3e1b5519f98de92f57057b2b068829148fe8
```

Den passenden Link per Rechtsklick kopieren und für Schritt 2 bereithalten.

✅ Danach: **Jenkins-Build abwarten** – erst wenn der Build erfolgreich ist, weiter mit Schritt 2.

---

## Schritt 2 – Im Deploy-Repo (`elpa-elpa4`) ausführen

```shell
git clone ssh://git@git.system.local:7999/elpa/copsi-init.git &&\
cd copsi-init &&\
chmod +x init-service.sh &&\
./init-service.sh &&\
cd .. &&\
rm -rf copsi-init
```

Das Script fragt nach dem Git-Link aus Schritt 1 – den kopierten Link einfach einfügen:

```
--- Copsi Git-Link ---
Beispiel: https://git.system.local/scm/elpa/<service>.git//copsi/<env>?ref=<commit-hash>

Git-Link zum Copsi-Component: https://git.system.local/scm/elpa/hint.git//copsi/tst?ref=05ec3e1b5519f98de92f57057b2b068829148fe8
  🔍  Erkannt: service='hint'  env='tst'  ref='05ec3e1'
  📦  Image-Name: dev.docker.system.local/elpa-hint-tst/hint
  🔎  Prüfe Registry: https://dev.docker.system.local/v2/elpa-hint-tst/hint/tags/list
  ✅  Image-Tag gefunden: 05ec3e1.3

--- Infrastruktur-Komponenten (bleiben im Deploy-Repo) ---
Wird PostgreSQL verwendet? (ja/nein - j/n): j
Wird Kafka verwendet? (ja/nein - j/n): j
```

---

## Außerdem manuell erledigen

| # | Was | Wo |
|---|---|---|
| 1 | Kafka-Topics des Services eintragen | `elpa-elpa4/kafka/values.yaml` |
| 2 | Service in Umgebung registrieren (falls nicht automatisch) | `elpa-elpa4/envs/<env>/kustomization.yaml` |

---

## Hinweis – Jira

Der Jira-Ticket **ELPA4-504** ist für diese Initialisierung vorbereitet.
Branch und Commit-Nachrichten bitte mit diesem Ticket verknüpfen:

```
Branch:  ELPA4-504-<service-name>-init
Commit:  ELPA4-504: <service-name> add copsi config
```