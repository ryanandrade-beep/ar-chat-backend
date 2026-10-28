package br.com.archat.apresentacao.seguranca;

import br.com.archat.aplicacao.autenticacao.Sessao;
import br.com.archat.aplicacao.excecao.AcessoNegadoException;
import br.com.archat.aplicacao.excecao.NaoAutenticadoException;
import br.com.archat.aplicacao.porta.OperadorRepositorio;
import br.com.archat.aplicacao.porta.ServicoToken;
import br.com.archat.dominio.cliente.Operador;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Set;

/**
 * Lê o token do cabeçalho {@code Authorization: Bearer} e entrega ao controller quem está autenticado.
 * Basta o controller declarar um parâmetro {@link ClienteAutenticado}, {@link AdministradorAutenticado}
 * ou {@link UsuarioAutenticado} para a rota exigir aquele perfil.
 */
@Component
public class ResolvedorDeSessao implements HandlerMethodArgumentResolver {

    private static final String PREFIXO = "Bearer ";
    private static final Set<Class<?>> TIPOS = Set.of(ClienteAutenticado.class, AdministradorAutenticado.class,
            UsuarioAutenticado.class);

    private final ServicoToken tokens;
    private final OperadorRepositorio operadores;

    public ResolvedorDeSessao(ServicoToken tokens, OperadorRepositorio operadores) {
        this.tokens = tokens;
        this.operadores = operadores;
    }

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        return TIPOS.contains(parametro.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer mavContainer,
                                  NativeWebRequest requisicao, WebDataBinderFactory binderFactory) {
        Sessao sessao = sessao(requisicao.getHeader(HttpHeaders.AUTHORIZATION));
        Class<?> tipo = parametro.getParameterType();
        if (tipo == UsuarioAutenticado.class) {
            if (sessao.perfil() == Sessao.Perfil.OPERADOR) {
                throw new AcessoNegadoException("Disponível apenas para o responsável pela conta");
            }
            return new UsuarioAutenticado(sessao);
        }
        if (tipo == ClienteAutenticado.class) {
            if (sessao.perfil() == Sessao.Perfil.OPERADOR) {
                Operador operador = operadores.buscarPorId(sessao.id())
                        .filter(Operador::ativo)
                        .orElseThrow(() -> new NaoAutenticadoException("Operador desativado ou removido"));
                return new ClienteAutenticado(operador.clienteId(), operador.id());
            }
            exigirPerfil(sessao, Sessao.Perfil.CLIENTE, "Rota disponível apenas para clientes");
            return new ClienteAutenticado(sessao.id());
        }
        exigirPerfil(sessao, Sessao.Perfil.ADMINISTRADOR, "Rota disponível apenas para administradores");
        return new AdministradorAutenticado(sessao.id());
    }

    private Sessao sessao(String cabecalho) {
        if (cabecalho == null || !cabecalho.startsWith(PREFIXO)) {
            throw new NaoAutenticadoException("Informe o token no cabeçalho Authorization: Bearer <token>");
        }
        return tokens.validar(cabecalho.substring(PREFIXO.length()).strip())
                .orElseThrow(() -> new NaoAutenticadoException("Token inválido ou expirado"));
    }

    private static void exigirPerfil(Sessao sessao, Sessao.Perfil perfil, String mensagem) {
        if (sessao.perfil() != perfil) {
            throw new AcessoNegadoException(mensagem);
        }
    }
}
