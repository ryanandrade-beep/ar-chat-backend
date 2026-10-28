package br.com.archat.aplicacao.plano;

import br.com.archat.aplicacao.porta.AssinaturaRepositorio;
import br.com.archat.aplicacao.porta.PlanoRepositorio;
import br.com.archat.dominio.mensagem.Prioridade;
import br.com.archat.dominio.plano.Plano;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta o plano da workspace: preço de cada mensagem e limite de operadores. Sem assinatura, valem os
 * preços padrão da prioridade (R$ 0,25 e R$ 0,50) e não há limite de operadores.
 */
public class PlanoDoCliente {

    private final AssinaturaRepositorio assinaturas;
    private final PlanoRepositorio planos;

    public PlanoDoCliente(AssinaturaRepositorio assinaturas, PlanoRepositorio planos) {
        this.assinaturas = assinaturas;
        this.planos = planos;
    }

    public Optional<Plano> plano(UUID clienteId) {
        return assinaturas.buscarPorCliente(clienteId).flatMap(a -> planos.buscarPorId(a.planoId()));
    }

    public BigDecimal custo(UUID clienteId, Prioridade prioridade) {
        return plano(clienteId).map(p -> p.custo(prioridade)).orElse(prioridade.custo());
    }

    public Optional<Integer> limiteDeOperadores(UUID clienteId) {
        return plano(clienteId).map(Plano::operadoresIncluidos);
    }
}
