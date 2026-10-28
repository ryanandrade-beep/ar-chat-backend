package br.com.archat.aplicacao.destinatario;

import br.com.archat.aplicacao.porta.ConexaoWhatsApp;
import br.com.archat.aplicacao.porta.ConexaoWhatsApp.ContatoWhatsApp;
import br.com.archat.aplicacao.porta.DestinatarioRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.conversa.Destinatario;
import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Traz os contatos da agenda do WhatsApp conectado para o cadastro de destinatários. Contatos já cadastrados
 * (com ou sem o DDI 55) são ignorados. Os importados entram sem consentimento para ofertas (LGPD): o cliente
 * marca depois quem aceitou receber campanhas.
 */
public class ImportarContatosUseCase {

    public static final int MAXIMO_POR_IMPORTACAO = 500;

    private final ConexaoWhatsApp conexao;
    private final DestinatarioRepositorio destinatarios;
    private final UnidadeDeTrabalho unidadeDeTrabalho;

    public ImportarContatosUseCase(ConexaoWhatsApp conexao, DestinatarioRepositorio destinatarios,
                                   UnidadeDeTrabalho unidadeDeTrabalho) {
        this.conexao = conexao;
        this.destinatarios = destinatarios;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
    }

    public List<ContatoDaAgenda> listar(UUID clienteId, int pagina, int tamanhoPagina) {
        if (pagina < 1 || tamanhoPagina < 1 || tamanhoPagina > 200) {
            throw new RegraDeNegocioException("Use página a partir de 1 e tamanho de 1 a 200");
        }
        Set<String> cadastrados = telefonesCadastrados(clienteId);
        return conexao.listarContatos(clienteId, pagina, tamanhoPagina).stream()
                .map(c -> new ContatoDaAgenda(c.telefone(), c.nome(), variacoes(c.telefone()).stream().anyMatch(cadastrados::contains)))
                .toList();
    }

    public Resultado importar(UUID clienteId, List<ContatoWhatsApp> contatos) {
        if (contatos == null || contatos.isEmpty()) {
            throw new RegraDeNegocioException("Selecione ao menos um contato");
        }
        if (contatos.size() > MAXIMO_POR_IMPORTACAO) {
            throw new RegraDeNegocioException("Importe no máximo " + MAXIMO_POR_IMPORTACAO + " contatos por vez");
        }
        return unidadeDeTrabalho.executar(() -> {
            Set<String> cadastrados = telefonesCadastrados(clienteId);
            int importados = 0;
            int ignorados = 0;
            for (ContatoWhatsApp contato : contatos) {
                String telefone = contato.telefone() == null ? "" : contato.telefone().replaceAll("\\D", "");
                if (telefone.isEmpty() || variacoes(telefone).stream().anyMatch(cadastrados::contains)) {
                    ignorados++;
                    continue;
                }
                String nome = contato.nome() == null || contato.nome().isBlank() ? telefone : contato.nome();
                Destinatario novo = Destinatario.novo(clienteId, nome.length() > 120 ? nome.substring(0, 120) : nome,
                        telefone, false);
                destinatarios.salvar(novo);
                cadastrados.add(novo.telefone());
                importados++;
            }
            return new Resultado(importados, ignorados);
        });
    }

    private Set<String> telefonesCadastrados(UUID clienteId) {
        Set<String> telefones = new HashSet<>();
        destinatarios.listarPorCliente(clienteId).forEach(d -> telefones.addAll(variacoes(d.telefone())));
        return telefones;
    }

    private static Set<String> variacoes(String telefone) {
        String numero = telefone.replaceAll("\\D", "");
        Set<String> variacoes = new HashSet<>();
        variacoes.add(numero);
        if (numero.startsWith("55") && numero.length() > 11) {
            variacoes.add(numero.substring(2));
        } else if (numero.length() <= 11) {
            variacoes.add("55" + numero);
        }
        return variacoes;
    }

    public record ContatoDaAgenda(String telefone, String nome, boolean jaCadastrado) {
    }

    public record Resultado(int importados, int ignorados) {
    }
}
