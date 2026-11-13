package com.pessoal.lancador;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64;
import androidx.core.content.FileProvider;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

/**
 * Acesso à memória interna para o Lançador (implementa o lado nativo de window.LancadorNativo).
 *
 * Os caminhos vêm do JavaScript relativos à raiz da memória interna, separados por "/".
 * Nenhum caminho pode sair dessa raiz.
 */
@CapacitorPlugin(name = "LancadorArquivos")
public class ArquivosPlugin extends Plugin {

    static final String NAO_ENCONTRADO = "NAO_ENCONTRADO";
    static final String CAMINHO_INVALIDO = "CAMINHO_INVALIDO";
    static final String SEM_ACESSO = "SEM_ACESSO";
    static final String ERRO_ES = "ERRO_ES";

    /**
     * Prefixo e sufixo dos arquivos temporários de escreverTexto. É o mesmo padrão dos
     * temporários do próprio Syncthing (".syncthing.<nome>.tmp"), que ele nunca envia.
     */
    static final String TEMP_PREFIXO = ".syncthing.";
    static final String TEMP_SUFIXO = ".tmp";

    // ---- contrato ----

    @PluginMethod
    public void pastaPadrao(PluginCall call) {
        resolverValor(call, "Lancador");
    }

    @PluginMethod
    public void temAcesso(PluginCall call) {
        resolverValor(call, Environment.isExternalStorageManager());
    }

    @PluginMethod
    public void pedirAcesso(PluginCall call) {
        if (Environment.isExternalStorageManager()) {
            resolverValor(call, true);
            return;
        }
        Intent intent = new Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:" + getContext().getPackageName())
        );
        if (intent.resolveActivity(getContext().getPackageManager()) == null) {
            // Alguns aparelhos não têm a tela por app; cai na lista geral.
            intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        }
        // Resolve quando o usuário volta da tela de configurações.
        startActivityForResult(call, intent, "voltouDaPermissao");
    }

    @ActivityCallback
    private void voltouDaPermissao(PluginCall call, ActivityResult resultado) {
        if (call == null) return;
        resolverValor(call, Environment.isExternalStorageManager());
    }

    @PluginMethod
    public void listar(PluginCall call) {
        File pasta = resolver(call, true);
        if (pasta == null) return;
        if (!pasta.exists()) {
            call.reject("Pasta não encontrada: " + call.getString("caminho"), NAO_ENCONTRADO);
            return;
        }
        if (!pasta.isDirectory()) {
            call.reject("Não é uma pasta: " + call.getString("caminho"), CAMINHO_INVALIDO);
            return;
        }
        File[] itens = pasta.listFiles();
        if (itens == null) {
            call.reject("Não foi possível ler a pasta (sem acesso?): " + call.getString("caminho"), SEM_ACESSO);
            return;
        }
        Arrays.sort(itens, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        JSArray lista = new JSArray();
        for (File f : itens) {
            String nome = f.getName();
            if (ehTemporario(nome)) continue;
            JSObject item = new JSObject();
            item.put("nome", nome);
            item.put("tipo", f.isDirectory() ? "pasta" : "arquivo");
            lista.put(item);
        }
        resolverValor(call, lista);
    }

    @PluginMethod
    public void lerTexto(PluginCall call) {
        File arquivo = resolver(call, false);
        if (arquivo == null) return;
        if (!arquivo.isFile()) {
            call.reject("Arquivo não encontrado: " + call.getString("caminho"), NAO_ENCONTRADO);
            return;
        }
        try {
            byte[] bytes = lerBytes(arquivo);
            String texto = new String(bytes, StandardCharsets.UTF_8);
            // Remove o BOM, se houver.
            if (texto.startsWith("﻿")) texto = texto.substring(1);
            resolverValor(call, texto);
        } catch (IOException e) {
            rejeitarES(call, "ler", e);
        }
    }

    @PluginMethod
    public void escreverTexto(PluginCall call) {
        File destino = resolver(call, false);
        if (destino == null) return;
        String texto = call.getString("texto");
        if (texto == null) texto = "";
        gravarAtomico(call, destino, texto.getBytes(StandardCharsets.UTF_8));
    }

    @PluginMethod
    public void lerBase64(PluginCall call) {
        File arquivo = resolver(call, false);
        if (arquivo == null) return;
        if (!arquivo.isFile()) {
            call.reject("Arquivo não encontrado: " + call.getString("caminho"), NAO_ENCONTRADO);
            return;
        }
        try {
            resolverValor(call, Base64.encodeToString(lerBytes(arquivo), Base64.NO_WRAP));
        } catch (IOException e) {
            rejeitarES(call, "ler", e);
        }
    }

    @PluginMethod
    public void escreverBase64(PluginCall call) {
        File destino = resolver(call, false);
        if (destino == null) return;
        byte[] bytes;
        try {
            bytes = Base64.decode(call.getString("base64", ""), Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            call.reject("Conteúdo em base64 inválido", CAMINHO_INVALIDO);
            return;
        }
        gravarAtomico(call, destino, bytes);
    }

    /** Abre o menu de compartilhar do Android com um arquivo (gravado antes no cache do app). */
    @PluginMethod
    public void compartilhar(PluginCall call) {
        String nome = call.getString("nome", "arquivo");
        String tipo = call.getString("tipo", "application/octet-stream");
        String titulo = call.getString("titulo", nome);
        nome = nome.replaceAll("[\\\\/:*?\"<>|\\u0000-\\u001f]", "_").trim();
        if (nome.isEmpty() || nome.startsWith(".")) nome = "arquivo" + nome;
        byte[] bytes;
        try {
            bytes = Base64.decode(call.getString("base64", ""), Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            call.reject("Conteúdo em base64 inválido", CAMINHO_INVALIDO);
            return;
        }
        File pasta = new File(getContext().getCacheDir(), "compartilhar");
        File[] antigos = pasta.listFiles();
        if (antigos != null) for (File f : antigos) //noinspection ResultOfMethodCallIgnored
            f.delete();
        //noinspection ResultOfMethodCallIgnored
        pasta.mkdirs();
        File arquivo = new File(pasta, nome);
        try (FileOutputStream out = new FileOutputStream(arquivo)) {
            out.write(bytes);
        } catch (IOException e) {
            rejeitarES(call, "preparar", e);
            return;
        }
        Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", arquivo);
        Intent envio = new Intent(Intent.ACTION_SEND);
        envio.setType(tipo);
        envio.putExtra(Intent.EXTRA_STREAM, uri);
        envio.putExtra(Intent.EXTRA_TITLE, nome);
        envio.putExtra(Intent.EXTRA_SUBJECT, titulo);
        envio.setClipData(ClipData.newRawUri(nome, uri));
        envio.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent menu = Intent.createChooser(envio, "Compartilhar " + nome);
        menu.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        getActivity().runOnUiThread(() -> {
            try {
                getActivity().startActivity(menu);
                call.resolve();
            } catch (Exception e) {
                call.reject("Não foi possível abrir o menu de compartilhar: " + e.getMessage());
            }
        });
    }

    private void gravarAtomico(PluginCall call, File destino, byte[] bytes) {
        if (destino.isDirectory()) {
            call.reject("Já existe uma pasta com esse nome: " + call.getString("caminho"), CAMINHO_INVALIDO);
            return;
        }
        File pasta = destino.getParentFile();
        if (pasta != null && !pasta.isDirectory() && !pasta.mkdirs() && !pasta.isDirectory()) {
            call.reject("Não foi possível criar a pasta " + pasta.getPath(), SEM_ACESSO);
            return;
        }
        // Grava num temporário na mesma pasta e renomeia no fim: o Syncthing nunca vê
        // o arquivo final pela metade, e o temporário segue o padrão que ele ignora.
        File temp = new File(pasta, TEMP_PREFIXO + destino.getName() + "." + UUID.randomUUID().toString().substring(0, 8) + TEMP_SUFIXO);
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(bytes);
                out.flush();
                out.getFD().sync();
            }
            Files.move(temp.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            call.resolve();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            rejeitarES(call, "gravar", e);
        }
    }

    @PluginMethod
    public void apagar(PluginCall call) {
        File arquivo = resolver(call, false);
        if (arquivo == null) return;
        if (!arquivo.exists()) {
            call.reject("Arquivo não encontrado: " + call.getString("caminho"), NAO_ENCONTRADO);
            return;
        }
        if (arquivo.isDirectory()) {
            // Pasta: só apaga se estiver vazia (o HTMLTi limpa a pasta de arquivos de um app removido).
            String[] dentro = arquivo.list();
            if (dentro == null || dentro.length > 0) {
                call.reject("apagar() só apaga arquivos ou pastas vazias: " + call.getString("caminho"), CAMINHO_INVALIDO);
                return;
            }
        }
        if (!arquivo.delete()) {
            call.reject("Não foi possível apagar " + call.getString("caminho"), SEM_ACESSO);
            return;
        }
        call.resolve();
    }

    // ---- extra (fora do contrato): cor atrás da barra de status ----

    /** Recebe a cor da meta theme-color da página, por exemplo "#1e1e2e". */
    @PluginMethod
    public void corDaBarra(PluginCall call) {
        String cor = call.getString("cor");
        int valor;
        try {
            valor = Color.parseColor(cor == null ? "" : cor.trim());
        } catch (IllegalArgumentException e) {
            call.reject("Cor inválida: " + cor);
            return;
        }
        getActivity().runOnUiThread(() -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).aplicarCorDaBarra(valor);
            }
            call.resolve();
        });
    }

    // ---- utilidades ----

    private static void resolverValor(PluginCall call, Object valor) {
        JSObject r = new JSObject();
        r.put("valor", valor);
        call.resolve(r);
    }

    private static byte[] lerBytes(File arquivo) throws IOException {
        try (FileInputStream in = new FileInputStream(arquivo)) {
            byte[] bytes = new byte[(int) arquivo.length()];
            int lidos = 0;
            while (lidos < bytes.length) {
                int n = in.read(bytes, lidos, bytes.length - lidos);
                if (n < 0) break;
                lidos += n;
            }
            return lidos == bytes.length ? bytes : Arrays.copyOf(bytes, lidos);
        }
    }

    static boolean ehTemporario(String nome) {
        return nome.startsWith(TEMP_PREFIXO) && nome.endsWith(TEMP_SUFIXO);
    }

    private static File raiz() {
        return Environment.getExternalStorageDirectory();
    }

    /**
     * Converte o caminho relativo recebido em File dentro da memória interna.
     * Rejeita a chamada (e devolve null) se o caminho for inválido.
     */
    private File resolver(PluginCall call, boolean podeSerRaiz) {
        String caminho = call.getString("caminho");
        if (caminho == null) {
            call.reject("Falta o caminho", CAMINHO_INVALIDO);
            return null;
        }
        String limpo = caminho.replace('\\', '/');
        while (limpo.startsWith("/")) limpo = limpo.substring(1);
        while (limpo.endsWith("/")) limpo = limpo.substring(0, limpo.length() - 1);
        if (limpo.isEmpty() && !podeSerRaiz) {
            call.reject("Caminho vazio", CAMINHO_INVALIDO);
            return null;
        }
        for (String parte : limpo.split("/")) {
            if (parte.equals("..") || (parte.isEmpty() && !limpo.isEmpty())) {
                call.reject("Caminho inválido: " + caminho, CAMINHO_INVALIDO);
                return null;
            }
        }
        File raiz = raiz();
        File f = limpo.isEmpty() ? raiz : new File(raiz, limpo);
        try {
            String r = raiz.getCanonicalPath();
            String c = f.getCanonicalPath();
            if (!c.equals(r) && !c.startsWith(r + File.separator)) {
                call.reject("Caminho fora da memória interna: " + caminho, CAMINHO_INVALIDO);
                return null;
            }
        } catch (IOException e) {
            rejeitarES(call, "resolver", e);
            return null;
        }
        if (!Environment.isExternalStorageManager()) {
            call.reject("O Lançador ainda não tem acesso a todos os arquivos", SEM_ACESSO);
            return null;
        }
        return f;
    }

    private static void rejeitarES(PluginCall call, String acao, IOException e) {
        call.reject("Erro ao " + acao + " " + call.getString("caminho") + ": " + e.getMessage(), ERRO_ES, e);
    }
}
