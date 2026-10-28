package br.com.archat.dominio;

import br.com.archat.dominio.departamento.ConfiguracaoUra;
import br.com.archat.dominio.departamento.Departamento;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguracaoUraTest {

    private final UUID mercado = UUID.randomUUID();
    private final List<Departamento> setores = List.of(
            Departamento.novo(mercado, "Açougue", Instant.now()),
            Departamento.novo(mercado, "Entregas", Instant.now()));

    @Test
    void menuNumeradoEBoasVindasComONomeDoContato() {
        ConfiguracaoUra ura = ConfiguracaoUra.padrao(mercado);
        assertThat(ura.boasVindas("Maria")).startsWith("Olá Maria,");
        assertThat(ura.menu(setores)).endsWith("\n1 - Açougue\n2 - Entregas");
    }

    @Test
    void entendeNumeroOuNomeSemAcento() {
        assertThat(ConfiguracaoUra.escolha(" 2 ", setores)).hasValueSatisfying(d -> assertThat(d.nome()).isEqualTo("Entregas"));
        assertThat(ConfiguracaoUra.escolha("acougue", setores)).hasValueSatisfying(d -> assertThat(d.nome()).isEqualTo("Açougue"));
        assertThat(ConfiguracaoUra.escolha("3", setores)).isEmpty();
        assertThat(ConfiguracaoUra.escolha("oi", setores)).isEmpty();
    }

    @Test
    void validaTentativasEMensagens() {
        ConfiguracaoUra ura = ConfiguracaoUra.padrao(mercado);
        assertThatThrownBy(() -> ura.alterar(true, "Oi", "Qual?", 0, null, true)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> ura.alterar(true, " ", "Qual?", 3, null, true)).isInstanceOf(RegraDeNegocioException.class);
    }
}
