package br.com.archat.integracao;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import br.com.archat.aplicacao.porta.EnvioDeEmail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Percorre a API de ponta a ponta: admin cadastra o cliente, cliente envia e o worker entrega. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FluxoCompletoIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EmailsEnviados emails;

    static class EmailsEnviados implements EnvioDeEmail {
        final List<String> textos = new ArrayList<>();

        @Override
        public void enviar(String para, String assunto, String texto) {
            textos.add(para + "|" + texto);
        }
    }

    @TestConfiguration
    static class ConfiguracaoDeEmail {
        @Bean
        @Primary
        EmailsEnviados emailsEnviados() {
            return new EmailsEnviados();
        }
    }

    @Test
    void criaContaEntraComEmailERecuperaASenha() throws Exception {
        String cadastro = """
                {"name": "Açougue do Zé", "email": "Ze@Acougue.com", "phone": "(44) 98888-7777",
                 "documentId": "390.533.447-05", "documentType": "CPF", "password": "picanha2026",
                 "passwordConfirmation": "picanha2026", "acceptTerms": %s}""";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(cadastro.formatted("false")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(cadastro.formatted("true")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.client.planType").value("prepaid"))
                .andExpect(jsonPath("$.client.balance").value(0));
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(cadastro.formatted("true")))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "ze@acougue.com", "password": "errada123"}""")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "ze@acougue.com", "password": "picanha2026"}""")).andExpect(status().isOk())
                .andExpect(jsonPath("$.client.name").value("Açougue do Zé"));

        mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "ninguem@exemplo.com"}""")).andExpect(status().isAccepted());
        mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "ze@acougue.com"}""")).andExpect(status().isAccepted());
        Matcher codigo = Pattern.compile("ze@acougue.com\\|.*?(\\d{6})").matcher(emails.textos.getLast());
        assertThat(codigo.find()).isTrue();

        String redefinir = """
                {"email": "ze@acougue.com", "code": "%s", "password": "costela2026", "passwordConfirmation": "costela2026"}""";
        String errado = codigo.group(1).equals("000000") ? "111111" : "000000";
        mvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(redefinir.formatted(errado)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(redefinir.formatted(codigo.group(1))))
                .andExpect(status().isNoContent());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "ze@acougue.com", "password": "costela2026"}""")).andExpect(status().isOk());
    }

    @Test
    void fluxoDeEnvioComCobrancaFilaERelatorios() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");

        String clienteId = corpo(executar(post("/clients"), admin, """
                {"name": "Empresa ABC", "documentId": "11.222.333/0001-81", "documentType": "CNPJ",
                 "planType": "prepaid", "initialAmount": 0.75}""").andExpect(status().isCreated()), "$.id");

        String cliente = token(post("/auth"), """
                {"documentId": "11222333000181", "documentType": "CNPJ"}""");

        String destinatarioId = corpo(executar(post("/recipients"), cliente, """
                {"name": "Maria Silva", "phone": "(44) 99999-0000"}""").andExpect(status().isCreated()), "$.id");

        String mensagemId = corpo(executar(post("/messages"), cliente, """
                {"recipientId": "%s", "content": "Olá, como vai?", "priority": "urgent"}""".formatted(destinatarioId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("queued"))
                .andExpect(jsonPath("$.cost").value(0.5))
                .andExpect(jsonPath("$.currentBalance").value(0.25)), "$.id");

        executar(post("/messages"), cliente, """
                {"recipientId": "%s", "content": "Outra", "priority": "urgent"}""".formatted(destinatarioId))
                .andExpect(status().isPaymentRequired());

        aguardarStatus(cliente, mensagemId, "delivered");

        executar(get("/conversations"), cliente, null)
                .andExpect(jsonPath("$[0].recipientName").value("Maria Silva"))
                .andExpect(jsonPath("$[0].lastMessageContent").value("Olá, como vai?"));

        executar(get("/clients/" + clienteId + "/balance"), cliente, null)
                .andExpect(jsonPath("$.available").value(0.25));

        String hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
        executar(get("/reports/attendances?from=%s&to=%s".formatted(hoje, hoje)), cliente, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        executar(get("/reports/attendances?from=%s&to=%s&format=csv".formatted(hoje, hoje)), cliente, null)
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")));
        executar(get("/reports/revenue?from=%s&to=%s".formatted(hoje, hoje)), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clients[?(@.name == 'Empresa ABC')].netRevenue").value(0.5));
        executar(get("/reports/revenue?from=%s&to=%s".formatted(hoje, hoje)), cliente, null)
                .andExpect(status().isForbidden());
        executar(get("/reports/campaigns?from=%s&to=%s".formatted(hoje, hoje)), cliente, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaigns").isEmpty());
        executar(get("/queue/status"), admin, null)
                .andExpect(jsonPath("$.sent").isNumber());
    }

    @Test
    void campanhaDisparaParaQuemAceitaOfertasEAcompanhaOAndamento() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients"), admin, """
                {"name": "Supermercado São Pedro", "documentId": "529.982.247-25", "documentType": "CPF",
                 "planType": "prepaid", "initialAmount": 10}""").andExpect(status().isCreated());
        String mercado = token(post("/auth"), """
                {"documentId": "52998224725", "documentType": "CPF"}""");
        for (int i = 1; i <= 3; i++) {
            executar(post("/recipients"), mercado, """
                    {"name": "Cliente %d", "phone": "4498888000%d", "acceptsMarketing": %b}""".formatted(i, i, i != 3))
                    .andExpect(status().isCreated());
        }

        String campanhaId = corpo(executar(post("/campaigns"), mercado, """
                {"name": "Ofertas da semana", "content": "Picanha R$ 49,90/kg", "priority": "normal", "allOptedIn": true}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRecipients").value(2))
                .andExpect(jsonPath("$.totalCost").value(0.5))
                .andExpect(jsonPath("$.currentBalance").value(9.5)), "$.id");

        for (int i = 0; i < 50; i++) {
            String entregues = corpo(executar(get("/campaigns/" + campanhaId), mercado, null), "$.messagesByStatus.delivered");
            if ("2".equals(entregues)) {
                String hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
                executar(get("/reports/campaigns?from=%s&to=%s".formatted(hoje, hoje)), mercado, null)
                        .andExpect(jsonPath("$.campaigns[0].delivered").value(2))
                        .andExpect(jsonPath("$.campaigns[0].deliveryRate").value(100.0))
                        .andExpect(jsonPath("$.campaigns[0].costPerDelivered").value(0.25));
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("As mensagens da campanha não foram entregues");
    }

    @Test
    void campanhaComEtiquetaAgendamentoEPausaFicaRetidaAteOHorario() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients"), admin, """
                {"name": "Padaria Central", "documentId": "123.456.789-09", "documentType": "CPF",
                 "planType": "prepaid", "initialAmount": 10}""").andExpect(status().isCreated());
        String padaria = token(post("/auth"), """
                {"documentId": "12345678909", "documentType": "CPF"}""");
        String[] tags = {"VIP", "vip", "centro"};
        for (int i = 1; i <= 3; i++) {
            executar(post("/recipients"), padaria, """
                    {"name": "Cliente %d", "phone": "4497777000%d", "acceptsMarketing": true, "tags": ["%s"]}"""
                    .formatted(i, i, tags[i - 1])).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tags[0]").value(tags[i - 1].toLowerCase()));
        }
        String amanha = java.time.Instant.now().plus(java.time.Duration.ofHours(24)).toString();

        String campanhaId = corpo(executar(post("/campaigns"), padaria, """
                {"name": "Pão quente", "content": "Pão saindo do forno", "priority": "normal", "allOptedIn": true,
                 "tag": "VIP", "scheduledAt": "%s", "intervalSeconds": 10}""".formatted(amanha))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRecipients").value(2)), "$.id");

        Thread.sleep(500);
        executar(get("/campaigns/" + campanhaId), padaria, null)
                .andExpect(jsonPath("$.tag").value("vip"))
                .andExpect(jsonPath("$.intervalSeconds").value(10))
                .andExpect(jsonPath("$.scheduledAt").exists())
                .andExpect(jsonPath("$.messagesByStatus.queued").value(2));
        executar(get("/recipients/tags"), padaria, null).andExpect(jsonPath("$[0]").value("centro"))
                .andExpect(jsonPath("$[1]").value("vip"));
    }

    @Test
    void exportaContatosEmCsvExcelEPdfComFiltrosEColunasEscolhidas() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients"), admin, """
                {"name": "Hortifruti Sol", "documentId": "987.654.321-00", "documentType": "CPF",
                 "planType": "prepaid", "initialAmount": 10}""").andExpect(status().isCreated());
        String loja = token(post("/auth"), """
                {"documentId": "98765432100", "documentType": "CPF"}""");
        executar(post("/recipients"), loja, """
                {"name": "Ana Souza", "phone": "44966660001", "acceptsMarketing": true, "tags": ["vip", "centro"]}""")
                .andExpect(status().isCreated());
        executar(post("/recipients"), loja, """
                {"name": "Bruno; Lima", "phone": "44966660002", "acceptsMarketing": false, "tags": ["centro"]}""")
                .andExpect(status().isCreated());

        executar(get("/exports"), loja, null).andExpect(jsonPath("$[2].key").value("payments"))
                .andExpect(jsonPath("$[3].key").value("contacts"))
                .andExpect(jsonPath("$[3].columns[0].key").value("name"));

        String csv = executar(get("/exports/contacts?format=csv&tag=VIP&columns=name,tags"), loja, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(csv).contains("Nome;Etiquetas").contains("Ana Souza;centro, vip")
                .doesNotContain("Bruno").doesNotContain("Telefone");

        String todos = executar(get("/exports/contacts?format=csv&accepts=no"), loja, null)
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(todos).contains("\"Bruno; Lima\"").doesNotContain("Ana Souza");

        byte[] xlsx = executar(get("/exports/contacts?format=xlsx&columns=name,phone"), loja, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(xlsx[0]).isEqualTo((byte) 'P');
        org.assertj.core.api.Assertions.assertThat(xlsx[1]).isEqualTo((byte) 'K');
        StringBuilder planilha = new StringBuilder();
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(xlsx))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (e.getName().equals("xl/worksheets/sheet1.xml")) {
                    planilha.append(new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        org.assertj.core.api.Assertions.assertThat(planilha.toString()).contains("Ana Souza").contains("44966660002")
                .contains("Bruno; Lima");

        byte[] pdf = executar(get("/exports/contacts?format=pdf"), loja, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        String hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
        executar(get("/exports/attendances?format=xlsx&from=%s&to=%s".formatted(hoje, hoje)), loja, null)
                .andExpect(status().isOk());
        executar(get("/exports/campaigns?format=csv&from=%s&to=%s&priority=urgent".formatted(hoje, hoje)), loja, null)
                .andExpect(status().isOk());
        executar(get("/exports/attendances?format=csv"), loja, null).andExpect(status().isUnprocessableContent());
        executar(get("/exports/contacts?format=doc"), loja, null).andExpect(status().isUnprocessableContent());
        executar(get("/exports/contacts?columns=nome_inventado"), loja, null).andExpect(status().isUnprocessableContent());
        executar(get("/exports/relatorio-secreto"), loja, null).andExpect(status().isUnprocessableContent());
    }

    @Test
    void historicoDoAtendimentoMostraAConversaDoTicketEExportaEmVariosFormatos() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients"), admin, """
                {"name": "Livraria Pagina", "documentId": "111.222.333-96", "documentType": "CPF",
                 "planType": "prepaid", "initialAmount": 10}""").andExpect(status().isCreated());
        String livraria = token(post("/auth"), """
                {"documentId": "11122233396", "documentType": "CPF"}""");
        String contatoId = corpo(executar(post("/recipients"), livraria, """
                {"name": "Marta Leitora", "phone": "44955550001", "acceptsMarketing": true}""")
                .andExpect(status().isCreated()), "$.id");
        String mensagemId = corpo(executar(post("/messages"), livraria, """
                {"recipientId": "%s", "content": "Olá, Marta! Seu livro chegou.", "priority": "normal"}""".formatted(contatoId))
                .andExpect(status().isCreated()), "$.id");
        aguardarStatus(livraria, mensagemId, "delivered");
        String conversaId = corpo(executar(get("/conversations"), livraria, null), "$[0].id");
        executar(post("/conversations/" + conversaId + "/simulate-reply"), livraria, """
                {"content": "Que ótimo! Passo aí hoje à tarde."}""").andExpect(status().is2xxSuccessful());
        executar(post("/conversations/" + conversaId + "/notes"), livraria, """
                {"content": "Cliente combinou retirada no balcão"}""").andExpect(status().isCreated());
        executar(post("/conversations/" + conversaId + "/finish"), livraria, null).andExpect(status().isOk());

        String hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
        String atendimentoId = corpo(executar(get("/reports/attendances?from=%s&to=%s".formatted(hoje, hoje)), livraria, null)
                .andExpect(jsonPath("$.total").value(1)), "$.attendances[0].id");

        executar(get("/attendances/" + atendimentoId), livraria, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipient").value("Marta Leitora"))
                .andExpect(jsonPath("$.status").value("finalizado"))
                .andExpect(jsonPath("$.messages.length()").value(3))
                .andExpect(jsonPath("$.messages[0].kind").value("operator"))
                .andExpect(jsonPath("$.messages[0].content").value("Olá, Marta! Seu livro chegou."))
                .andExpect(jsonPath("$.messages[1].kind").value("customer"))
                .andExpect(jsonPath("$.messages[1].author").value("Marta Leitora"))
                .andExpect(jsonPath("$.messages[2].kind").value("note"));

        String txt = executar(get("/attendances/" + atendimentoId + "/export?format=txt"), livraria, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(txt).contains("Conversa com Marta Leitora").contains("Marta Leitora: Que ótimo!")
                .contains("Nota interna");
        String csv = executar(get("/attendances/" + atendimentoId + "/export?format=csv"), livraria, null)
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(csv).contains("Data/hora;Quem;Mensagem;Status").contains("Seu livro chegou");
        byte[] pdf = executar(get("/attendances/" + atendimentoId + "/export?format=pdf"), livraria, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        executar(get("/attendances/" + atendimentoId + "/export?format=xlsx"), livraria, null).andExpect(status().isOk());
        executar(get("/attendances/" + atendimentoId + "/export?format=doc"), livraria, null).andExpect(status().isUnprocessableContent());
        executar(get("/attendances/" + java.util.UUID.randomUUID()), livraria, null).andExpect(status().isNotFound());
    }

    @Test
    void operadorAtuaEmDepartamentosDaPropriaEmpresa() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients"), admin, """
                {"name": "Casa de Carnes", "documentId": "222.333.444-05", "documentType": "CPF",
                 "planType": "postpaid", "initialAmount": 50}""").andExpect(status().isCreated());
        executar(post("/clients"), admin, """
                {"name": "Outra Loja", "documentId": "333.444.555-08", "documentType": "CPF",
                 "planType": "postpaid", "initialAmount": 50}""").andExpect(status().isCreated());
        String casa = token(post("/auth"), """
                {"documentId": "22233344405", "documentType": "CPF"}""");
        String outra = token(post("/auth"), """
                {"documentId": "33344455508", "documentType": "CPF"}""");
        String acougue = corpo(executar(post("/departments"), casa, """
                {"name": "Açougue"}""").andExpect(status().isCreated()), "$.id");
        String caixa = corpo(executar(post("/departments"), casa, """
                {"name": "Caixa"}""").andExpect(status().isCreated()), "$.id");
        String alheio = corpo(executar(post("/departments"), outra, """
                {"name": "Entregas"}""").andExpect(status().isCreated()), "$.id");

        String operadorId = corpo(executar(post("/operators"), casa, """
                {"name": "Zeca Açougueiro", "email": "zeca@casadecarnes.com", "password": "Senha1234",
                 "departmentIds": ["%s", "%s"]}""".formatted(acougue, caixa))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.departmentIds.length()").value(2)), "$.id");
        executar(get("/operators"), casa, null)
                .andExpect(jsonPath("$[?(@.name == 'Zeca Açougueiro')].departmentIds[0]").value(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.anyOf(org.hamcrest.Matchers.is(acougue), org.hamcrest.Matchers.is(caixa)))));

        executar(put("/operators/" + operadorId), casa, """
                {"name": "Zeca Açougueiro", "active": true, "departmentIds": ["%s"]}""".formatted(caixa))
                .andExpect(status().isOk()).andExpect(jsonPath("$.departmentIds.length()").value(1))
                .andExpect(jsonPath("$.departmentIds[0]").value(caixa));
        executar(put("/operators/" + operadorId), casa, """
                {"name": "Zeca Renomeado", "active": true}""")
                .andExpect(status().isOk()).andExpect(jsonPath("$.departmentIds[0]").value(caixa));
        executar(put("/operators/" + operadorId), casa, """
                {"name": "Zeca Renomeado", "active": true, "departmentIds": ["%s"]}""".formatted(alheio))
                .andExpect(status().isUnprocessableContent());
        executar(put("/operators/" + operadorId), casa, """
                {"name": "Zeca Renomeado", "active": true, "departmentIds": []}""")
                .andExpect(status().isOk()).andExpect(jsonPath("$.departmentIds.length()").value(0));
    }

    @Test
    void dashboardRelatorioEExportacaoFiltramPorDepartamento() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients"), admin, """
                {"name": "Mercadinho Bom Preco", "documentId": "444.555.666-19", "documentType": "CPF",
                 "planType": "postpaid", "initialAmount": 50}""").andExpect(status().isCreated());
        String loja = token(post("/auth"), """
                {"documentId": "44455566619", "documentType": "CPF"}""");
        String padaria = corpo(executar(post("/departments"), loja, """
                {"name": "Padaria"}""").andExpect(status().isCreated()), "$.id");
        String entregas = corpo(executar(post("/departments"), loja, """
                {"name": "Entregas"}""").andExpect(status().isCreated()), "$.id");
        String[] setores = {padaria, entregas};
        for (int i = 1; i <= 2; i++) {
            String contato = corpo(executar(post("/recipients"), loja, """
                    {"name": "Cliente %d", "phone": "4494440000%d"}""".formatted(i, i)).andExpect(status().isCreated()), "$.id");
            String msg = corpo(executar(post("/messages"), loja, """
                    {"recipientId": "%s", "content": "Oi %d", "priority": "normal"}""".formatted(contato, i))
                    .andExpect(status().isCreated()), "$.id");
            aguardarStatus(loja, msg, "delivered");
        }
        String conversas = executar(get("/conversations"), loja, null).andReturn().getResponse().getContentAsString();
        for (int i = 0; i < 2; i++) {
            String conversaId = com.jayway.jsonpath.JsonPath.read(conversas, "$[" + i + "].id");
            executar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/conversations/" + conversaId + "/department"),
                    loja, "{\"departmentId\": \"" + setores[i] + "\"}").andExpect(status().isOk());
            executar(post("/conversations/" + conversaId + "/finish"), loja, null).andExpect(status().isOk());
        }
        String hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
        String periodo = "from=%s&to=%s".formatted(hoje, hoje);

        executar(get("/reports/dashboard?" + periodo), loja, null)
                .andExpect(jsonPath("$.attendances").value(2))
                .andExpect(jsonPath("$.perDepartment.length()").value(2));
        executar(get("/reports/dashboard?%s&departmentId=%s".formatted(periodo, padaria)), loja, null)
                .andExpect(jsonPath("$.attendances").value(1))
                .andExpect(jsonPath("$.perDepartment.length()").value(1))
                .andExpect(jsonPath("$.perDepartment[0].name").value("Padaria"));
        executar(get("/reports/attendances?%s&departmentId=%s".formatted(periodo, entregas)), loja, null)
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.attendances[0].departmentId").value(entregas));
        String csv = executar(get("/exports/attendances?format=csv&%s&departmentId=%s&columns=recipient,department".formatted(periodo, entregas)),
                loja, null).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(csv).contains("Contato;Departamento").contains("Entregas").doesNotContain("Padaria");
    }

    @Test
    void encerraNoDepartamentoOuEnviaParaOGeralComObservacao() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients"), admin, """
                {"name": "Hortifruti Verde", "documentId": "555.666.777-20", "documentType": "CPF",
                 "planType": "postpaid", "initialAmount": 50}""").andExpect(status().isCreated());
        String loja = token(post("/auth"), """
                {"documentId": "55566677720", "documentType": "CPF"}""");
        String feira = corpo(executar(post("/departments"), loja, """
                {"name": "Feira"}""").andExpect(status().isCreated()), "$.id");
        String contato = corpo(executar(post("/recipients"), loja, """
                {"name": "Rita Compradora", "phone": "44933330001"}""").andExpect(status().isCreated()), "$.id");
        String msg = corpo(executar(post("/messages"), loja, """
                {"recipientId": "%s", "content": "Oi Rita", "priority": "normal"}""".formatted(contato))
                .andExpect(status().isCreated()), "$.id");
        aguardarStatus(loja, msg, "delivered");
        String conversaId = corpo(executar(get("/conversations"), loja, null), "$[0].id");
        executar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/conversations/" + conversaId + "/department"),
                loja, "{\"departmentId\": \"" + feira + "\"}").andExpect(status().isOk());

        executar(post("/conversations/" + conversaId + "/finish"), loja, """
                {"destination": "general", "note": "Cliente quer falar do financeiro"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("waiting"))
                .andExpect(jsonPath("$.departmentName").value("Geral"))
                .andExpect(jsonPath("$.operatorId").doesNotExist());
        executar(get("/conversations/" + conversaId + "/messages"), loja, null)
                .andExpect(jsonPath("$[?(@.internal == true)].content").value(org.hamcrest.Matchers.hasItem(
                        "Enviado para o geral · Observação: Cliente quer falar do financeiro")));

        executar(post("/conversations/" + conversaId + "/finish"), loja, """
                {"destination": "setor-inexistente"}""").andExpect(status().isUnprocessableContent());
        executar(post("/conversations/" + conversaId + "/finish"), loja, """
                {"destination": "close", "note": "%s"}""".formatted("x".repeat(501))).andExpect(status().isUnprocessableContent());

        executar(post("/conversations/" + conversaId + "/finish"), loja, """
                {"destination": "close", "note": "  Pedido entregue  "}""")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("finished"));

        String hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
        String atendimentoId = corpo(executar(get("/reports/attendances?from=%s&to=%s".formatted(hoje, hoje)), loja, null)
                .andExpect(jsonPath("$.attendances[0].closingNote").value("Pedido entregue")), "$.attendances[0].id");
        executar(get("/attendances/" + atendimentoId), loja, null).andExpect(jsonPath("$.closingNote").value("Pedido entregue"));
        String csv = executar(get("/exports/attendances?format=csv&%s&columns=recipient,closingNote".formatted("from=%s&to=%s".formatted(hoje, hoje))),
                loja, null).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(csv).contains("Observação do encerramento").contains("Pedido entregue");
        executar(post("/conversations/" + conversaId + "/finish"), loja, null).andExpect(status().isUnprocessableContent());
    }

    @Test
    void operadorGeraEntregaAoEncerrarEResponsavelConfirmaSaidaEEntrega() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients"), admin, """
                {"name": "Mercado Entrega Rapida", "documentId": "666.777.889-11", "documentType": "CPF",
                 "planType": "postpaid", "initialAmount": 50}""").andExpect(status().isCreated());
        String loja = token(post("/auth"), """
                {"documentId": "66677788911", "documentType": "CPF"}""");
        String contato = corpo(executar(post("/recipients"), loja, """
                {"name": "Joana Cliente", "phone": "44933330002"}""").andExpect(status().isCreated()), "$.id");
        String msg = corpo(executar(post("/messages"), loja, """
                {"recipientId": "%s", "content": "Oi Joana", "priority": "normal"}""".formatted(contato))
                .andExpect(status().isCreated()), "$.id");
        aguardarStatus(loja, msg, "delivered");
        String conversaId = corpo(executar(get("/conversations"), loja, null), "$[0].id");

        executar(post("/conversations/" + conversaId + "/finish"), loja, """
                {"destination": "close", "delivery": {"address": "", "order": "2 paes", "value": 10, "paymentMethod": "pix"}}""")
                .andExpect(status().isUnprocessableContent());
        executar(get("/conversations/" + conversaId), loja, null).andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.not("finished")));

        executar(post("/conversations/" + conversaId + "/finish"), loja, """
                {"destination": "close", "note": "Pedido combinado", "delivery": {"address": "Rua das Flores, 10",
                 "items": [{"productId": "p1", "name": "Pao frances", "quantity": 2, "unitPrice": 1.5}],
                 "paymentMethod": "pix", "note": "Tocar a campainha"}}""")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("finished"));

        String entregaId = corpo(executar(get("/deliveries?status=pending"), loja, null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].value").value(3.0))
                .andExpect(jsonPath("$[0].address").value("Rua das Flores, 10"))
                .andExpect(jsonPath("$[0].customerName").value("Joana Cliente")), "$[0].id");
        executar(get("/deliveries/summary"), loja, null).andExpect(jsonPath("$.byStatus.pending").value(1));

        executar(post("/deliveries/" + entregaId + "/dispatch"), loja, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("on_the_way"))
                .andExpect(jsonPath("$.noticeSent").value(true));
        executar(post("/deliveries/" + entregaId + "/dispatch"), loja, null).andExpect(status().isUnprocessableContent());
        executar(get("/conversations/" + conversaId + "/messages"), loja, null)
                .andExpect(jsonPath("$[*].content").value(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("saiu para entrega"))));

        executar(post("/deliveries/" + entregaId + "/deliver"), loja, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("delivered"));
        executar(post("/deliveries/" + entregaId + "/cancel"), loja, """
                {"reason": "tarde demais"}""").andExpect(status().isUnprocessableContent());
        executar(get("/deliveries?status=delivered&search=Joana"), loja, null).andExpect(jsonPath("$.length()").value(1));
        executar(get("/deliveries?status=invalido"), loja, null).andExpect(status().isUnprocessableContent());

        executar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/deliveries/settings"), loja, """
                {"notifyDeparture": false, "departureMessage": "Oi {nome}, saiu!", "notifyDelivered": true, "deliveredMessage": "Oi {nome}, chegou!",
                 "pixKey": "mercado@exemplo.com", "pixKeyType": "email", "pixName": "Mercado", "acceptCard": true}""")
                .andExpect(status().isOk()).andExpect(jsonPath("$.pixKey").value("mercado@exemplo.com"));
        executar(get("/deliveries/settings"), loja, null).andExpect(jsonPath("$.notifyDeparture").value(false));
    }

    @Test
    void empresaConfiguraWebhookDeSaidaERecebeEventosNoFormatoDoArChat() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients"), admin, """
                {"name": "Loja Com CRM", "documentId": "777.888.999-41", "documentType": "CPF",
                 "planType": "postpaid", "initialAmount": 50}""").andExpect(status().isCreated());
        String loja = token(post("/auth"), """
                {"documentId": "77788899941", "documentType": "CPF"}""");
        var put = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/integrations/webhook");

        executar(get("/integrations/webhook"), loja, null).andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.availableEvents.length()").value(5));
        executar(post("/integrations/webhook/test"), loja, null).andExpect(status().isUnprocessableContent());
        executar(put, loja, """
                {"url": "http://crm.exemplo.com/hook", "events": ["attendance.finished"]}""").andExpect(status().isUnprocessableContent());
        executar(put, loja, """
                {"url": "https://localhost/hook", "events": ["attendance.finished"]}""").andExpect(status().isUnprocessableContent());
        executar(put, loja, """
                {"url": "https://crm.exemplo.com/hook", "events": []}""").andExpect(status().isUnprocessableContent());
        executar(put, loja, """
                {"url": "https://crm.exemplo.com/hook", "events": ["evento.inventado"]}""").andExpect(status().isUnprocessableContent());

        String segredo = corpo(executar(put, loja, """
                {"url": "https://crm.exemplo.com/hook", "events": ["attendance.finished", "message.received"]}""")
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.secret").value(org.hamcrest.Matchers.startsWith("whsec_"))), "$.secret");
        executar(put, loja, """
                {"url": "https://crm.exemplo.com/hook2", "events": ["attendance.finished", "message.received"]}""")
                .andExpect(jsonPath("$.secret").value(segredo));
        String novoSegredo = corpo(executar(post("/integrations/webhook/secret"), loja, null).andExpect(status().isOk()), "$.secret");
        org.assertj.core.api.Assertions.assertThat(novoSegredo).isNotEqualTo(segredo);

        String contato = corpo(executar(post("/recipients"), loja, """
                {"name": "Paula Cliente", "phone": "44933330003"}""").andExpect(status().isCreated()), "$.id");
        String msg = corpo(executar(post("/messages"), loja, """
                {"recipientId": "%s", "content": "Oi Paula", "priority": "normal"}""".formatted(contato))
                .andExpect(status().isCreated()), "$.id");
        aguardarStatus(loja, msg, "delivered");
        String conversaId = corpo(executar(get("/conversations"), loja, null), "$[0].id");
        executar(post("/conversations/" + conversaId + "/simulate-reply"), loja, """
                {"content": "Quero fechar o pedido"}""").andExpect(status().is2xxSuccessful());
        executar(post("/conversations/" + conversaId + "/finish"), loja, """
                {"destination": "close", "note": "Pedido fechado"}""").andExpect(status().isOk());

        String envios = executar(get("/integrations/webhook/deliveries"), loja, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(envios)
                .contains("message.received").contains("attendance.finished")
                .contains("Paula Cliente").contains("Quero fechar o pedido").contains("Pedido fechado")
                .doesNotContainIgnoringCase("instanceId").doesNotContainIgnoringCase("z-api").doesNotContainIgnoringCase("zapi");

        executar(get("/integrations/webhook/deliveries"), loja, null)
                .andExpect(jsonPath("$[?(@.event == 'attendance.started')]").isEmpty());
        String envioId = corpo(executar(get("/integrations/webhook/deliveries"), loja, null), "$[0].id");
        executar(post("/integrations/webhook/deliveries/" + envioId + "/retry"), loja, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("pendente"));
        executar(post("/integrations/webhook/deliveries/" + UUID.randomUUID() + "/retry"), loja, null).andExpect(status().isNotFound());
    }

    @Test
    void suporteVeNaAuditoriaQuemFezCadaAcaoEOResponsavelNaoTemAcesso() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        String clienteId = corpo(executar(post("/clients"), admin, """
                {"name": "Padaria Auditada", "documentId": "%s", "documentType": "CPF",
                 "planType": "postpaid", "initialAmount": 50}""".formatted("888.999.111-93")).andExpect(status().isCreated()), "$.id");
        String loja = token(post("/auth"), """
                {"documentId": "88899911193", "documentType": "CPF"}""");

        executar(post("/operators"), loja, """
                {"name": "Joana Atendente", "email": "joana@padaria.com", "password": "Senha@12345"}""").andExpect(status().isCreated());
        String contato = corpo(executar(post("/recipients"), loja, """
                {"name": "Marcos Cliente", "phone": "44933330004"}""").andExpect(status().isCreated()), "$.id");
        String msg = corpo(executar(post("/messages"), loja, """
                {"recipientId": "%s", "content": "Oi Marcos", "priority": "normal"}""".formatted(contato)).andExpect(status().isCreated()), "$.id");
        aguardarStatus(loja, msg, "delivered");
        String conversaId = corpo(executar(get("/conversations"), loja, null), "$[0].id");
        executar(post("/conversations/" + conversaId + "/finish"), loja, """
                {"destination": "close", "delivery": {"address": "Rua das Flores, 10", "order": "2 paes", "value": 10, "paymentMethod": "pix"}}""")
                .andExpect(status().isOk());
        String entregaId = corpo(executar(get("/deliveries"), loja, null), "$[0].id");
        executar(post("/deliveries/" + entregaId + "/dispatch"), loja, null).andExpect(status().isOk());
        executar(post("/conversations/" + conversaId + "/finish"), loja, null).andExpect(status().isUnprocessableContent());

        String json = executar(get("/admin/audit?clientId=" + clienteId), admin, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(json)
                .contains("operator.created").contains("Operador Joana Atendente")
                .contains("attendance.finished").contains("Atendimento de Marcos Cliente")
                .contains("delivery.created").contains("Entrega de Marcos Cliente")
                .contains("delivery.dispatched")
                .contains("\"clientName\":\"Padaria Auditada\"");
        org.assertj.core.api.Assertions.assertThat(json.split("attendance.finished", -1).length - 1)
                .as("a tentativa recusada (422) não pode ser auditada").isEqualTo(1);

        executar(get("/admin/audit?clientId=" + clienteId + "&action=delivery.dispatched"), admin, null)
                .andExpect(jsonPath("$.length()").value(1));
        executar(get("/admin/audit?clientId=" + clienteId + "&search=joana"), admin, null)
                .andExpect(jsonPath("$.length()").value(1));
        executar(get("/admin/audit/actions"), admin, null).andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(10)));
        executar(get("/admin/audit"), loja, null).andExpect(status().isForbidden());
        executar(get("/admin/audit"), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void empresaVeEExportaOHistoricoDePagamentos() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        String clienteId = corpo(executar(post("/clients"), admin, """
                {"name": "Mercado Pagador", "documentId": "999.111.222-71", "documentType": "CPF",
                 "planType": "prepaid", "initialAmount": 0}""").andExpect(status().isCreated()), "$.id");
        String loja = token(post("/auth"), """
                {"documentId": "99911122271", "documentType": "CPF"}""");
        executar(post("/clients/" + clienteId + "/credits"), admin, """
                {"amount": 100}""").andExpect(status().isOk());
        executar(post("/clients/" + clienteId + "/credits"), admin, """
                {"amount": 50.5}""").andExpect(status().isOk());
        String contato = corpo(executar(post("/recipients"), loja, """
                {"name": "Consumo Teste", "phone": "44933330005"}""").andExpect(status().isCreated()), "$.id");
        executar(post("/messages"), loja, """
                {"recipientId": "%s", "content": "Gera um debito", "priority": "normal"}""".formatted(contato)).andExpect(status().isCreated());

        executar(get("/clients/me/payments"), loja, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.type != 'credit')]").isEmpty());
        executar(get("/clients/me/payments?type=monthly_fee"), loja, null).andExpect(jsonPath("$.length()").value(0));
        executar(get("/clients/me/payments?type=outro"), loja, null).andExpect(status().isUnprocessableContent());

        String hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
        String csv = executar(get("/exports/payments?format=csv&from=%s&to=%s".formatted(hoje, hoje)), loja, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(csv).contains("Data;Tipo;Valor (R$);Saldo após (R$);Código da operação")
                .contains("Recarga de créditos").doesNotContain("Gera um debito");
        org.assertj.core.api.Assertions.assertThat(csv.lines().count()).as("cabeçalho + 2 pagamentos").isEqualTo(3);
        executar(get("/exports/payments?format=xlsx&from=%s&to=%s".formatted(hoje, hoje)), loja, null).andExpect(status().isOk());
        executar(get("/exports/payments?format=pdf&from=%s&to=%s".formatted(hoje, hoje)), loja, null).andExpect(status().isOk());
        executar(get("/exports/payments?format=csv"), loja, null).andExpect(status().isUnprocessableContent());
    }

    @Test
    void webhookDaZApiNaoEhBarradoPeloCorsMesmoComCabecalhoOrigin() throws Exception {
        String corpo = """
                {"type": "ReceivedCallback", "fromMe": true, "instanceId": "X", "phone": "5544900000000", "messageId": "CORS-1"}""";
        mvc.perform(post("/webhooks/zapi/segredo-de-teste/received").contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "https://api.z-api.io").content(corpo))
                .andExpect(status().isOk());
        mvc.perform(post("/webhooks/zapi/segredo-de-teste/received").contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "https://api.z-api.io").content(corpo.replace("true", "false")))
                .andExpect(status().isOk());
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).header("Origin", "https://site-desconhecido.com")
                        .content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void departamentoGeralEhPadraoNaoPodeSerAlteradoEAceitaOperadores() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients"), admin, """
                {"name": "Emporio Central", "documentId": "666.777.888-30", "documentType": "CPF",
                 "planType": "postpaid", "initialAmount": 50}""").andExpect(status().isCreated());
        String loja = token(post("/auth"), """
                {"documentId": "66677788830", "documentType": "CPF"}""");

        String geralId = corpo(executar(get("/departments"), loja, null)
                .andExpect(jsonPath("$[0].name").value("Geral"))
                .andExpect(jsonPath("$[0].general").value(true))
                .andExpect(jsonPath("$.length()").value(1)), "$[0].id");
        executar(put("/departments/" + geralId), loja, """
                {"name": "Outro nome", "active": true}""").andExpect(status().isUnprocessableContent());
        executar(put("/departments/" + geralId), loja, """
                {"name": "Geral", "active": false}""").andExpect(status().isUnprocessableContent());
        executar(post("/departments"), loja, """
                {"name": "geral"}""").andExpect(status().isUnprocessableContent());
        executar(post("/departments"), loja, """
                {"name": "Vendas"}""").andExpect(status().isCreated());
        executar(get("/departments"), loja, null).andExpect(jsonPath("$[0].name").value("Geral"))
                .andExpect(jsonPath("$[1].name").value("Vendas"));

        String operadorId = corpo(executar(post("/operators"), loja, """
                {"name": "Dani Atendente", "email": "dani@emporio.com", "password": "Senha1234"}""")
                .andExpect(status().isCreated()), "$.id");
        executar(put("/departments/" + geralId + "/operators"), loja, "{\"operatorIds\": [\"" + operadorId + "\"]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.operatorIds[0]").value(operadorId))
                .andExpect(jsonPath("$.general").value(true));
        executar(get("/departments"), loja, null).andExpect(jsonPath("$[0].operatorIds[0]").value(operadorId));
        executar(get("/operators"), loja, null)
                .andExpect(jsonPath("$[?(@.name == 'Dani Atendente')].departmentIds[0]").value(org.hamcrest.Matchers.hasItem(geralId)));
        executar(put("/departments/" + geralId + "/operators"), loja, "{\"operatorIds\": [\"" + java.util.UUID.randomUUID() + "\"]}")
                .andExpect(status().isUnprocessableContent());

        String contato = corpo(executar(post("/recipients"), loja, """
                {"name": "Lia Sem Setor", "phone": "44922220001"}""").andExpect(status().isCreated()), "$.id");
        String msg = corpo(executar(post("/messages"), loja, "{\"recipientId\": \"" + contato
                + "\", \"content\": \"Oi Lia\", \"priority\": \"normal\"}").andExpect(status().isCreated()), "$.id");
        aguardarStatus(loja, msg, "delivered");
        executar(get("/conversations"), loja, null).andExpect(jsonPath("$[0].departmentId").value(geralId))
                .andExpect(jsonPath("$[0].departmentName").value("Geral"));
    }

    @Test
    void enviaMensagemParaGrupoDoWhatsApp() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients"), admin, """
                {"name": "Loja do Grupo", "documentId": "111.444.777-35", "documentType": "CPF",
                 "planType": "postpaid", "initialAmount": 5}""").andExpect(status().isCreated());
        String loja = token(post("/auth"), """
                {"documentId": "11144477735", "documentType": "CPF"}""");

        String grupoId = corpo(executar(post("/recipients"), loja, """
                {"type": "group", "name": "Clientes VIP", "phone": "120363019502650977-group"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("group"))
                .andExpect(jsonPath("$.phone").value("120363019502650977-group")), "$.id");

        executar(post("/recipients"), loja, """
                {"type": "group", "name": "Inválido", "phone": "grupo-vip"}""")
                .andExpect(status().isUnprocessableContent());

        executar(post("/messages"), loja, """
                {"recipientId": "%s", "content": "Promoção para o grupo", "priority": "normal"}""".formatted(grupoId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cost").value(0.25));
    }

    @Test
    void atendimentoCompletoComWebhooksDaZApi() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients"), admin, """
                {"name": "Padaria", "documentId": "935.411.347-80", "documentType": "CPF",
                 "planType": "prepaid", "initialAmount": 5}""").andExpect(status().isCreated());
        String padaria = token(post("/auth"), """
                {"documentId": "93541134780", "documentType": "CPF"}""");
        executar(post("/recipients"), padaria, """
                {"name": "Joana", "phone": "5544977776666"}""").andExpect(status().isCreated());

        mvc.perform(post("/webhooks/zapi/segredo-de-teste/received").contentType(MediaType.APPLICATION_JSON).content("""
                {"isStatusReply": false, "senderLid": "81896604192873@lid", "connectedPhone": "554499999999",
                 "waitingMessage": false, "isEdit": false, "isGroup": false, "isNewsletter": false,
                 "instanceId": "A20DA9C0183A2D35A260F53F5D2B9244", "messageId": "MSG-RECEBIDA-1",
                 "phone": "5544977776666", "fromMe": false, "momment": 1632228638000, "status": "RECEIVED",
                 "chatName": "name", "senderPhoto": "https://", "senderName": "Joana", "participantPhone": null,
                 "participantLid": null, "photo": "https://", "broadcast": false, "type": "ReceivedCallback",
                 "text": {"message": "Tem pão de queijo hoje?"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true));

        executar(get("/conversations/summary"), padaria, null).andExpect(jsonPath("$.waiting").value(1));
        String conversaId = corpo(executar(get("/conversations?status=waiting"), padaria, null)
                .andExpect(jsonPath("$[0].unreadCount").value(1))
                .andExpect(jsonPath("$[0].lastMessageContent").value("Tem pão de queijo hoje?")), "$[0].id");

        String respostaId = corpo(executar(post("/messages"), padaria, """
                {"conversationId": "%s", "content": "Tem sim, saindo do forno!", "priority": "normal"}""".formatted(conversaId))
                .andExpect(status().isCreated()), "$.id");
        executar(get("/conversations/" + conversaId), padaria, null).andExpect(jsonPath("$.status").value("in_progress"));

        executar(post("/conversations/" + conversaId + "/hold"), padaria, null).andExpect(jsonPath("$.status").value("on_hold"));
        executar(post("/conversations/" + conversaId + "/resume"), padaria, null).andExpect(jsonPath("$.status").value("in_progress"));
        executar(post("/conversations/" + conversaId + "/read"), padaria, null).andExpect(jsonPath("$.unreadCount").value(0));

        String idExterno = null;
        for (int i = 0; i < 50 && idExterno == null; i++) {
            Thread.sleep(100);
            idExterno = mensagemEnviada(respostaId);
        }
        assertThat(idExterno).isNotNull();
        mvc.perform(post("/webhooks/zapi/segredo-de-teste/status").contentType(MediaType.APPLICATION_JSON).content("""
                {"instanceId": "instance.id", "status": "READ", "ids": ["%s"], "momment": 1632234645000,
                 "phoneDevice": 0, "phone": "5544977776666", "type": "MessageStatusCallback", "isGroup": false}"""
                .formatted(idExterno)))
                .andExpect(status().isOk());
        executar(get("/messages/" + respostaId + "/status"), padaria, null).andExpect(jsonPath("$.status").value("read"));

        executar(post("/conversations/" + conversaId + "/finish"), padaria, null).andExpect(jsonPath("$.status").value("finished"));
        String hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
        executar(get("/reports/attendances?from=%s&to=%s".formatted(hoje, hoje)), padaria, null)
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.attendances[0].recipient").value("Joana"))
                .andExpect(jsonPath("$.attendances[0].startedBy").value("customer"));

        mvc.perform(post("/webhooks/zapi/segredo-errado/received").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void enviaERecebeMidiaPeloAtendimento() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients"), admin, """
                {"name": "Hortifruti", "documentId": "22333444000181", "documentType": "CNPJ",
                 "planType": "prepaid", "initialAmount": 5}""").andExpect(status().isCreated());
        String horti = token(post("/auth"), """
                {"documentId": "22333444000181", "documentType": "CNPJ"}""");
        executar(post("/recipients"), horti, """
                {"name": "Carlos", "phone": "5544966665555", "acceptsMarketing": true}""").andExpect(status().isCreated());

        mvc.perform(post("/webhooks/zapi/segredo-de-teste/received").contentType(MediaType.APPLICATION_JSON).content("""
                {"isStatusReply": false, "senderLid": "81896604192873@lid", "connectedPhone": "554499999999",
                 "waitingMessage": false, "isEdit": false, "isGroup": false, "isNewsletter": false,
                 "instanceId": "A20DA9C0183A2D35A260F53F5D2B9244", "messageId": "MSG-IMAGEM-1",
                 "phone": "5544966665555", "fromMe": false, "momment": 1632228828000, "status": "RECEIVED",
                 "chatName": "name", "senderPhoto": "https://", "senderName": "name", "participantPhone": null,
                 "participantLid": null, "photo": "https://", "broadcast": false, "type": "ReceivedCallback",
                 "image": {"mimeType": "image/jpeg", "imageUrl": "https://", "thumbnailUrl": "https://",
                           "downloadError": null, "caption": "", "width": 600, "height": 315, "viewOnce": true}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true));
        String conversaId = corpo(executar(get("/conversations?status=waiting"), horti, null)
                .andExpect(jsonPath("$[0].lastMessageContent").value("[Imagem]")), "$[0].id");

        String documentoId = corpo(executar(post("/messages"), horti, """
                {"conversationId": "%s", "content": "Segue o encarte", "priority": "normal",
                 "media": {"type": "document", "url": "https://hortifruti.com/encarte", "fileName": "encarte.pdf"}}"""
                .formatted(conversaId)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.cost").value(0.25)), "$.id");
        executar(get("/messages/" + documentoId), horti, null)
                .andExpect(jsonPath("$.media.type").value("document"))
                .andExpect(jsonPath("$.media.fileName").value("encarte.pdf"))
                .andExpect(jsonPath("$.content").value("Segue o encarte"));

        executar(post("/messages"), horti, """
                {"conversationId": "%s", "content": "ouça", "priority": "normal",
                 "media": {"type": "audio", "url": "https://hortifruti.com/aviso.ogg"}}""".formatted(conversaId))
                .andExpect(status().isUnprocessableEntity());
        executar(post("/messages"), horti, """
                {"conversationId": "%s", "priority": "normal", "media": {"type": "sticker", "url": "https://x.com/a"}}"""
                .formatted(conversaId)).andExpect(status().is4xxClientError());

        String url = corpo(mvc.perform(multipart("/media").file(new org.springframework.mock.web.MockMultipartFile(
                        "file", "tabela.pdf", "application/pdf", "%PDF-1.4 teste".getBytes()))
                        .header("Authorization", "Bearer " + horti))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("document")), "$.url");
        executar(post("/messages"), horti, """
                {"conversationId": "%s", "priority": "normal", "media": {"type": "document", "url": "%s", "fileName": "tabela.pdf"}}"""
                .formatted(conversaId, url)).andExpect(status().isCreated());
        mvc.perform(get(url).header("Authorization", "Bearer " + horti))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes("%PDF-1.4 teste".getBytes()));

        String contatoId = corpo(executar(post("/messages"), horti, """
                {"conversationId": "%s", "priority": "normal", "contact": {"name": "Fornecedor de Banana", "phone": "(44) 98877-6655"}}"""
                .formatted(conversaId)).andExpect(status().isCreated()), "$.id");
        executar(get("/messages/" + contatoId), horti, null)
                .andExpect(jsonPath("$.contact.name").value("Fornecedor de Banana"))
                .andExpect(jsonPath("$.contact.phone").value("44988776655"));
        mvc.perform(post("/webhooks/zapi/segredo-de-teste/received").contentType(MediaType.APPLICATION_JSON).content("""
                {"messageId": "MSG-CONTATO-1", "phone": "5544966665555", "fromMe": false, "momment": 1632228900000,
                 "type": "ReceivedCallback", "isGroup": false,
                 "contact": {"displayName": "Cesar Baleco", "vCard": "BEGIN:VCARD", "phones": ["5544999999999"]}}"""))
                .andExpect(jsonPath("$.registered").value(true));
        executar(get("/conversations/" + conversaId), horti, null)
                .andExpect(jsonPath("$.lastMessageContent").value("[Contato] Cesar Baleco"));

        executar(post("/campaigns"), horti, """
                {"name": "Encarte da semana", "priority": "normal", "allOptedIn": true,
                 "media": {"type": "image", "url": "https://hortifruti.com/encarte.jpg"}}""")
                .andExpect(status().isCreated());
        executar(get("/campaigns"), horti, null).andExpect(jsonPath("$[0].media.type").value("image"));
    }

    @Test
    void donoCadastraOperadoresQueAtendemComLoginProprio() throws Exception {
        String dono = corpo(mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Mercearia Boa Vista", "email": "dono@boavista.com", "phone": "44977776666",
                 "documentId": "86288366757", "documentType": "CPF", "password": "boavista2026",
                 "passwordConfirmation": "boavista2026", "acceptTerms": true}"""))
                .andExpect(jsonPath("$.role").value("owner")), "$.token");
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        String clienteId = corpo(executar(get("/clients/me"), dono, null), "$.id");
        executar(post("/clients/" + clienteId + "/credits"), admin, """
                {"amount": 10}""").andExpect(status().isOk());

        String carla = corpo(executar(post("/operators"), dono, """
                {"name": "Carla", "email": "carla@boavista.com", "password": "atende2026"}""")
                .andExpect(status().isCreated()), "$.id");
        executar(post("/operators"), dono, """
                {"name": "Outra", "email": "dono@boavista.com", "password": "atende2026"}""")
                .andExpect(status().isUnprocessableEntity());

        String operadora = corpo(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "carla@boavista.com", "password": "atende2026"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("operator"))
                .andExpect(jsonPath("$.userName").value("Carla")), "$.token");

        executar(post("/operators"), operadora, """
                {"name": "X", "email": "x@boavista.com", "password": "atende2026"}""").andExpect(status().isForbidden());
        executar(get("/campaigns"), operadora, null).andExpect(status().isForbidden());
        executar(get("/clients/me/transactions"), operadora, null).andExpect(status().isForbidden());

        String contato = corpo(executar(post("/recipients"), operadora, """
                {"name": "Dona Lúcia", "phone": "5544955554444"}""").andExpect(status().isCreated()), "$.id");
        String conversa = corpo(executar(post("/messages"), operadora, """
                {"recipientId": "%s", "content": "Seu pedido saiu!", "priority": "normal"}""".formatted(contato))
                .andExpect(status().isCreated()), "$.conversationId");
        executar(get("/conversations/" + conversa), dono, null)
                .andExpect(jsonPath("$.operatorId").value(carla))
                .andExpect(jsonPath("$.operatorName").value("Carla"));
        executar(get("/conversations?assigned=me"), operadora, null).andExpect(jsonPath("$.length()").value(1));

        executar(post("/conversations/" + conversa + "/assign"), dono, """
                {"operatorId": null}""").andExpect(jsonPath("$.operatorId").doesNotExist());
        executar(get("/conversations?assigned=none"), dono, null).andExpect(jsonPath("$[0].id").value(conversa));

        executar(put("/operators/" + carla), dono, """
                {"name": "Carla", "active": false}""").andExpect(jsonPath("$.active").value(false));
        executar(get("/conversations"), operadora, null).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "carla@boavista.com", "password": "atende2026"}""")).andExpect(status().isForbidden());
    }

    @Test
    void planoDefinePrecoDaMensagemMensalidadeELimiteDeOperadores() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        String plano = corpo(executar(post("/plans"), admin, """
                {"name": "Essencial", "monthlyFee": 49.90, "includedOperators": 1, "normalPrice": 0.20,
                 "urgentPrice": 0.40}""").andExpect(status().isCreated()), "$.id");
        executar(post("/plans"), admin, """
                {"name": "essencial", "monthlyFee": 10, "includedOperators": 1, "normalPrice": 0.20,
                 "urgentPrice": 0.40}""").andExpect(status().isUnprocessableEntity());

        String dono = corpo(mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Açaí da Praça", "email": "dono@acai.com", "phone": "44966665555",
                 "documentId": "15350946056", "documentType": "CPF", "password": "acai20260",
                 "passwordConfirmation": "acai20260", "acceptTerms": true}""")), "$.token");
        String clienteId = corpo(executar(get("/clients/me"), dono, null), "$.id");

        executar(put("/clients/" + clienteId + "/subscription"), admin, """
                {"planId": "%s"}""".formatted(plano)).andExpect(status().isUnprocessableEntity());
        executar(post("/clients/" + clienteId + "/credits"), admin, """
                {"amount": 60}""").andExpect(status().isOk());
        executar(put("/clients/" + clienteId + "/subscription"), admin, """
                {"planId": "%s"}""".formatted(plano))
                .andExpect(jsonPath("$.plan.name").value("Essencial"))
                .andExpect(jsonPath("$.status").value("active"));
        executar(get("/clients/me"), dono, null).andExpect(jsonPath("$.balance").value(10.10));
        executar(get("/clients/me/transactions"), dono, null).andExpect(jsonPath("$[0].type").value("monthly_fee"));

        String contato = corpo(executar(post("/recipients"), dono, """
                {"name": "Bia", "phone": "5544944443333"}"""), "$.id");
        executar(post("/messages"), dono, """
                {"recipientId": "%s", "content": "Açaí pronto!", "priority": "urgent"}""".formatted(contato))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cost").value(0.40));
        executar(get("/conversations?assigned=me"), dono, null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].operatorName").value("Açaí da Praça"));

        executar(post("/operators"), dono, """
                {"name": "Léo", "email": "leo@acai.com", "password": "atende2026"}""").andExpect(status().isCreated());
        executar(post("/operators"), dono, """
                {"name": "Lia", "email": "lia@acai.com", "password": "atende2026"}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1 operador")));
        executar(get("/clients/me/subscription"), dono, null).andExpect(jsonPath("$.activeOperators").value(1));
    }

    @Test
    void uraEnviaOMenuEEncaminhaParaODepartamentoEscolhido() throws Exception {
        String dono = corpo(mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Mercadinho Sol", "email": "dono@sol.com", "phone": "44955556666",
                 "documentId": "28625587887", "documentType": "CPF", "password": "mercado2026",
                 "passwordConfirmation": "mercado2026", "acceptTerms": true}""")), "$.token");
        executar(put("/ivr"), dono, """
                {"active": true, "welcomeMessage": "Olá {nome}!", "questionMessage": "Com quem deseja falar?",
                 "maxAttempts": 2, "ignoreGroups": true}""").andExpect(status().isUnprocessableEntity());
        executar(post("/departments"), dono, """
                {"name": "Entregas"}""").andExpect(status().isCreated());
        String acougue = corpo(executar(post("/departments"), dono, """
                {"name": "Açougue"}""").andExpect(status().isCreated()), "$.id");
        executar(put("/ivr"), dono, """
                {"active": true, "welcomeMessage": "Olá {nome}!", "questionMessage": "Com quem deseja falar?",
                 "maxAttempts": 2, "defaultDepartmentId": "%s", "ignoreGroups": true}""".formatted(acougue))
                .andExpect(jsonPath("$.active").value(true));
        executar(post("/recipients"), dono, """
                {"name": "Seu Zé", "phone": "5544933332222"}""").andExpect(status().isCreated());

        receber("5544933332222", "URA-1", "Boa tarde");
        String conversa = corpo(executar(get("/conversations?status=waiting"), dono, null)
                .andExpect(jsonPath("$[0].waitingIvr").value(true)), "$[0].id");
        executar(get("/conversations/" + conversa + "/messages"), dono, null)
                .andExpect(jsonPath("$[1].automatic").value(true))
                .andExpect(jsonPath("$[1].content").value("Olá Seu Zé!"))
                .andExpect(jsonPath("$[2].content").value("Com quem deseja falar?\n1 - Açougue\n2 - Entregas"))
                .andExpect(jsonPath("$[2].cost").value(0));

        receber("5544933332222", "URA-2", "2");
        executar(get("/conversations/" + conversa), dono, null)
                .andExpect(jsonPath("$.departmentName").value("Entregas"))
                .andExpect(jsonPath("$.waitingIvr").value(false));

        executar(put("/conversations/" + conversa + "/department"), dono, """
                {"departmentId": "%s"}""".formatted(acougue)).andExpect(jsonPath("$.departmentName").value("Açougue"));
        executar(get("/conversations?department=" + acougue), dono, null).andExpect(jsonPath("$[0].id").value(conversa));

        String hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
        executar(get("/reports/dashboard?from=%s&to=%s".formatted(hoje, hoje)), dono, null)
                .andExpect(jsonPath("$.attendances").value(1))
                .andExpect(jsonPath("$.openNow").value(1))
                .andExpect(jsonPath("$.waitingNow").value(1))
                .andExpect(jsonPath("$.topContacts[0].name").value("Seu Zé"))
                .andExpect(jsonPath("$.perOperator[0].name").value("Sem responsável"));
    }

    private void receber(String telefone, String id, String texto) throws Exception {
        mvc.perform(post("/webhooks/zapi/segredo-de-teste/received").contentType(MediaType.APPLICATION_JSON).content("""
                {"messageId": "%s", "phone": "%s", "fromMe": false, "momment": %d, "type": "ReceivedCallback",
                 "isGroup": false, "senderName": "Zé", "text": {"message": "%s"}}"""
                .formatted(id, telefone, System.currentTimeMillis(), texto)))
                .andExpect(jsonPath("$.registered").value(true));
    }

    @Test
    void operadoresParticipamTrocamNotasETransferem() throws Exception {
        String dono = corpo(mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Pet Shop Amigo", "email": "dono@amigo.com", "phone": "44911112222",
                 "documentId": "73514628017", "documentType": "CPF", "password": "petshop2026",
                 "passwordConfirmation": "petshop2026", "acceptTerms": true}""")), "$.token");
        String clienteId = corpo(executar(get("/clients/me"), dono, null), "$.id");
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients/" + clienteId + "/credits"), admin, """
                {"amount": 10}""").andExpect(status().isOk());
        executar(post("/operators"), dono, """
                {"name": "Rita", "email": "rita@amigo.com", "password": "atende2026"}""").andExpect(status().isCreated());
        String paulo = corpo(executar(post("/operators"), dono, """
                {"name": "Paulo", "email": "paulo@amigo.com", "password": "atende2026"}"""), "$.id");
        String banho = corpo(executar(post("/departments"), dono, """
                {"name": "Banho e Tosa"}"""), "$.id");
        String rita = token(post("/auth/login"), """
                {"email": "rita@amigo.com", "password": "atende2026"}""");
        String pauloToken = token(post("/auth/login"), """
                {"email": "paulo@amigo.com", "password": "atende2026"}""");

        String contato = corpo(executar(post("/recipients"), rita, """
                {"name": "Dona Célia", "phone": "5544977778888"}"""), "$.id");
        String conversa = corpo(executar(post("/messages"), rita, """
                {"recipientId": "%s", "content": "O Thor já está pronto!", "priority": "normal"}""".formatted(contato))
                .andExpect(status().isCreated()), "$.conversationId");

        String resposta = """
                {"conversationId": "%s", "content": "Pode buscar até 18h", "priority": "normal"}""".formatted(conversa);
        executar(post("/messages"), pauloToken, resposta).andExpect(status().isUnprocessableEntity());
        executar(post("/conversations/" + conversa + "/join"), pauloToken, null)
                .andExpect(jsonPath("$.participants[0].name").value("Paulo"));
        executar(post("/messages"), pauloToken, resposta).andExpect(status().isCreated());
        executar(get("/conversations?assigned=me"), pauloToken, null).andExpect(jsonPath("$.length()").value(1));

        executar(post("/conversations/" + conversa + "/notes"), rita, """
                {"content": "Paulo, confirma o horário com a tosa?"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internal").value(true))
                .andExpect(jsonPath("$.cost").value(0));

        executar(post("/conversations/" + conversa + "/transfer"), rita, """
                {"operatorId": "%s", "departmentId": "%s", "note": "Cliente quer agendar tosa"}""".formatted(paulo, banho))
                .andExpect(jsonPath("$.operatorName").value("Paulo"))
                .andExpect(jsonPath("$.departmentName").value("Banho e Tosa"))
                .andExpect(jsonPath("$.participants.length()").value(0));
        executar(get("/conversations/" + conversa + "/messages"), dono, null)
                .andExpect(jsonPath("$[-1].internal").value(true))
                .andExpect(jsonPath("$[-1].content").value(
                        "Transferido para o departamento Banho e Tosa e para Paulo por Rita: Cliente quer agendar tosa"));
    }

    @Test
    void canaisMostramWhatsAppBusinessEmBreve() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        String empresa = corpo(mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Floricultura Ipê", "email": "dono@ipe.com", "phone": "44922221111",
                 "documentId": "47193945823", "documentType": "CPF", "password": "flores2026",
                 "passwordConfirmation": "flores2026", "acceptTerms": true}""")), "$.token");
        executar(get("/channels"), admin, null).andExpect(status().isForbidden());
        executar(get("/whatsapp/qr-code"), admin, null).andExpect(status().isForbidden());
        String clienteId = corpo(executar(get("/clients/me"), empresa, null), "$.id");
        executar(put("/clients/" + clienteId + "/whatsapp-instance"), admin, """
                {"instanceId": "3F4B2299E56411F97FFECA692CFECF87", "token": "4D6D0C717E997B6397E9C534"}""")
                .andExpect(jsonPath("$.dedicated").value(true))
                .andExpect(jsonPath("$.tokenMasked").value("4D6D…C534"));
        executar(get("/channels"), empresa, null)
                .andExpect(jsonPath("$[0].id").value("whatsapp_web"))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[1].id").value("whatsapp_business"))
                .andExpect(jsonPath("$[1].badge").value("Em breve"));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.archat.aplicacao.porta.MensagemRepositorio repositorioDeMensagens;

    private String mensagemEnviada(String mensagemId) {
        return repositorioDeMensagens.buscarPorId(java.util.UUID.fromString(mensagemId))
                .filter(m -> m.status() == br.com.archat.dominio.mensagem.StatusMensagem.ENVIADA
                        || m.status() == br.com.archat.dominio.mensagem.StatusMensagem.ENTREGUE)
                .map(br.com.archat.dominio.mensagem.Mensagem::idExterno)
                .orElse(null);
    }

    @Test
    void rotasProtegidasExigemTokenEPerfilCorreto() throws Exception {
        mvc.perform(get("/clients")).andExpect(status().isUnauthorized());
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(get("/recipients"), admin, null).andExpect(status().isForbidden());
    }

    @Test
    void validaDadosDeEntrada() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(post("/clients"), admin, """
                {"name": "X", "documentId": "123", "documentType": "CPF", "planType": "prepaid"}""")
                .andExpect(status().isUnprocessableContent());
        executar(post("/clients"), admin, """
                {"name": "", "documentType": "CPF"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());
    }

    private void aguardarStatus(String token, String mensagemId, String esperado) throws Exception {
        String atual = null;
        for (int i = 0; i < 50 && !esperado.equals(atual); i++) {
            atual = corpo(executar(get("/messages/" + mensagemId + "/status"), token, null), "$.status");
            if (!esperado.equals(atual)) {
                Thread.sleep(100);
            }
        }
        assertThat(atual).isEqualTo(esperado);
    }

    private String token(MockHttpServletRequestBuilder requisicao, String json) throws Exception {
        return corpo(mvc.perform(requisicao.contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()), "$.token");
    }

    private ResultActions executar(MockHttpServletRequestBuilder requisicao, String token, String json) throws Exception {
        requisicao.header("Authorization", "Bearer " + token);
        if (json != null) {
            requisicao.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(requisicao);
    }

    private static String corpo(ResultActions resultado, String caminho) throws Exception {
        return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), caminho).toString();
    }
}
