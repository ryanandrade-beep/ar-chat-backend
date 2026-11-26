package br.com.archat.aplicacao.destinatario;

import br.com.archat.aplicacao.porta.DestinatarioRepositorio;
import br.com.archat.aplicacao.porta.EtiquetasDosContatos;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Etiquetas dos contatos (ex.: "vip", "açougue", "bairro-centro"), usadas para filtrar o público ao criar uma
 * campanha. Ficam em minúsculas, sem espaços repetidos e só com letras, números, espaço, hífen e sublinhado.
 */
public class GerenciarEtiquetasUseCase {

    public static final int MAXIMO_POR_CONTATO = 10;
    private static final Pattern VALIDA = Pattern.compile("^[\\p{L}0-9][\\p{L}\\p{M}0-9 _-]{0,29}$");

    private final EtiquetasDosContatos etiquetas;
    private final DestinatarioRepositorio destinatarios;
    private final UnidadeDeTrabalho unidadeDeTrabalho;

    public GerenciarEtiquetasUseCase(EtiquetasDosContatos etiquetas, DestinatarioRepositorio destinatarios,
                                     UnidadeDeTrabalho unidadeDeTrabalho) {
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.etiquetas = etiquetas;
        this.destinatarios = destinatarios;
    }

    public static String normalizar(String etiqueta) {
        String limpa = etiqueta == null ? "" : etiqueta.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (!VALIDA.matcher(limpa).matches()) {
            throw new RegraDeNegocioException("Etiqueta inválida: use até 30 letras, números, espaço, hífen ou sublinhado");
        }
        return limpa;
    }

    public Map<UUID, List<String>> doCliente(UUID clienteId) {
        return etiquetas.doCliente(clienteId);
    }

    public List<String> disponiveis(UUID clienteId) {
        TreeSet<String> todas = new TreeSet<>();
        etiquetas.doCliente(clienteId).values().forEach(todas::addAll);
        return List.copyOf(todas);
    }

    public List<String> definir(UUID clienteId, UUID destinatarioId, List<String> novas) {
        destinatarios.buscarPorId(destinatarioId).filter(d -> d.pertenceA(clienteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Destinatário"));
        TreeSet<String> normalizadas = new TreeSet<>();
        (novas == null ? List.<String>of() : novas).forEach(e -> normalizadas.add(normalizar(e)));
        if (normalizadas.size() > MAXIMO_POR_CONTATO) {
            throw new RegraDeNegocioException("Cada contato pode ter no máximo %d etiquetas".formatted(MAXIMO_POR_CONTATO));
        }
        List<String> lista = List.copyOf(normalizadas);
        unidadeDeTrabalho.executar(() -> etiquetas.definir(destinatarioId, lista));
        return lista;
    }
}
