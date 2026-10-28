package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.arquivo.GuardarArquivoUseCase;
import br.com.archat.apresentacao.dto.ApiEnums;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import br.com.archat.dominio.arquivo.Arquivo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@RestController
@Tag(name = "Arquivos", description = "Upload de arquivos do computador para enviar no chat (até 16 MB)")
class ArquivoController {

    private final GuardarArquivoUseCase arquivos;

    ArquivoController(GuardarArquivoUseCase arquivos) {
        this.arquivos = arquivos;
    }

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Recebe um arquivo do computador; use a url devolvida em media.url ao enviar a mensagem")
    UploadResponse enviar(ClienteAutenticado cliente, @RequestParam("file") MultipartFile arquivo) throws IOException {
        Arquivo guardado = arquivos.guardar(cliente.id(), arquivo.getOriginalFilename(), arquivo.getContentType(),
                arquivo.getBytes());
        return new UploadResponse(guardado.id(), guardado.url(), ApiEnums.midia(guardado.tipoDeMidia()), guardado.nome(),
                guardado.tipoMime(), guardado.conteudo().length);
    }

    @GetMapping("/media/{id}")
    @Operation(summary = "Conteúdo do arquivo, para o painel exibir (somente a própria workspace)")
    ResponseEntity<byte[]> ler(ClienteAutenticado cliente, @PathVariable UUID id) {
        Arquivo arquivo = arquivos.ler(cliente.id(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arquivo.tipoMime()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(arquivo.nome(), StandardCharsets.UTF_8).build().toString())
                .body(arquivo.conteudo());
    }

    record UploadResponse(UUID id, String url, String type, String fileName, String mimeType, int size) {
    }
}
