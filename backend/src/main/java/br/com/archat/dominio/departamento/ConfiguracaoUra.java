package br.com.archat.dominio.departamento;

import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * URA (atendimento automático) da workspace: mensagem de boas-vindas, pergunta com o menu de departamentos,
 * quantas respostas inválidas aceita antes de encaminhar ao departamento padrão e se ignora grupos.
 * {@code {nome}} nas mensagens é trocado pelo nome do contato.
 */
public class ConfiguracaoUra {

    public static final String BOAS_VINDAS_PADRAO = "Olá {nome}, seja bem-vindo(a) ao nosso atendimento!";
    public static final String PERGUNTA_PADRAO = "Para melhor atender, com qual departamento deseja falar? Responda com o número:";

    private final UUID clienteId;
    private boolean ativa;
    private String mensagemBoasVindas;
    private String mensagemPergunta;
    private int maximoTentativas;
    private UUID departamentoPadraoId;
    private boolean ignorarGrupos;

    public ConfiguracaoUra(UUID clienteId, boolean ativa, String mensagemBoasVindas, String mensagemPergunta,
                           int maximoTentativas, UUID departamentoPadraoId, boolean ignorarGrupos) {
        this.clienteId = Objects.requireNonNull(clienteId);
        alterar(ativa, mensagemBoasVindas, mensagemPergunta, maximoTentativas, departamentoPadraoId, ignorarGrupos);
    }

    public static ConfiguracaoUra padrao(UUID clienteId) {
        return new ConfiguracaoUra(clienteId, false, BOAS_VINDAS_PADRAO, PERGUNTA_PADRAO, 3, null, true);
    }

    public void alterar(boolean ativa, String mensagemBoasVindas, String mensagemPergunta, int maximoTentativas,
                        UUID departamentoPadraoId, boolean ignorarGrupos) {
        if (mensagemBoasVindas == null || mensagemBoasVindas.isBlank() || mensagemBoasVindas.length() > 1000
                || mensagemPergunta == null || mensagemPergunta.isBlank() || mensagemPergunta.length() > 1000) {
            throw new RegraDeNegocioException("Informe as mensagens de boas-vindas e de pergunta (até 1.000 caracteres)");
        }
        if (maximoTentativas < 1 || maximoTentativas > 10) {
            throw new RegraDeNegocioException("A quantidade de tentativas deve ser de 1 a 10");
        }
        this.ativa = ativa;
        this.mensagemBoasVindas = mensagemBoasVindas.strip();
        this.mensagemPergunta = mensagemPergunta.strip();
        this.maximoTentativas = maximoTentativas;
        this.departamentoPadraoId = departamentoPadraoId;
        this.ignorarGrupos = ignorarGrupos;
    }

    public String boasVindas(String nomeContato) {
        return mensagemBoasVindas.replace("{nome}", nomeContato == null ? "" : nomeContato).replaceAll(" +", " ");
    }

    public String menu(List<Departamento> departamentos) {
        StringBuilder texto = new StringBuilder(mensagemPergunta);
        for (int i = 0; i < departamentos.size(); i++) {
            texto.append('\n').append(i + 1).append(" - ").append(departamentos.get(i).nome());
        }
        return texto.toString();
    }

    public static Optional<Departamento> escolha(String resposta, List<Departamento> departamentos) {
        if (resposta == null || resposta.isBlank()) {
            return Optional.empty();
        }
        String texto = semAcento(resposta.strip());
        if (texto.matches("\\d{1,3}")) {
            int opcao = Integer.parseInt(texto);
            return opcao >= 1 && opcao <= departamentos.size() ? Optional.of(departamentos.get(opcao - 1)) : Optional.empty();
        }
        return departamentos.stream().filter(d -> semAcento(d.nome()).equals(texto)).findFirst();
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    public UUID clienteId() { return clienteId; }
    public boolean ativa() { return ativa; }
    public String mensagemBoasVindas() { return mensagemBoasVindas; }
    public String mensagemPergunta() { return mensagemPergunta; }
    public int maximoTentativas() { return maximoTentativas; }
    public UUID departamentoPadraoId() { return departamentoPadraoId; }
    public boolean ignorarGrupos() { return ignorarGrupos; }
}
