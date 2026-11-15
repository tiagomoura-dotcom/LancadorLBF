# HTMLTi (antigo Lançador), versão 2

App pessoal que guarda e roda apps HTML feitos no chat com o Claude, organizados em áreas e subáreas.
Roda no PC (Chrome ou Edge), no Galaxy Tab S11 e no Galaxy Z Fold4, e se ajusta à tela de cada um.
Os três aparelhos compartilham uma pasta sincronizada pelo Syncthing. A própria pasta é a configuração: o que um aparelho cria aparece nos outros.

## Estrutura do repositório

- `www/index.html`: o HTMLTi inteiro (HTML, CSS e JS num arquivo só, sem etapa de build). É o mesmo código no PC e no Android.
- `www/nativo.js`: ponte nativa. No PC não faz nada; no APK, implementa `window.LancadorNativo`.
- `www/sw.js`, `www/manifest.webmanifest` e os ícones: permitem instalar no PC e abrir sem internet.
- `android/`: projeto Capacitor (appId `com.pessoal.lancador`). O plugin nativo é `ArquivosPlugin.java` (nome JS `LancadorArquivos`).
- `.github/workflows/android.yml`: APK de debug na release `ultima-versao` (`htmlti.apk`). `.github/workflows/pages.yml`: publica `www/` no GitHub Pages.

## Regras

- O nome mostrado é HTMLTi, mas os identificadores internos continuam com "lancador" (appId `com.pessoal.lancador`, pasta `Lancador`, `window.LancadorNativo`, `window.lancador`, `lancadorVoltar`, chaves `lancador.*`) para o APK novo instalar por cima do antigo e os dados continuarem valendo.
- Logo: `www/icone.svg` (triângulo com "Ti" e "HTML" embaixo, só formas, sem fonte). Os PNGs `icone-192`, `icone-512` e `icone-maskable` são gerados a partir dele; o mesmo desenho está em `I.logo` no `index.html`.
- Link do APK (não mudar): `https://github.com/tiagomoura-dotcom/LancadorLBF/releases/download/ultima-versao/htmlti.apk`.
- Não reescreva a lógica do `index.html` para o Android. A integração nativa é só `window.LancadorNativo` (em `nativo.js`) e a chamada a `window.lancadorVoltar()`.
- Mudanças de comportamento são feitas no `index.html` e valem para todos os aparelhos.
- Ao mudar qualquer arquivo de `www/`, aumente a versão do cache em `sw.js` (`lancador-v6` para `lancador-v7` e assim por diante), para o PC pegar a versão nova.
- Não edite `android/app/src/main/assets/public` (gerado por `npx cap sync android`) nem os `mipmap-*/ic_launcher*.png` (gerados por `npm run icones` a partir de `www/icone-512.png`).
- O botão "<" do app aberto e o Voltar do Android fecham o app direto (`fecharAppInterno`); não use `history.back()` para isso, porque apps que mudam o `#` criam entradas no histórico.

## Formato da pasta sincronizada

```
Lancador/
  config.json                cores das áreas
  .lancador/aparelhos/       um arquivo por aparelho: recentes e app aberto agora
  <Área>/
    <Subárea>/               opcional
      <Nome do app>/
        app.html             o app
        app.json             id, nome, símbolo, descrição, temas, favorito
        estado.json          dados do app (localStorage e window.storage), com data e aparelho de cada item
        importados/          arquivos que o usuário escolheu dentro do app
        exportados/          arquivos que o app gerou, com o nome que o usuário escolheu
        .lancador/<aparelho>.json   marcador oculto de cada arquivo (origem, data, impressão digital)
```

Uma pasta com `app.json` ou `app.html` é um app; as outras são áreas (primeiro nível) ou subáreas (segundo nível).
Cada aparelho grava só nos próprios arquivos de `.lancador`, então eles nunca entram em conflito.
O HTMLTi junta sozinho as cópias de conflito do Syncthing (`*.sync-conflict-*`) de `estado.json`, `app.json` e `config.json`; as de `app.html` e dos arquivos de `importados` e `exportados` aparecem para o usuário escolher.

Migração: uma pasta no formato 1 (`lancador.json`, `apps/`, `dados/`, `uso/` e, do HTMLTi 1.x, `arquivos/<pasta>/`) é convertida em `migrarV1()`; os arquivos de `arquivos/` vão para `exportados/` do app.

## O que o HTMLTi faz pelos HTMLs (`window.lancador`)

Os apps rodam isolados (iframe `srcdoc` com sandbox). Sem mudar nada neles:
- `localStorage` e `window.storage` são gravados no `estado.json` do app e sincronizam;
- downloads (`<a download>`, `showSaveFilePicker`) abrem um "Salvar como" e vão para `exportados`;
- `<input type="file">` e `showOpenFilePicker` mostram primeiro os arquivos da pasta do app, e o que vier do aparelho é copiado para `importados`;
- links `href="#..."` só trocam o `#` (sem o shim, o `srcdoc` carregaria o HTMLTi dentro do app) e `history.pushState` não dá erro.

Apps feitos para o HTMLTi também podem usar diretamente:

```js
if (window.lancador) {
  await lancador.salvarArquivo('resumo.csv', textoOuBlob);            // pergunta o nome; { perguntar: false } salva direto
  const lista = await lancador.listarArquivos('exportados');           // [{ caminho, nome, pasta, tamanho, modificado, criadoPor, criadoEm }]
  const blob = await lancador.lerArquivo('exportados/resumo.csv');
  const arquivos = await lancador.escolherArquivo({ accept: '.csv' }); // [File]
}
```

Fora do HTMLTi, `window.lancador` não existe; o app deve funcionar normalmente sem ele.

Interface: compartilhar o HTML do app (barra do app, menu ⋮ e formulário) e compartilhar arquivos do painel "Arquivos do app": no APK usa `LancadorNativo.compartilhar`, no PC o Web Share do navegador (sem ele, baixa). A seção "Arquivos" da biblioteca (view `arquivos`) lista os apps com a quantidade de arquivos e abre o painel de cada um.

## Contrato de `window.LancadorNativo`

Todas as funções devolvem Promise. Os caminhos são relativos à memória interna e separados por `/` (exemplo: `Lancador/Idiomas/Quiz/estado.json`). Erros têm `code`: `NAO_ENCONTRADO` (não existe), `SEM_ACESSO`, `CAMINHO_INVALIDO` (`..`, fora da memória interna, tipo errado), `ERRO_ES`.

| Função | O que faz |
|---|---|
| `pastaPadrao()` → `string` | Sugestão de pasta na primeira abertura (`"Lancador"`). |
| `temAcesso()` → `boolean` | Se o app já tem acesso a todos os arquivos. |
| `pedirAcesso()` → `boolean` | Abre a tela de permissão do sistema; resolve quando o usuário volta. |
| `listar(caminho)` → `[{ nome, tipo }]` | Conteúdo da pasta, incluindo itens que começam com ponto (esconde só os temporários `.syncthing.*.tmp`); `tipo` é `"arquivo"` ou `"pasta"`. |
| `lerTexto(caminho)` → `string` | Lê em UTF-8. |
| `escreverTexto(caminho, texto)` | Grava em UTF-8, criando as pastas. |
| `lerBase64(caminho)` → `string` | Lê um arquivo binário em Base64. |
| `escreverBase64(caminho, base64)` | Grava um arquivo binário, criando as pastas. |
| `info(caminho)` → `{ tamanho, modificado }` | Tamanho em bytes e data de modificação em ms. |
| `criarPasta(caminho)` | Cria a pasta e as que faltarem. |
| `apagar(caminho)` | Apaga um arquivo (ou uma pasta vazia). |
| `apagarPasta(caminho)` | Apaga uma pasta com tudo dentro. |
| `mover(origem, destino)` | Move ou renomeia arquivo ou pasta (`renameTo`; se falhar, copia e apaga; troca só de maiúsculas passa por nome temporário). |
| `abrirExterno(caminho, tipo)` | Abre o arquivo em outro app do Android (FileProvider + `ACTION_VIEW`). |
| `compartilhar(nome, base64, tipo, titulo)` | Extra do HTMLTi: menu de compartilhar do Android (arquivo no cache, via FileProvider). |

As duas funções de escrita gravam em `.syncthing.<nome>.<x>.tmp` e renomeiam no fim (atômico), para o Syncthing nunca enviar um arquivo pela metade.
