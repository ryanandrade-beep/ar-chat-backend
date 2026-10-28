package br.com.archat.aplicacao.arquivo;

import br.com.archat.aplicacao.porta.ArquivoRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.arquivo.Arquivo;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;

import java.time.Clock;
import java.util.UUID;

/** Guarda o arquivo escolhido no painel e o devolve para exibição (só para a própria workspace). */
public class GuardarArquivoUseCase {

    private final ArquivoRepositorio arquivos;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public GuardarArquivoUseCase(ArquivoRepositorio arquivos, UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this.arquivos = arquivos;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public Arquivo guardar(UUID clienteId, String nome, String tipoMime, byte[] conteudo) {
        Arquivo arquivo = Arquivo.novo(clienteId, nome, tipoMime, conteudo, relogio.instant());
        unidadeDeTrabalho.executar(() -> arquivos.salvar(arquivo));
        return arquivo;
    }

    public Arquivo ler(UUID clienteId, UUID arquivoId) {
        return arquivos.buscarPorId(arquivoId).filter(a -> a.clienteId().equals(clienteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo"));
    }
}
