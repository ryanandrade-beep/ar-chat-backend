package br.com.archat.dominio.entrega;

import java.util.Set;

/** Estados de uma entrega: registrada pelo operador, saiu, entregue ou cancelada. */
public enum StatusEntrega {
    AGUARDANDO,
    A_CAMINHO,
    ENTREGUE,
    CANCELADA;

    public boolean podeIrPara(StatusEntrega destino) {
        return switch (this) {
            case AGUARDANDO -> Set.of(A_CAMINHO, CANCELADA).contains(destino);
            case A_CAMINHO -> Set.of(ENTREGUE, CANCELADA).contains(destino);
            case ENTREGUE, CANCELADA -> false;
        };
    }

    public boolean emAberto() {
        return this == AGUARDANDO || this == A_CAMINHO;
    }
}
