package br.com.archat.aplicacao.porta;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface EtiquetasDosContatos {

    Map<UUID, List<String>> doCliente(UUID clienteId);

    void definir(UUID destinatarioId, List<String> etiquetas);

    Set<UUID> comEtiqueta(UUID clienteId, String etiqueta);
}
