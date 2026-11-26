package br.com.archat.apresentacao.dto;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Exportação de relatórios em CSV no formato que o Excel em português abre direto:
 * separador ";", vírgula decimal e UTF-8 com BOM (acentos corretos).
 */
public final class Csv {

    private static final String BOM = "﻿";

    private Csv() {
    }

    public static ResponseEntity<String> arquivo(String nomeArquivo, List<String> cabecalho, List<List<Object>> linhas) {
        String conteudo = BOM + linha(cabecalho.stream().map(Object.class::cast).toList())
                + linhas.stream().map(Csv::linha).collect(Collectors.joining());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nomeArquivo, StandardCharsets.UTF_8).build().toString())
                .body(conteudo);
    }

    private static String linha(List<Object> valores) {
        return valores.stream().map(Csv::celula).collect(Collectors.joining(";")) + "\r\n";
    }

    private static String celula(Object valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor instanceof BigDecimal numero ? numero.toPlainString().replace('.', ',')
                : valor instanceof LocalDateTime dataHora ? dataHora.toString().replace('T', ' ') : valor.toString();
        boolean precisaAspas = texto.contains(";") || texto.contains("\"") || texto.contains("\n");
        return precisaAspas ? "\"" + texto.replace("\"", "\"\"") + "\"" : texto;
    }
}
