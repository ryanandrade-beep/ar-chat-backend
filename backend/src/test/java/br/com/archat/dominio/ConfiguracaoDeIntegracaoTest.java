package br.com.archat.dominio;

import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.integracao.ConfiguracaoDeIntegracao;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguracaoDeIntegracaoTest {

    @Test
    void aceitaApenasHttpsPublico() {
        assertThat(ConfiguracaoDeIntegracao.validarUrl(" https://crm.exemplo.com/webhooks/archat ")).isEqualTo("https://crm.exemplo.com/webhooks/archat");
        for (String url : new String[]{"http://crm.exemplo.com/x", "ftp://crm.exemplo.com", "https://localhost/x", "https://127.0.0.1/x",
                "https://10.0.0.5/x", "https://192.168.1.10/x", "https://169.254.169.254/latest", "https://[::1]/x",
                "https://servidor-interno/x", "https://crm.local/x", "https://user:senha@crm.exemplo.com/x", "", "   ", "isto nao e url"}) {
            assertThatThrownBy(() -> ConfiguracaoDeIntegracao.validarUrl(url)).as(url).isInstanceOf(RegraDeNegocioException.class);
        }
    }

    @Test
    void reconheceEnderecosInternosDepoisDeResolverONome() throws Exception {
        assertThat(ConfiguracaoDeIntegracao.enderecoInterno(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(ConfiguracaoDeIntegracao.enderecoInterno(InetAddress.getByName("172.16.0.1"))).isTrue();
        assertThat(ConfiguracaoDeIntegracao.enderecoInterno(InetAddress.getByName("100.64.0.1"))).isTrue();
        assertThat(ConfiguracaoDeIntegracao.enderecoInterno(InetAddress.getByName("fd00::1"))).isTrue();
        assertThat(ConfiguracaoDeIntegracao.enderecoInterno(InetAddress.getByName("8.8.8.8"))).isFalse();
    }
}
