# HTMLTi (antigo Lançador)

App pessoal que guarda e roda apps HTML feitos no chat com o Claude, organizados por áreas e temas.
Roda no PC (Chrome ou Edge), no Galaxy Tab S11 e no Galaxy Z Fold4, e se ajusta à tela de cada um.
Os três aparelhos compartilham uma pasta sincronizada pelo Syncthing.

## Estrutura do repositório

- `www/index.html`: o HTMLTi inteiro (HTML, CSS e JS num arquivo só, sem etapa de build). É o mesmo código no PC e no Android.
- `www/nativo.js`: ponte nativa. No PC não faz nada; no APK, implementa `window.LancadorNativo`.
- `www/sw.js`, `www/manifest.webmanifest` e os ícones: permitem instalar no PC e abrir sem internet.
- `android/`: projeto Capacitor (appId `com.pessoal.lancador`). O plugin nativo é `ArquivosPlugin.java` (nome JS `LancadorArquivos`).
- `.github/workflows/android.yml`: APK de debug na release `ultima-versao`. `.github/workflows/pages.yml`: publica `www/` no GitHub Pages.

## Regras

- O nome mostrado é HTMLTi, mas os identificadores internos continuam com "lancador" (appId `com.pessoal.lancador`, pasta `Lancador`, `window.LancadorNativo`, `lancadorVoltar`, chaves `lancador.*`) para o APK novo instalar por cima do antigo e os dados continuarem valendo.
- Logo: `www/icone.svg` (triângulo com "Ti" e "HTML" embaixo, só formas, sem fonte). Os PNGs `icone-192`, `icone-512` e `icone-maskable` são gerados a partir dele.
- Não reescreva a lógica do `index.html` para o Android. A integração nativa é só `window.LancadorNativo` (em `nativo.js`) e a chamada a `window.lancadorVoltar()`.
- Mudanças de comportamento do Lançador são feitas no `index.html` e valem para todos os aparelhos.
- Ao mudar qualquer arquivo de `www/`, aumente a versão do cache em `sw.js` (`lancador-v1` para `lancador-v2` e assim por diante), para o PC pegar a versão nova.
- Não edite `android/app/src/main/assets/public` (é gerado por `npx cap sync android`) nem os `mipmap-*/ic_launcher*.png` (gerados por `npm run icones` a partir de `www/icone-512.png`).

## Formato da pasta sincronizada

```
Lancador/
  lancador.json          áreas da biblioteca
  apps/<id>.html         o app
  apps/<id>.json         nome, símbolo, área, temas, favorito, modo compatível
  dados/<id>.json        o que o app salvou (localStorage e window.storage)
  uso/<aparelho>.json    abertos recentemente, um arquivo por aparelho
  arquivos/<id>/<nome>   arquivos do app: os que ele "baixou" e os guardados para abrir nele
```

Dentro de cada app, o shim (`shimDoApp`) desvia para o HTMLTi:
- links com `download` para `blob:` ou `data:` (o arquivo vai para `arquivos/<id>/`);
- `<input type="file">` (clique, `click()` ou `showPicker()`): o HTMLTi mostra os arquivos guardados do app e a opção de escolher do aparelho, e devolve os escolhidos ao input com o evento `change`.

As cópias de conflito do Syncthing (`*.sync-conflict-*`) aparecem no Lançador para o usuário escolher qual versão fica.

## Contrato de `window.LancadorNativo`

Todas as funções devolvem Promise. Os caminhos são relativos à memória interna e separados por `/`.

| Função | O que faz |
|---|---|
| `pastaPadrao()` → `string` | Sugestão de pasta na primeira abertura (`"Lancador"`). |
| `temAcesso()` → `boolean` | Se o app já tem acesso a todos os arquivos. |
| `pedirAcesso()` → `boolean` | Abre a tela de permissão do sistema; resolve quando o usuário volta. |
| `listar(caminho)` → `[{ nome, tipo }]` | Conteúdo da pasta; `tipo` é `"arquivo"` ou `"pasta"`. Esconde os temporários `.syncthing.*.tmp`. |
| `lerTexto(caminho)` → `string` | Lê em UTF-8. |
| `escreverTexto(caminho, texto)` | Grava em UTF-8, criando as pastas; escreve em `.syncthing.<nome>.<x>.tmp` e renomeia (atômico). |
| `apagar(caminho)` | Apaga um arquivo. |
| `lerBase64(caminho)` → `string` | Extra do HTMLTi: lê qualquer arquivo em base64. |
| `escreverBase64(caminho, base64)` | Extra do HTMLTi: grava bytes (mesma gravação atômica). |
| `compartilhar(nome, base64, tipo, titulo)` | Extra do HTMLTi: abre o menu de compartilhar do Android (arquivo no cache, via FileProvider). |

Erros têm `code`: `NAO_ENCONTRADO` (não existe), `SEM_ACESSO` (sem permissão), `CAMINHO_INVALIDO` (`..`, fora da memória interna, pasta no lugar de arquivo), `ERRO_ES`.
