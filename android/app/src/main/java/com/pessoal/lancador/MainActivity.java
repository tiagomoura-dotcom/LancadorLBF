package com.pessoal.lancador;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BridgeActivity {

    /** Cor informada pela página (theme-color). Null = cor padrão do tema claro/escuro. */
    @Nullable
    private Integer corDaPagina = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(ArquivosPlugin.class);
        super.onCreate(savedInstanceState);

        manterAbaixoDasBarras();
        bridge.getWebView().setWebChromeClient(new SeletorDeArquivos(bridge));
        aplicarCorDaBarra(null);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Trocar entre tema claro e escuro não recria a Activity; atualiza a cor padrão.
        if (corDaPagina == null) aplicarCorDaBarra(null);
        ViewCompat.requestApplyInsets(getWindow().getDecorView());
    }

    /**
     * O Android 15+ desenha os apps de ponta a ponta. Aqui o conteúdo recebe um recuo do
     * tamanho da barra de status, da barra de navegação, do recorte da câmera e do teclado,
     * para o WebView nunca ficar por baixo deles. O espaço da barra mostra a cor do fundo.
     * (O tratamento do plugin SystemBars está desligado em capacitor.config.json.)
     */
    private void manterAbaixoDasBarras() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View conteudo = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(conteudo, (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets teclado = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(barras.left, barras.top, barras.right, Math.max(barras.bottom, teclado.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(conteudo);
    }

    /** Pinta o fundo atrás das barras e escolhe ícones claros ou escuros conforme a cor. */
    void aplicarCorDaBarra(@Nullable Integer cor) {
        if (cor != null) corDaPagina = cor;
        int valor = corDaPagina != null ? corDaPagina : ContextCompat.getColor(this, R.color.fundo);
        getWindow().getDecorView().setBackgroundColor(valor);
        boolean fundoClaro = ColorUtils.calculateLuminance(valor) > 0.5;
        WindowInsetsControllerCompat controle = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controle.setAppearanceLightStatusBars(fundoClaro);
        controle.setAppearanceLightNavigationBars(fundoClaro);
    }

    /**
     * Seletor de arquivos de <input type="file">: igual ao do Capacitor, mas se o accept
     * tiver só extensões que o Android não conhece (o Capacitor quebraria com a lista vazia),
     * abre o seletor sem filtro. Para HTML, aceita também arquivos que o provedor marca
     * como texto ou binário genérico, comuns em pastas sincronizadas.
     */
    private static class SeletorDeArquivos extends BridgeWebChromeClient {

        SeletorDeArquivos(com.getcapacitor.Bridge bridge) {
            super(bridge);
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
            return super.onShowFileChooser(webView, callback, new ParametrosAjustados(params));
        }
    }

    private static class ParametrosAjustados extends WebChromeClient.FileChooserParams {

        private final WebChromeClient.FileChooserParams original;
        private final String[] tipos;

        ParametrosAjustados(WebChromeClient.FileChooserParams original) {
            this.original = original;
            this.tipos = ajustar(original.getAcceptTypes());
        }

        private static String[] ajustar(String[] aceitos) {
            List<String> tipos = new ArrayList<>();
            boolean html = false;
            MimeTypeMap mapa = MimeTypeMap.getSingleton();
            if (aceitos != null) {
                for (String bruto : aceitos) {
                    if (bruto == null) continue;
                    for (String parte : bruto.split(",")) {
                        String t = parte.trim().toLowerCase();
                        if (t.isEmpty()) continue;
                        if (t.startsWith(".")) {
                            String m = mapa.getMimeTypeFromExtension(t.substring(1));
                            if (m == null) continue;
                            t = m;
                        }
                        if (t.equals("text/html") || t.equals("application/xhtml+xml")) html = true;
                        if (!tipos.contains(t)) tipos.add(t);
                    }
                }
            }
            if (html) {
                for (String extra : new String[] { "text/html", "application/xhtml+xml", "text/plain", "application/octet-stream" }) {
                    if (!tipos.contains(extra)) tipos.add(extra);
                }
            }
            if (tipos.isEmpty()) tipos.add("*/*");
            return tipos.toArray(new String[0]);
        }

        @Override
        public int getMode() {
            return original.getMode();
        }

        @Override
        public String[] getAcceptTypes() {
            return tipos;
        }

        @Override
        public boolean isCaptureEnabled() {
            return original.isCaptureEnabled();
        }

        @Nullable
        @Override
        public CharSequence getTitle() {
            return original.getTitle();
        }

        @Nullable
        @Override
        public String getFilenameHint() {
            return original.getFilenameHint();
        }

        @Override
        public Intent createIntent() {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(tipos.length == 1 ? tipos[0] : "*/*");
            if (tipos.length > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, tipos);
            return intent;
        }
    }
}
