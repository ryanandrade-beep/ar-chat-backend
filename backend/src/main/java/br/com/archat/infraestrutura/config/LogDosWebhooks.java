package br.com.archat.infraestrutura.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Registra no log cada chamada recebida em {@code /webhooks/**}: método, caminho (com o segredo da URL oculto),
 * tipo de conteúdo, status da resposta e o corpo (até 3000 caracteres). O corpo é lido antes de tudo, então aparece
 * mesmo quando a requisição é recusada. Serve para saber se a Z-API está chamando o sistema e o que ela envia.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class LogDosWebhooks extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LogDosWebhooks.class);
    private static final int LIMITE = 3000;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/webhooks/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] corpo = request.getInputStream().readAllBytes();
        CorpoEmMemoria encapsulada = new CorpoEmMemoria(request, corpo);
        try {
            chain.doFilter(encapsulada, response);
        } finally {
            String texto = new String(corpo, StandardCharsets.UTF_8);
            log.info("Webhook recebido [{} {} content-type={} bytes={} status={} corpo={}]", request.getMethod(),
                    request.getRequestURI().replaceAll("/webhooks/zapi/[^/]+/", "/webhooks/zapi/***/"),
                    request.getContentType(), corpo.length, response.getStatus(),
                    texto.length() > LIMITE ? texto.substring(0, LIMITE) + "…" : texto);
        }
    }

    private static final class CorpoEmMemoria extends HttpServletRequestWrapper {

        private final byte[] corpo;

        CorpoEmMemoria(HttpServletRequest request, byte[] corpo) {
            super(request);
            this.corpo = corpo;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream origem = new ByteArrayInputStream(corpo);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return origem.read();
                }

                @Override
                public boolean isFinished() {
                    return origem.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
