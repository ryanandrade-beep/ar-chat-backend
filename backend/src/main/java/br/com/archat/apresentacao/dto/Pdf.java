package br.com.archat.apresentacao.dto;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Exportação de relatórios em PDF (A4 paisagem) com o logo do A&R Chat, a empresa, o período, os filtros
 * aplicados, os indicadores e a tabela, com "Página X de Y" no rodapé.
 */
public final class Pdf {

    private static final Color MARCA = new Color(0x0f, 0x8f, 0x6b);
    private static final Color MARCA_SUAVE = new Color(0xe2, 0xf4, 0xed);
    private static final Color ZEBRA = new Color(0xf6, 0xf8, 0xf7);
    private static final Color LINHA = new Color(0xd9, 0xe1, 0xdd);
    private static final Color CINZA = new Color(0x5b, 0x67, 0x62);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");

    private Pdf() {
    }

    public record Indicador(String rotulo, String valor) {
    }

    public record Relatorio(String titulo, String empresa, LocalDate de, LocalDate ate, LocalDateTime geradoEm,
                            List<String> filtros, List<Indicador> indicadores, List<String> cabecalho,
                            float[] larguras, List<List<Object>> linhas, String vazio) {
    }

    public static ResponseEntity<byte[]> arquivo(String nomeArquivo, Relatorio r) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nomeArquivo, StandardCharsets.UTF_8).build().toString())
                .body(gerar(r));
    }

    static byte[] gerar(Relatorio r) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4.rotate(), 32, 32, 32, 40);
        PdfWriter escritor = PdfWriter.getInstance(documento, saida);
        escritor.setPageEvent(new Rodape(r.geradoEm()));
        documento.addTitle(r.titulo());
        documento.addAuthor("A&R Chat");
        documento.open();
        documento.add(cabecalho(r));
        if (!r.filtros().isEmpty()) {
            Paragraph filtros = new Paragraph("Filtros: " + String.join("  ·  ", r.filtros()), fonte(8.5f, Font.NORMAL, CINZA));
            filtros.setSpacingBefore(6);
            documento.add(filtros);
        }
        documento.add(indicadores(r.indicadores()));
        documento.add(tabela(r));
        if (r.linhas().isEmpty()) {
            Paragraph vazio = new Paragraph(r.vazio(), fonte(10, Font.ITALIC, CINZA));
            vazio.setAlignment(Element.ALIGN_CENTER);
            vazio.setSpacingBefore(14);
            documento.add(vazio);
        }
        documento.close();
        return saida.toByteArray();
    }

    private static PdfPTable cabecalho(Relatorio r) {
        PdfPTable topo = new PdfPTable(new float[]{0.6f, 5.4f, 4f});
        topo.setWidthPercentage(100);

        PdfPCell logo = new PdfPCell(logo(), true);
        logo.setBorder(Rectangle.NO_BORDER);
        logo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logo.setPaddingRight(8);
        topo.addCell(logo);

        Paragraph marca = new Paragraph();
        marca.add(new Chunk("A&R Chat\n", fonte(16, Font.BOLD, MARCA)));
        marca.add(new Chunk(r.titulo(), fonte(13, Font.BOLD, Color.BLACK)));
        PdfPCell titulo = new PdfPCell(marca);
        titulo.setBorder(Rectangle.NO_BORDER);
        titulo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        topo.addCell(titulo);

        Paragraph dados = new Paragraph();
        dados.setLeading(13);
        dados.add(new Chunk(r.empresa() + "\n", fonte(10, Font.BOLD, Color.BLACK)));
        dados.add(new Chunk("Período: %s a %s\n".formatted(r.de().format(DATA), r.ate().format(DATA)),
                fonte(9, Font.NORMAL, CINZA)));
        dados.add(new Chunk("Emitido em " + r.geradoEm().format(DATA_HORA), fonte(9, Font.NORMAL, CINZA)));
        PdfPCell direita = new PdfPCell(dados);
        direita.setBorder(Rectangle.NO_BORDER);
        direita.setHorizontalAlignment(Element.ALIGN_RIGHT);
        direita.setVerticalAlignment(Element.ALIGN_MIDDLE);
        topo.addCell(direita);

        PdfPCell faixa = new PdfPCell();
        faixa.setColspan(3);
        faixa.setBorder(Rectangle.BOTTOM);
        faixa.setBorderColorBottom(MARCA);
        faixa.setBorderWidthBottom(2);
        faixa.setFixedHeight(8);
        topo.addCell(faixa);
        return topo;
    }

    private static PdfPTable indicadores(List<Indicador> indicadores) {
        PdfPTable grade = new PdfPTable(Math.max(1, indicadores.size()));
        grade.setWidthPercentage(100);
        grade.setSpacingBefore(10);
        grade.setSpacingAfter(12);
        for (Indicador i : indicadores) {
            Paragraph conteudo = new Paragraph();
            conteudo.add(new Chunk(i.rotulo() + "\n", fonte(8.5f, Font.NORMAL, CINZA)));
            conteudo.add(new Chunk(i.valor(), fonte(14, Font.BOLD, Color.BLACK)));
            PdfPCell celula = new PdfPCell(conteudo);
            celula.setBackgroundColor(MARCA_SUAVE);
            celula.setBorderColor(Color.WHITE);
            celula.setBorderWidth(3);
            celula.setPadding(8);
            grade.addCell(celula);
        }
        return grade;
    }

    private static PdfPTable tabela(Relatorio r) {
        PdfPTable tabela = new PdfPTable(r.larguras());
        tabela.setWidthPercentage(100);
        tabela.setHeaderRows(1);
        for (String titulo : r.cabecalho()) {
            PdfPCell celula = new PdfPCell(new Phrase(titulo, fonte(8, Font.BOLD, Color.WHITE)));
            celula.setBackgroundColor(MARCA);
            celula.setBorderColor(MARCA);
            celula.setPadding(5);
            tabela.addCell(celula);
        }
        for (int i = 0; i < r.linhas().size(); i++) {
            for (Object valor : r.linhas().get(i)) {
                boolean numero = valor instanceof Number;
                PdfPCell celula = new PdfPCell(new Phrase(texto(valor), fonte(8.5f, Font.NORMAL, Color.BLACK)));
                celula.setBackgroundColor(i % 2 == 1 ? ZEBRA : Color.WHITE);
                celula.setBorderColor(LINHA);
                celula.setPadding(4.5f);
                celula.setHorizontalAlignment(numero ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                tabela.addCell(celula);
            }
        }
        return tabela;
    }

    private static String texto(Object valor) {
        if (valor == null) {
            return "—";
        }
        if (valor instanceof BigDecimal numero) {
            return String.format(BRASIL, "%,.2f", numero);
        }
        if (valor instanceof LocalDateTime dataHora) {
            return dataHora.format(DATA_HORA);
        }
        return valor.toString();
    }

    private static Image logo() {
        try (InputStream entrada = Pdf.class.getResourceAsStream("/relatorio/logo-archat.png")) {
            if (entrada == null) {
                throw new IllegalStateException("Logo do relatório não encontrado");
            }
            return Image.getInstance(entrada.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Font fonte(float tamanho, int estilo, Color cor) {
        return new Font(Font.HELVETICA, tamanho, estilo, cor);
    }

    private static final class Rodape extends PdfPageEventHelper {

        private final LocalDateTime geradoEm;
        private PdfTemplate total;
        private BaseFont base;

        private Rodape(LocalDateTime geradoEm) {
            this.geradoEm = geradoEm;
        }

        @Override
        public void onOpenDocument(PdfWriter escritor, Document documento) {
            total = escritor.getDirectContent().createTemplate(40, 12);
            try {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            PdfContentByte conteudo = escritor.getDirectContent();
            float y = documento.bottom() - 20;
            ColumnText.showTextAligned(conteudo, Element.ALIGN_LEFT,
                    new Phrase("A&R Chat · atendimento e campanhas pelo WhatsApp · emitido em " + geradoEm.format(DATA_HORA),
                            fonte(7.5f, Font.NORMAL, CINZA)), documento.left(), y, 0);
            String pagina = "Página %d de ".formatted(escritor.getPageNumber());
            float largura = base.getWidthPoint(pagina, 7.5f);
            float x = documento.right() - largura - 18;
            conteudo.beginText();
            conteudo.setFontAndSize(base, 7.5f);
            conteudo.setColorFill(CINZA);
            conteudo.setTextMatrix(x, y);
            conteudo.showText(pagina);
            conteudo.endText();
            conteudo.addTemplate(total, x + largura, y);
        }

        @Override
        public void onCloseDocument(PdfWriter escritor, Document documento) {
            total.beginText();
            total.setFontAndSize(base, 7.5f);
            total.setColorFill(CINZA);
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(escritor.getPageNumber() - 1));
            total.endText();
        }
    }
}
