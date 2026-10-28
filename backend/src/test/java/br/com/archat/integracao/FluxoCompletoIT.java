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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void canaisMostramWhatsAppBusinessEmBreve() throws Exception {
        String admin = token(post("/admin/auth"), """
                {"login": "admin", "password": "admin12345"}""");
        executar(get("/channels"), admin, null)
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
