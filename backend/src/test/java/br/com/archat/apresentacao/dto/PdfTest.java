package br.com.archat.apresentacao.dto;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTest {

    @Test
    void geraPdfComVariasPaginasMesmoComValoresVazios() throws Exception {
        List<List<Object>> linhas = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            linhas.add(java.util.Arrays.asList("Campanha " + i, 10, new BigDecimal("2.40"), null));
        }

        byte[] pdf = Pdf.gerar(new Pdf.Relatorio("Relatório de teste", "Supermercado São Pedro",
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), LocalDateTime.parse("2026-10-28T20:00:00"),
                List.of("Prioridade: normal"), List.of(new Pdf.Indicador("Campanhas", "120")),
                List.of("Campanha", "Destinatários", "Custo (R$)", "Custo/entregue (R$)"), new float[]{4, 1, 1, 1},
                linhas, "Nada no período."));

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        PdfReader leitor = new PdfReader(pdf);
        int paginas = leitor.getNumberOfPages();
        assertThat(paginas).isGreaterThan(1);
        String primeira = new PdfTextExtractor(leitor).getTextFromPage(1).replaceAll("\\s+", " ");
        assertThat(primeira).contains("Supermercado São Pedro", "Prioridade: normal", "Página 1 de " + paginas);
    }
}
