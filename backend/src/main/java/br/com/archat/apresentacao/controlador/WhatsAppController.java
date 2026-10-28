package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.porta.ConexaoWhatsApp.GrupoWhatsApp;
import br.com.archat.aplicacao.porta.ConexaoWhatsApp.SituacaoConexao;
import br.com.archat.aplicacao.whatsapp.GerenciarConexaoWhatsAppUseCase;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/whatsapp")
@Tag(name = "WhatsApp", description = "Conexão do número de WhatsApp da empresa (feita pelo administrador da empresa)")
class WhatsAppController {

    private final GerenciarConexaoWhatsAppUseCase conexao;

    WhatsAppController(GerenciarConexaoWhatsAppUseCase conexao) {
        this.conexao = conexao;
    }

    @GetMapping("/status")
    @Operation(summary = "Situação da conexão com o WhatsApp (administrador da empresa)")
    ConnectionResponse status(ClienteAutenticado cliente) {
        cliente.exigirGestor();
        return ConnectionResponse.de(conexao.situacao(cliente.id()));
    }

    @GetMapping("/qr-code")
    @Operation(summary = "QR Code para conectar o número (administrador da empresa); expira em poucos segundos, consulte de novo")
    QrCodeResponse qrCode(ClienteAutenticado cliente) {
        cliente.exigirGestor();
        return new QrCodeResponse(conexao.qrCode(cliente.id()));
    }

    @GetMapping("/phone-code/{phone}")
    @Operation(summary = "Código para conectar pelo número (administrador da empresa): digite-o no WhatsApp em Aparelhos conectados > "
            + "Conectar com número de telefone")
    PhoneCodeResponse codigoPorTelefone(ClienteAutenticado cliente, @PathVariable String phone) {
        cliente.exigirGestor();
        return new PhoneCodeResponse(conexao.codigoPorTelefone(cliente.id(), phone));
    }

    @GetMapping("/groups")
    @Operation(summary = "Grupos de que o número participa, com o ID para cadastrar como destinatário (administrador da empresa)")
    List<GroupResponse> grupos(ClienteAutenticado cliente, @RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "50") int size) {
        cliente.exigirGestor();
        return conexao.listarGrupos(cliente.id(), page, size).stream().map(GroupResponse::de).toList();
    }

    @PostMapping("/disconnect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desconecta o número (administrador da empresa)")
    void desconectar(ClienteAutenticado cliente) {
        cliente.exigirGestor();
        conexao.desconectar(cliente.id());
    }

    record ConnectionResponse(String provider, boolean connected, boolean phoneOnline, String detail) {

        static ConnectionResponse de(SituacaoConexao s) {
            return new ConnectionResponse(s.provedor(), s.conectado(), s.celularOnline(), s.detalhe());
        }
    }

    record QrCodeResponse(String image) {
    }

    record PhoneCodeResponse(String code) {
    }

    record GroupResponse(String id, String name) {

        static GroupResponse de(GrupoWhatsApp g) {
            return new GroupResponse(g.id(), g.nome());
        }
    }
}
