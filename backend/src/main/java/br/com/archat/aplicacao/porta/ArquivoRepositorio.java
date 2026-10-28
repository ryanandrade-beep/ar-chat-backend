package br.com.archat.aplicacao.porta;

import br.com.archat.dominio.arquivo.Arquivo;

import java.util.Optional;
import java.util.UUID;

public interface ArquivoRepositorio {

    void salvar(Arquivo arquivo);

    Optional<Arquivo> buscarPorId(UUID id);
}
