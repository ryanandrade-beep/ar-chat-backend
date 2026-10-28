package br.com.archat.apresentacao.dto;

import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.financeiro.Transacao;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ClienteDtos {

    private ClienteDtos() {
    }

    public record CreateClientRequest(@NotBlank @Size(max = 120) String name, @NotBlank String documentId,
                                      @NotBlank String documentType, @NotBlank String planType,
                                      @DecimalMin("0.00") BigDecimal initialAmount) {
    }

    public record UpdateClientRequest(@NotBlank @Size(max = 120) String name, @NotNull Boolean active) {
    }

    public record CreditRequest(@NotNull @DecimalMin(value = "0.01") BigDecimal amount) {
    }

    public record LimitRequest(@NotNull @DecimalMin("0.00") BigDecimal limit) {
    }

    public record ChangePlanRequest(@NotBlank String planType, BigDecimal limit) {
    }

    public record ClientResponse(UUID id, String name, String documentId, String documentType, String planType,
                                 BigDecimal balance, BigDecimal limit, BigDecimal monthlyUsage, BigDecimal available,
                                 boolean active, Instant createdAt) {

        public static ClientResponse de(Cliente c) {
            return new ClientResponse(c.id(), c.nome(), c.documento().numero(), c.documento().tipo().name(),
                    ApiEnums.plano(c.tipoPlano()), c.saldo(), c.limiteMensal(), c.consumoMes(), c.disponivel(),
                    c.ativo(), c.criadoEm());
        }
    }

    public record TransactionResponse(UUID id, String type, BigDecimal amount, BigDecimal balanceAfter, UUID messageId,
                                      Instant timestamp) {

        public static TransactionResponse de(Transacao t) {
            return new TransactionResponse(t.id(), ApiEnums.transacao(t.tipo()), t.valor(), t.saldoApos(), t.mensagemId(),
                    t.dataHora());
        }
    }

    public record BalanceResponse(UUID clientId, String planType, BigDecimal balance, BigDecimal limit,
                                  BigDecimal monthlyUsage, BigDecimal available) {

        public static BalanceResponse de(Cliente c) {
            return new BalanceResponse(c.id(), ApiEnums.plano(c.tipoPlano()), c.saldo(), c.limiteMensal(),
                    c.consumoMes(), c.disponivel());
        }
    }
}
