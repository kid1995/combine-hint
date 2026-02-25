# init script

## Init Copsi in Code Repo

``` shell
git clone ssh://git@git.system.local:7999/elpa/copsi-init.git &&\
cd copsi-init &&\
chmod +x init-copsi.sh &&\
rm -rf .git &&\
./init-copsi.sh &&\
cd .. &&\
rm -rf copsi-init
```

## Init Kustomization in Deploy Repo

``` shell
git clone ssh://git@git.system.local:7999/elpa/copsi-init.git &&\
cd copsi-init &&\
chmod +x init-service.sh &&\
./init-service.sh &&\
cd .. &&\
rm -rf copsi-init
```
