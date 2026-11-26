package br.com.archat.apresentacao.dto;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exportação em Excel (.xlsx) sem dependências: o arquivo é um zip com os XMLs mínimos de uma planilha.
 * O cabeçalho vai em negrito com a primeira linha congelada, números ficam como números (dá para somar)
 * e as datas saem no formato dd/MM/yyyy HH:mm.
 */
public final class Xlsx {

    public static final MediaType TIPO = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Xlsx() {
    }

    public static ResponseEntity<byte[]> arquivo(String nomeArquivo, String aba, List<String> cabecalho,
                                                 List<List<Object>> linhas) {
        return ResponseEntity.ok()
                .contentType(TIPO)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nomeArquivo, StandardCharsets.UTF_8).build().toString())
                .body(gerar(aba, cabecalho, linhas));
    }

    public static byte[] gerar(String aba, List<String> cabecalho, List<List<Object>> linhas) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(saida, StandardCharsets.UTF_8)) {
            entrada(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">\
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>\
                    <Default Extension="xml" ContentType="application/xml"/>\
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>\
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>\
                    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>\
                    </Types>""");
            entrada(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>\
                    </Relationships>""");
            entrada(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">\
                    <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets></workbook>""".formatted(nomeDaAba(aba)));
            entrada(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>\
                    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>\
                    </Relationships>""");
            entrada(zip, "xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">\
                    <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>\
                    <fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>\
                    <fill><patternFill patternType="solid"><fgColor rgb="FFDCEFD3"/><bgColor indexed="64"/></patternFill></fill></fills>\
                    <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>\
                    <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>\
                    <cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>\
                    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs>\
                    </styleSheet>""");
            entrada(zip, "xl/worksheets/sheet1.xml", planilha(cabecalho, linhas));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return saida.toByteArray();
    }

    private static String planilha(List<String> cabecalho, List<List<Object>> linhas) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">\
                <sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>\
                <cols>""");
        for (int c = 0; c < cabecalho.size(); c++) {
            int largura = Math.min(48, Math.max(12, cabecalho.get(c).length() + 4));
            xml.append("<col min=\"%d\" max=\"%d\" width=\"%d\" customWidth=\"1\"/>".formatted(c + 1, c + 1, largura));
        }
        xml.append("</cols><sheetData>");
        xml.append("<row r=\"1\">");
        for (int c = 0; c < cabecalho.size(); c++) {
            xml.append(textoCelula(referencia(c, 1), cabecalho.get(c), 1));
        }
        xml.append("</row>");
        int numero = 2;
        for (List<Object> linha : linhas) {
            xml.append("<row r=\"").append(numero).append("\">");
            for (int c = 0; c < linha.size(); c++) {
                xml.append(celula(referencia(c, numero), linha.get(c)));
            }
            xml.append("</row>");
            numero++;
        }
        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    private static String celula(String ref, Object valor) {
        if (valor == null || valor.toString().isEmpty()) {
            return "";
        }
        if (valor instanceof BigDecimal numero) {
            return "<c r=\"%s\"><v>%s</v></c>".formatted(ref, numero.toPlainString());
        }
        if (valor instanceof Integer || valor instanceof Long || valor instanceof Short) {
            return "<c r=\"%s\"><v>%s</v></c>".formatted(ref, valor);
        }
        if (valor instanceof Double || valor instanceof Float) {
            return "<c r=\"%s\"><v>%s</v></c>".formatted(ref, BigDecimal.valueOf(((Number) valor).doubleValue()).toPlainString());
        }
        String texto = valor instanceof LocalDateTime dataHora ? dataHora.format(DATA_HORA) : valor.toString();
        return textoCelula(ref, texto, 0);
    }

    private static String textoCelula(String ref, String texto, int estilo) {
        return "<c r=\"%s\" t=\"inlineStr\" s=\"%d\"><is><t xml:space=\"preserve\">%s</t></is></c>"
                .formatted(ref, estilo, escapar(texto));
    }

    private static String referencia(int coluna, int linha) {
        StringBuilder letras = new StringBuilder();
        for (int c = coluna; c >= 0; c = c / 26 - 1) {
            letras.insert(0, (char) ('A' + c % 26));
        }
        return letras.toString() + linha;
    }

    private static String nomeDaAba(String aba) {
        String limpo = aba.replaceAll("[\\\\/?*\\[\\]:]", " ").strip();
        return escapar(limpo.isEmpty() ? "Dados" : limpo.substring(0, Math.min(31, limpo.length())));
    }

    private static String escapar(String texto) {
        StringBuilder saida = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '&' -> saida.append("&amp;");
                case '<' -> saida.append("&lt;");
                case '>' -> saida.append("&gt;");
                case '"' -> saida.append("&quot;");
                default -> {
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                        saida.append(c);
                    }
                }
            }
        }
        return saida.toString();
    }

    private static void entrada(ZipOutputStream zip, String nome, String conteudo) throws IOException {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
