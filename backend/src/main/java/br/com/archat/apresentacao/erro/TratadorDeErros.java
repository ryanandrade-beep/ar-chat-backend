package br.com.archat.apresentacao.erro;

import br.com.archat.aplicacao.excecao.AcessoNegadoException;
import br.com.archat.aplicacao.excecao.CanalIndisponivelException;
import br.com.archat.aplicacao.excecao.NaoAutenticadoException;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.excecao.SaldoInsuficienteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converte as exceções das camadas internas em respostas HTTP com um corpo de erro padronizado. */
@RestControllerAdvice
class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(SaldoInsuficienteException.class)
    ResponseEntity<RespostaErro> saldoInsuficiente(SaldoInsuficienteException e) {
        return resposta(HttpStatus.PAYMENT_REQUIRED, e.getMessage(), null);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ResponseEntity<RespostaErro> naoEncontrado(RecursoNaoEncontradoException e) {
        return resposta(HttpStatus.NOT_FOUND, e.getMessage(), null);
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    ResponseEntity<RespostaErro> regraDeNegocio(RegraDeNegocioException e) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), null);
    }

    @ExceptionHandler(NaoAutenticadoException.class)
    ResponseEntity<RespostaErro> naoAutenticado(NaoAutenticadoException e) {
        return resposta(HttpStatus.UNAUTHORIZED, e.getMessage(), null);
    }

    @ExceptionHandler(AcessoNegadoException.class)
    ResponseEntity<RespostaErro> acessoNegado(AcessoNegadoException e) {
        return resposta(HttpStatus.FORBIDDEN, e.getMessage(), null);
    }

    @ExceptionHandler(CanalIndisponivelException.class)
    ResponseEntity<RespostaErro> canalIndisponivel(CanalIndisponivelException e) {
        return resposta(HttpStatus.BAD_GATEWAY, e.getMessage(), null);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<RespostaErro> arquivoGrande(Exception e) {
        return resposta(HttpStatus.PAYLOAD_TOO_LARGE, "O arquivo deve ter no máximo 16 MB", null);
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    ResponseEntity<RespostaErro> semArquivo(Exception e) {
        return resposta(HttpStatus.BAD_REQUEST, "Envie o arquivo no campo 'file'", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<RespostaErro> camposInvalidos(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(erro -> campos.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
        return resposta(HttpStatus.BAD_REQUEST, "Dados inválidos", campos);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<RespostaErro> requisicaoMalFormada(Exception e) {
        return resposta(HttpStatus.BAD_REQUEST, "Requisição mal formada: verifique o JSON e os parâmetros", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<RespostaErro> rotaInexistente(NoResourceFoundException e) {
        return resposta(HttpStatus.NOT_FOUND, "Rota não encontrada", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<RespostaErro> erroInesperado(Exception e) {
        log.error("Erro inesperado", e);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno; tente novamente mais tarde", null);
    }

    private static ResponseEntity<RespostaErro> resposta(HttpStatus status, String mensagem, Map<String, String> campos) {
        return ResponseEntity.status(status)
                .body(new RespostaErro(status.value(), status.getReasonPhrase(), mensagem, campos, Instant.now()));
    }
}
