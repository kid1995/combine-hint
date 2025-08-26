# Hint Service

# Entwicklung auf der DEV VM

Führe folgende Schritte aus um das Projekt lokal auf der DEV VM zu entwickeln.

## Projekt Klonen/Öffnen

1. Klone das Projekt.
2. Wechsle zum develop Branch.
3. Öffne das Projekt mit IntelliJ IDEA (Ultimate Edition).

## Docker Container starten

1. Im Projekt befindet sich eine **docker-compose.yml** Datei, mit der du die Fremdapplikationen starten kannst, die für
   das Projekt benötigt werden.
2. Starte den Kafka-Container über die **docker-compose.yml** Datei. Dies kannst du entweder in IntelliJ machen oder
   über das Terminal. Zusätzlich zum Kafka-Container wird auch ein Zookeeper-Container gestartet.
3. Starte die MongoDb über die **docker-compose.yml** Datei. Dies kannst du entweder in IntelliJ machen oder
   über das Terminal.

## Spring Boot Anwendung starten

1. Öffne die Klasse **HintApplication.java** und starte die Spring Boot Anwendung, indem du auf den
   Play-Button neben der "public void main" Methode klickst.
2. Die Anwendung ist gestartet, wenn du in der Konsole folgende Zeile siehst: Tomcat started on port(s): 8081 (http)
   with context path ''. Es fehlen jedoch noch einige Umgebungsvariablen, die wir manuell setzen müssen.
3. Öffne die Run Configuration in IntelliJ und füge der Spring Boot Anwendung folgende VM Options hinzu:
   -Dhttp.proxyHost=localhost -Dhttp.proxyPort=3128 -Dhttps.proxyHost=localhost -Dhttps.proxyPort=3128
   -Dhttps.nonProxyHosts="*.nonproxyrepos.com|localhost|m2repo.system.local|*.system.local" -Dhttp.nonProxyHosts="
   *.nonproxyrepos.com|localhost|m2repo.system.local|*.system.local"
5. Öffne die Run Configuration in IntelliJ und füge der Spring Boot Anwendung folgende Environment Options hinzu:
   MONGODB_OPTIONS=authSource=admin&authMechanism=SCRAM-SHA-1
6. Nach dem Starten öffne den Browser und rufe folgende URL auf: http://localhost:8081/health. Wenn der Status auf der
   Seite UP ist, dann ist die Anwendung hochgefahren.

Die API Definitionen kann man sich unter den folgenden URL's anschauen.

* TST: https://develop-hint-elpa-hint-tst.osot.system.local/docs/swagger-ui/index.html
* ABN: https://abn-hint-elpa-hint-abn.cloud-test.system.local/docs/swagger-ui/index.html
* Local DEV: http://localhost:8080/api/docs/swagger-ui/index.html