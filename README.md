# Lançador

App pessoal que guarda e roda apps HTML feitos no chat com o Claude.
O mesmo `www/index.html` roda no PC (GitHub Pages, Chrome ou Edge) e no Android (APK Capacitor).

**APK mais recente:** https://github.com/tiagomoura-dotcom/LancadorLBF/releases/download/ultima-versao/lancador.apk

**PC:** https://tiagomoura-dotcom.github.io/LancadorLBF/

Cada push na `main` compila o APK de debug e substitui o arquivo da release `ultima-versao`,
e publica `www/` no GitHub Pages. Todos os APKs têm a mesma assinatura
(`android/app/debug.keystore`), então o novo instala por cima do anterior sem perder dados.

## Peças

| Onde | O quê |
|---|---|
| `www/` | O Lançador (HTML, CSS e JS sem build). Publicado no Pages e copiado para o APK. |
| `www/nativo.js` | Ponte nativa: no APK define `window.LancadorNativo` e liga o botão Voltar a `window.lancadorVoltar()`. No PC não faz nada. |
| `android/app/src/main/java/com/pessoal/lancador/ArquivosPlugin.java` | Plugin `LancadorArquivos`: lê e grava arquivos da memória interna com `java.io.File`. |
| `android/app/src/main/java/com/pessoal/lancador/MainActivity.java` | Mantém o WebView abaixo das barras do sistema, pinta a barra com a `theme-color` da página e ajusta o seletor de arquivos. |
| `.github/workflows/android.yml` | Compila o APK. |
| `.github/workflows/pages.yml` | Publica `www/` no GitHub Pages. |

## Compilar localmente (precisa do Android SDK)

```
npm ci
npm run icones                     # ícones a partir de www/icone-512.png (scripts/icones.mjs)
npx cap sync android               # copia www/ para o projeto Android
cd android && ./gradlew assembleDebug
```
