package br.com.archat.aplicacao.whatsapp;

import br.com.archat.aplicacao.porta.ClienteRepositorio;
import br.com.archat.aplicacao.porta.InstanciaWhatsAppRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.canal.InstanciaWhatsApp;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Backoffice: vincula à empresa a instância da Z-API (ID e token) que será o número de WhatsApp dela.
 * A conexão em si (QR Code ou código) é feita depois pelo administrador da empresa.
 */
public class VincularInstanciaUseCase {

    private final InstanciaWhatsAppRepositorio instancias;
    private final ClienteRepositorio clientes;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public VincularInstanciaUseCase(InstanciaWhatsAppRepositorio instancias, ClienteRepositorio clientes,
                                    UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this.instancias = instancias;
        this.clientes = clientes;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public Optional<InstanciaWhatsApp> daEmpresa(UUID clienteId) {
        return instancias.buscarPorCliente(clienteId);
    }

    public InstanciaWhatsApp vincular(UUID clienteId, String instancia, String token) {
        InstanciaWhatsApp nova = new InstanciaWhatsApp(clienteId, instancia, token, relogio.instant());
        return unidadeDeTrabalho.executar(() -> {
            clientes.buscarPorId(clienteId).orElseThrow(() -> new RecursoNaoEncontradoException("Cliente"));
            instancias.buscarPorInstancia(nova.instancia())
                    .filter(outra -> !outra.clienteId().equals(clienteId))
                    .ifPresent(outra -> {
                        throw new RegraDeNegocioException("Esta instância já está vinculada a outra empresa");
                    });
            instancias.salvar(nova);
            return nova;
        });
    }

    public void desvincular(UUID clienteId) {
        unidadeDeTrabalho.executar(() -> instancias.remover(clienteId));
    }
}
