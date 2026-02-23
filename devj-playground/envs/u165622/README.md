## U165622 - Dom Troubleshooting

## WIP (Work in Progress)

Die **WIP Dateien** in diesem Projekt sind zum Troubleshooting da. Sobald das Troubleshooting gelöst ist, wird es kommentiert und einem **Case** hinzugefügt. Das **Basis Setup** hat immer ein **default Alpine Deployment** mit einem **overridden Entrypoint**, um es am Laufen zu halten und **terminal access** via ArgoCD zu haben.

-----

## Case-01: Internal Namespace communication

*Die **Container Plattform** ist als "**defence in depth**" aufgesetzt. Deswegen ist alles jegliche Kommunikation **by default** denied. Link zum [**Securitykonzept**](https://www.google.com/search?q=https://wiki.system.local/spaces/COPLAT/pages/330384957/URP-0001-Securitykonzept-Servicekommunikation).*

Um eine **erfolgreiche Kommunikation** innerhalb eines **Namespace** in der **Container Plattform** zu etablieren:

- Eine **native Kubernetes NetworkPolicy** für **ingress** muss erstellt werden, um **eigehende** und **ausgehende Verbindungen** für den **Namespace** selbst zu **erlauben**.
- Eine **istio AuthorizationPolicy** für den **envoy** muss erstellt werden, um die **Berechtigung** zu vergeben (**allow**), mit **Pods** zu kommunizieren.

Siehe `case-01-namespace-internal`\!

-----

## Case-02: Namespace to namespace cluster internal communication

*Die **Container Plattform** ist als "**defence in depth**" aufgesetzt. Deswegen ist alles jegliche Kommunikation **by default** denied. Link zum [**Securitykonzept**](https://www.google.com/search?q=https://wiki.system.local/spaces/COPLAT/pages/330384957/URP-0001-Securitykonzept-Servicekommunikation).*

Nehmen wir an, wir haben einen Quell Namespace `source-namespace` und einen Ziel Namespace `target-namespace`.

Um einen erfoglrieche Verbindung zwischen zwei **Namespaces** innerhalb der **Container Plattform** zu etablieren:

- `target-namespace`:
  - Eine **native Kubernetes NetworkPolicy** für **ingress** muss erstellt werden, um **eigehende Verbindungen** aus dem **Quell Namespace** selbst zu **erlauben**.
  - Eine **istio AuthorizationPolicy** für den **envoy** muss erstellt werden, um die **Berechtigung** zu vergeben (**allow**), mit **Pods** zu kommunizieren.
- `source-namespace`:
  - Eine **native Kubernetes NetworkPolicy** für **ingress** muss erstellt werden, um **ausgehende Verbindungen** für den **Ziel Namespace** zu **erlauben**.

\=\> Wegen der **Kommunikation** innerhalb von **istio**, die eine **mtls connection** herstellt, musst mit anderen **services** via **http** und NICHT **https** **kommuniziertn** werden\!

Siehe `case-02-namespace-to-namespace`\!