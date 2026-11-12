/*
 * Ponte nativa do HTMLTi (antigo Lançador).
 *
 * No PC (Chrome/Edge) este arquivo não faz nada.
 * No APK (Capacitor) define window.LancadorNativo, que lê e grava arquivos na memória
 * interna pelo plugin LancadorArquivos (android/.../ArquivosPlugin.java), e liga o botão
 * Voltar do Android a window.lancadorVoltar().
 *
 * Contrato (todas as funções devolvem Promise; caminhos relativos à memória interna,
 * separados por "/"):
 *   pastaPadrao()                 -> string
 *   temAcesso()                   -> boolean
 *   pedirAcesso()                 -> boolean
 *   listar(caminho)               -> [{ nome, tipo: "arquivo" | "pasta" }]
 *   lerTexto(caminho)             -> string
 *   escreverTexto(caminho, texto) -> undefined
 *   apagar(caminho)               -> undefined
 * Extras do HTMLTi:
 *   lerBase64(caminho)            -> string (conteúdo em base64)
 *   escreverBase64(caminho, b64)  -> undefined (mesma gravação atômica de escreverTexto)
 *   compartilhar(nome, b64, tipo, titulo) -> abre o menu de compartilhar do Android
 * listar, lerTexto e apagar rejeitam com erro.code === "NAO_ENCONTRADO" quando o
 * arquivo ou a pasta não existe.
 */
(function () {
  'use strict';

  var cap = window.Capacitor;
  if (!(cap && typeof cap.isNativePlatform === 'function' && cap.isNativePlatform())) return;

  function plugin(nome) {
    if (cap.Plugins && cap.Plugins[nome]) return cap.Plugins[nome];
    if (typeof cap.registerPlugin === 'function') return cap.registerPlugin(nome);
    return null;
  }

  var arquivos = plugin('LancadorArquivos');
  var app = plugin('App');

  // Garante que o erro rejeitado tenha .code (o Capacitor já preenche; isto é só reforço).
  function chamar(metodo, args) {
    return arquivos[metodo](args || {}).then(
      function (r) { return r ? r.valor : undefined; },
      function (e) {
        var erro = e instanceof Error ? e : new Error((e && e.message) || String(e));
        if (e && e.code && !erro.code) erro.code = e.code;
        throw erro;
      }
    );
  }

  window.LancadorNativo = {
    pastaPadrao: function () { return chamar('pastaPadrao'); },
    temAcesso: function () { return chamar('temAcesso'); },
    pedirAcesso: function () { return chamar('pedirAcesso'); },
    listar: function (caminho) { return chamar('listar', { caminho: caminho }); },
    lerTexto: function (caminho) { return chamar('lerTexto', { caminho: caminho }); },
    escreverTexto: function (caminho, texto) {
      return chamar('escreverTexto', { caminho: caminho, texto: texto == null ? '' : String(texto) });
    },
    apagar: function (caminho) { return chamar('apagar', { caminho: caminho }); },
    // Extras do HTMLTi (arquivos binários e menu de compartilhar do Android)
    lerBase64: function (caminho) { return chamar('lerBase64', { caminho: caminho }); },
    escreverBase64: function (caminho, base64) { return chamar('escreverBase64', { caminho: caminho, base64: base64 || '' }); },
    compartilhar: function (nome, base64, tipo, titulo) {
      return chamar('compartilhar', { nome: nome, base64: base64 || '', tipo: tipo || 'application/octet-stream', titulo: titulo || nome });
    }
  };

  // Botão Voltar: o Lançador decide (fechar o app aberto, sair de um menu...).
  // Se ele devolver false, não há mais para onde voltar e o app vai para segundo plano.
  if (app) {
    app.addListener('backButton', function () {
      Promise.resolve()
        .then(function () {
          return typeof window.lancadorVoltar === 'function' ? window.lancadorVoltar() : false;
        })
        .catch(function () { return false; })
        .then(function (tratou) {
          if (tratou === false) app.minimizeApp();
        });
    });
  }

  // Extra: pinta o fundo atrás da barra de status com a cor da meta theme-color da página.
  var ultimaCor = null;
  function sincronizarCor() {
    var metas = document.querySelectorAll('meta[name="theme-color"]');
    var cor = null;
    for (var i = 0; i < metas.length; i++) {
      var media = metas[i].getAttribute('media');
      if (!media || window.matchMedia(media).matches) { cor = metas[i].getAttribute('content'); break; }
    }
    if (cor && cor !== ultimaCor) {
      ultimaCor = cor;
      arquivos.corDaBarra({ cor: cor }).catch(function () {});
    }
  }
  function observarCor() {
    sincronizarCor();
    new MutationObserver(sincronizarCor).observe(document.head || document.documentElement, {
      subtree: true, childList: true, attributes: true, attributeFilter: ['content', 'media']
    });
    if (window.matchMedia) {
      var escuro = window.matchMedia('(prefers-color-scheme: dark)');
      if (escuro.addEventListener) escuro.addEventListener('change', sincronizarCor);
    }
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', observarCor);
  else observarCor();
})();
