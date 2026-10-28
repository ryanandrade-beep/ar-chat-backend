const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  HeadingLevel, AlignmentType, WidthType, BorderStyle, ShadingType, PageBreak,
  Header, Footer, PageNumber, TableOfContents, PageOrientation, LevelFormat, StyleLevel,
  PositionalTab, PositionalTabAlignment, PositionalTabRelativeTo, PositionalTabLeader,
} = require("docx");

const DIR = __dirname;
const DOCS = path.join(DIR, "..");
const PASTAS_DE_IMAGENS = [path.join(DOCS, "03-diagramas"), path.join(DOCS, "04-prototipos")];
const MIGRATIONS = path.join(DOCS, "..", "backend", "src", "main", "resources", "db", "migration");
const OUT = process.argv[2] || path.join(DOCS, "01-documento", "Entrega do Documento Final até Especificações de Requisitos.docx");

// Papel A4 e margens da ABNT NBR 14724 (3 cm superior/esquerda, 2 cm inferior/direita)
const PAGE = { width: 11906, height: 16838 };
const MARGIN = { top: 1701, left: 1701, bottom: 1134, right: 1134 };
const CW = PAGE.width - MARGIN.left - MARGIN.right; // 9071
const CW_LAND = PAGE.height - MARGIN.left - MARGIN.right; // 14003

// ---------- helpers ----------
const run = (text, o = {}) => new TextRun({ text, ...o });

const RECUO_PARAGRAFO = 709; // 1,25 cm na primeira linha

function P(content, o = {}) {
  const children = typeof content === "string" ? [run(content)] : content;
  const justificado = !o.alignment || o.alignment === AlignmentType.JUSTIFIED;
  return new Paragraph({ alignment: AlignmentType.JUSTIFIED, indent: justificado ? { firstLine: RECUO_PARAGRAFO } : undefined, children, ...o });
}
const C = (content, o = {}) => P(content, { alignment: AlignmentType.CENTER, ...o });
const blank = (n = 1) => Array.from({ length: n }, () => new Paragraph({ children: [] }));
const brk = () => new Paragraph({ children: [new PageBreak()] });

// último título visto: dá nome aos quadros que não informam um título próprio
let ultimoTitulo = "";
const semNumero = (t) => t.replace(/^[0-9.]+\s+/, "");
const frase = (t) => (t === t.toUpperCase() ? t.charAt(0) + t.slice(1).toLowerCase() : t);
const titulo = (nivel) => (t) => {
  ultimoTitulo = frase(semNumero(t));
  return new Paragraph({ heading: nivel, children: [run(t)] });
};
const H1 = titulo(HeadingLevel.HEADING_1);
const H2 = titulo(HeadingLevel.HEADING_2);
const H3 = titulo(HeadingLevel.HEADING_3);

const kv = (label, value = "") =>
  new Paragraph({ alignment: AlignmentType.JUSTIFIED, spacing: { after: 120 }, children: [run(label + ": ", { bold: true }), run(value)] });

const sub = (t) => new Paragraph({ spacing: { before: 240, after: 120 }, keepNext: true, children: [run(t, { bold: true })] });

const bullet = (t) =>
  new Paragraph({
    numbering: { reference: "bullets", level: 0 },
    alignment: AlignmentType.JUSTIFIED,
    children: typeof t === "string" ? [run(t)] : t,
  });

const border = { style: BorderStyle.SINGLE, size: 4, color: "000000" };
const borders = { top: border, bottom: border, left: border, right: border };

function cell(text, width, { header = false, mono = false } = {}) {
  const lines = String(text).split("\n");
  return new TableCell({
    borders,
    width: { size: width, type: WidthType.DXA },
    shading: header ? { fill: "D9D9D9", type: ShadingType.CLEAR, color: "auto" } : undefined,
    margins: { top: 100, bottom: 100, left: 140, right: 140 },
    children: lines.map(
      (l) =>
        new Paragraph({
          spacing: { line: 240, after: 0 },
          children: [run(l, { bold: header, size: 20, font: mono ? "Courier New" : "Arial" })],
        })
    ),
  });
}

let quadroN = 0;
const legendaQuadro = (texto) => new Paragraph({ style: "LegendaQuadro", keepNext: true, children: [run(texto)] });
const fonte = () => new Paragraph({ style: "FonteIlustracao", children: [run("Fonte: Os autores (2026).")] });

/** Quadro no padrão ABNT: título acima ("Quadro N – …"), tabela e fonte abaixo. */
function table(headers, rows, widths, { titulo: tituloQuadro } = {}) {
  const total = CW;
  const sum = widths.reduce((a, b) => a + b, 0);
  const w = widths.map((x) => Math.round((x / sum) * total));
  w[w.length - 1] += total - w.reduce((a, b) => a + b, 0);
  quadroN++;
  return [
    legendaQuadro(`Quadro ${quadroN} – ${tituloQuadro || ultimoTitulo}`),
    new Table({
      width: { size: total, type: WidthType.DXA },
      columnWidths: w,
      rows: [
        new TableRow({ tableHeader: true, cantSplit: true, children: headers.map((h, i) => cell(h, w[i], { header: true })) }),
        ...rows.map((r) => new TableRow({ cantSplit: true, children: r.map((c, i) => cell(c, w[i])) })),
      ],
    }),
    fonte(),
  ];
}
const after = () => new Paragraph({ spacing: { after: 0 }, children: [] });

function pngSize(file) {
  const b = fs.readFileSync(file);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
}
/**
 * Usa o SVG gerado pelo PlantUML (vetorial: nítido em qualquer zoom e na impressão) quando existir,
 * com o PNG como reserva para leitores sem suporte a SVG. A proporção é sempre a do PNG.
 */
function imagemDaFigura(f, legenda, file, width, height) {
  const altText = { title: legenda, description: legenda, name: file };
  const transformation = { width, height };
  const svg = f.replace(/\.png$/, ".svg");
  if (!fs.existsSync(svg)) return new ImageRun({ type: "png", data: fs.readFileSync(f), transformation, altText });
  // O PlantUML grava preserveAspectRatio="none"; trocado para manter a proporção caso o leitor reescale
  const dadosSvg = Buffer.from(fs.readFileSync(svg, "utf8").replace('preserveAspectRatio="none"', 'preserveAspectRatio="xMidYMid meet"'));
  return new ImageRun({ type: "svg", data: dadosSvg, fallback: { type: "png", data: fs.readFileSync(f) }, transformation, altText });
}
let figN = 0;
const figuras = [];
/** Figura no padrão ABNT: título acima ("Figura N – …"), imagem e fonte abaixo; ocupa o máximo da página. */
function fig(file, legenda, maxWdxa = CW, maxHpx = 860) {
  const f = PASTAS_DE_IMAGENS.map((pasta) => path.join(pasta, file)).find((c) => fs.existsSync(c));
  if (!f) throw new Error("Imagem não encontrada: " + file);
  const { w, h } = pngSize(f);
  const limiteAltura = maxWdxa === CW ? Math.max(maxHpx, 860) : maxHpx;
  const maxW = (maxWdxa / 1440) * 96;
  const s = Math.min(maxW / w, limiteAltura / h, 1);
  figN++;
  figuras.push(`Figura ${figN} – ${legenda}`);
  return [
    new Paragraph({ style: "LegendaFigura", keepNext: true, children: [run(`Figura ${figN} – ${legenda}`)] }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      keepNext: true,
      spacing: { before: 0, after: 60, line: 240 },
      children: [imagemDaFigura(f, legenda, file, Math.round(w * s), Math.round(h * s))],
    }),
    fonte(),
  ];
}

// Tabela "Ação do ator / Resposta do sistema" no formato do modelo
const fluxo = (pares) => table(["Ação do Ator", "Resposta do Sistema"], pares, [1, 1], { titulo: `Fluxo do caso de uso ${ultimoTitulo}` });

// ---------- cabeçalho / rodapé ----------
const logo = fs.readFileSync(path.join(DIR, "assets", "logo-umfg.png"));
const imagemLogo = () => new ImageRun({ type: "png", data: logo, transformation: { width: 120, height: 56 },
  altText: { title: "UMFG", description: "Logo UMFG Faculdade", name: "logo" } });
const headerSemNumero = new Header({
  children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [imagemLogo()] })],
});
// ABNT: número da página no canto superior direito
const headerNumerado = new Header({
  children: [
    new Paragraph({
      alignment: AlignmentType.CENTER,
      children: [
        imagemLogo(),
        new TextRun({
          children: [
            new PositionalTab({ alignment: PositionalTabAlignment.RIGHT, relativeTo: PositionalTabRelativeTo.MARGIN, leader: PositionalTabLeader.NONE }),
            PageNumber.CURRENT,
          ],
          size: 20,
        }),
      ],
    }),
  ],
});
const emptyFooter = new Footer({ children: [new Paragraph({ children: [] })] });

// ---------- conteúdo ----------
const SISTEMA = "A&R Chat";

const capa = [
  C([run("CURSO SUPERIOR DE TECNOLOGIA EM ANÁLISE E DESENVOLVIMENTO DE SISTEMAS", { bold: true })]),
  ...blank(6),
  C([run("DOCUMENTO DE PROJETO DE ESTÁGIO SUPERVISIONADO I", { bold: true, size: 28 })]),
  ...blank(1),
  C([run(SISTEMA.toUpperCase(), { bold: true, size: 32 })]),
  C([run("Plataforma de atendimento ao cliente e campanhas de marketing pelo WhatsApp", { italics: true })]),
  ...blank(4),
  C("Ryan Andrade — RA 25001981"),
  C("Arthur Mian — RA 25002083"),
  ...blank(9),
  C([run("Cianorte - PR", { bold: true })]),
  C([run("2026", { bold: true })]),
  brk(),
];

const rosto = [
  C([run("ESTÁGIO SUPERVISIONADO I", { bold: true, size: 28 })]),
  ...blank(2),
  C([run(SISTEMA.toUpperCase(), { bold: true, size: 28 })]),
  ...blank(3),
  C("Ryan Andrade"),
  C("Arthur Mian"),
  ...blank(4),
  new Paragraph({
    indent: { left: 4536 },
    alignment: AlignmentType.JUSTIFIED,
    spacing: { line: 240 },
    children: [run("Projeto desenvolvido para a disciplina de ESTÁGIO SUPERVISIONADO I do Curso Superior de Tecnologia em Análise e Desenvolvimento de Sistemas da UMFG.", { size: 22 })],
  }),
  ...blank(1),
  new Paragraph({ indent: { left: 4536 }, children: [run("Prof. Orientador: Me. Guilherme Dias Vicentini", { size: 22 })] }),
  ...blank(8),
  C([run("Cianorte - PR", { bold: true })]),
  C([run("2026", { bold: true })]),
  brk(),
];

const tituloPreTextual = (t) => C([run(t, { bold: true })], { spacing: { after: 360 } });
const sumario = [
  tituloPreTextual("SUMÁRIO"),
  new TableOfContents("Sumário", { hyperlink: true, headingStyleRange: "1-3" }),
  brk(),
];
const listas = [
  tituloPreTextual("LISTA DE FIGURAS"),
  new TableOfContents("Lista de figuras", { hyperlink: true, stylesWithLevels: [new StyleLevel("Legenda de Figura", 1)] }),
  brk(),
  tituloPreTextual("LISTA DE QUADROS"),
  new TableOfContents("Lista de quadros", { hyperlink: true, stylesWithLevels: [new StyleLevel("Legenda de Quadro", 1)] }),
  brk(),
];

// 1 INTRODUÇÃO
const intro = [
  H1("1 INTRODUÇÃO"),
  H2("1.1 Identificação do Primeiro Acadêmico"),
  kv("Nome do Acadêmico", "Ryan Andrade"),
  kv("Número do Acadêmico (RA)", "25001981"),
  kv("Telefone(s) do Acadêmico"),
  kv("E-mail do Acadêmico"),
  kv("Experiência profissional do Acadêmico", "Analista de Testes na Irrah Tech"),
  H2("1.2 Identificação do Segundo Acadêmico"),
  kv("Nome do Acadêmico", "Arthur Mian"),
  kv("Número do Acadêmico (RA)", "25002083"),
  kv("Telefone(s) do Acadêmico"),
  kv("E-mail do Acadêmico"),
  kv("Experiência profissional do Acadêmico"),
  H2("1.3 Identificação da Empresa"),
  kv("Razão Social", "Mian & Guelfi Ltda"),
  kv("Nome Fantasia", "Supermercado São Pedro"),
  kv("CNPJ", "07.427.254/0001-30"),
  kv("Responsável pela Empresa", "Claudinei"),
  kv("Telefone(s) da Empresa", "(44) 3672-1440"),
  kv("Supervisor do Estágio na empresa", "Claudinei"),
  kv("Descrição do Ramo de Atividade", "Supermercado — comércio varejista de alimentos, bebidas e produtos de uso doméstico, com atendimento aos clientes no balcão e pelo WhatsApp."),
  H2("1.4 Identificação do Sistema"),
  kv("Nome do Sistema", SISTEMA),
  kv("Objetivos do sistema"),
  P("O A&R Chat é uma plataforma web para empresas (pessoas físicas ou jurídicas) gerenciarem o atendimento aos seus clientes pelo WhatsApp e dispararem campanhas de marketing. Todas as conversas ficam centralizadas em uma tela única, organizadas pelo status do atendimento — aguardando resposta, em andamento, em espera e finalizado —, de modo que nenhum cliente fique sem retorno. As mensagens podem ser de texto ou levar uma imagem, um documento (como PDF), um áudio ou um vídeo. Cada mensagem enviada tem um custo fixo conforme a prioridade (normal: R$ 0,25; urgente: R$ 0,50), qualquer que seja o tipo, debitado do saldo no plano pré-pago ou somado ao consumo do mês no plano pós-pago."),
  P("As campanhas de marketing permitem enviar a mesma mensagem, como as ofertas da semana, a todos os contatos que consentiram em recebê-las (LGPD), com cobrança tudo ou nada. Todos os envios passam por uma fila com dois níveis de prioridade, processada em segundo plano: as respostas urgentes do atendimento saem antes das mensagens de campanha, sem que as normais fiquem esquecidas (prevenção de starvation). Falhas de envio geram novas tentativas e, se persistirem, o estorno do valor cobrado."),
  P("O canal de atendimento disponível na primeira versão é o WhatsApp Web, conectado por QR Code ou por código enviado ao telefone, por meio da Z-API, que também avisa o sistema, por webhooks, sobre mensagens recebidas, entregas e leituras. A API oficial do WhatsApp Business está prevista como canal futuro (“em breve”). O administrador da plataforma cadastra clientes, controla créditos e limites e acompanha o faturamento. No Supermercado São Pedro, o sistema será usado para responder os clientes (pedidos, horários, disponibilidade de produtos) e divulgar as ofertas da semana."),
  P("Além do escopo principal, o sistema já oferece funcionalidades adicionais: uma equipe de operadores com login próprio por empresa, planos com mensalidade e preço por mensagem, uma URA que direciona o cliente final ao departamento desejado, o atendimento em equipe (participar de um atendimento, notas internas e transferência) e um painel do gestor. Cada empresa conecta o seu próprio número de WhatsApp."),
  brk(),
];

// 2 REQUISITOS
const requisitos = [
  H1("2 IDENTIFICAÇÃO DOS REQUISITOS"),
  H2("2.1 Requisitos de Negócio"),
  ...table(["Número", "Requisito", "Frequência"], [
    ["RN1", "Informar quantos atendimentos foram concluídos e com que rapidez a empresa respondeu", "Eventual"],
    ["RN2", "Informar quais clientes geram mais receita para a plataforma e como usam o serviço", "Eventual"],
    ["RN3", "Informar o resultado das campanhas (entregas, leituras, falhas e custo por mensagem entregue)", "Eventual"],
    ["RN4", "Informar quantas conversas aguardam resposta da empresa", "Contínuo"],
  ], [1, 5, 1.5]),
  after(),
  H2("2.2 Requisitos Funcionais"),
  P("Os requisitos funcionais estão organizados em três cadastros, três movimentações e três relatórios, que formam o escopo principal, além das funcionalidades de apoio e das funcionalidades adicionais (RF13 a RF17). Nenhuma movimentação depende da Z-API: ela é usada apenas como canal para entregar as mensagens e receber as respostas."),
  ...table(["Número", "Requisito", "Tipo", "Frequência"], [
    ["RF1", "Cadastrar Clientes", "Cadastro", "Esporádico"],
    ["RF2", "Cadastrar Destinatários", "Cadastro", "Diária"],
    ["RF3", "Cadastrar Administradores", "Cadastro", "Esporádico"],
    ["RF4", "Realizar Atendimento (responder, colocar em espera, retomar, finalizar)", "Movimentação", "Contínuo"],
    ["RF5", "Disparar Campanha de Marketing", "Movimentação", "Eventual"],
    ["RF6", "Movimentar Créditos e Limites", "Movimentação", "Eventual"],
    ["RF7", "Emitir Relatório de Atendimentos Finalizados", "Relatório", "Eventual"],
    ["RF8", "Emitir Relatório de Faturamento por Cliente", "Relatório", "Eventual"],
    ["RF9", "Emitir Relatório de Desempenho das Campanhas", "Relatório", "Eventual"],
    ["RF10", "Autenticar (backoffice, administrador da empresa e operador)", "Apoio", "Contínuo"],
    ["RF11", "Processar Fila de Envio e Receber Mensagens (interno)", "Apoio", "Contínuo"],
    ["RF12", "Conectar Canal de Atendimento (WhatsApp Web; WhatsApp Business em breve)", "Apoio", "Esporádico"],
    ["RF13", "Gerenciar Operadores", "Adicional", "Esporádico"],
    ["RF14", "Gerenciar Planos e Assinaturas", "Adicional", "Esporádico"],
    ["RF15", "Configurar Departamentos e URA", "Adicional", "Esporádico"],
    ["RF16", "Atender em Equipe (participar, nota interna, transferir)", "Adicional", "Contínuo"],
    ["RF17", "Consultar Painel do Gestor", "Adicional", "Diária"],
  ], [1, 5, 1.6, 1.5]),
  after(),
  H2("2.3 Requisitos Não Funcionais"),
  H3("2.3.1 Requisitos Mínimos de Hardware"),
  ...table(["Número", "Tipo", "Descrição"], [
    ["1", "Processador", "Servidor: mínimo 2 núcleos de 2.0 GHz"],
    ["2", "Memória RAM", "Servidor: mínimo 4 GB; estação do cliente: mínimo 2 GB"],
    ["3", "Espaço em Disco", "Servidor: mínimo 20 GB livres (aplicação, banco de dados e logs)"],
    ["4", "Dispositivo do usuário", "Computador, tablet ou smartphone com navegador web atualizado"],
    ["5", "Conexão de rede", "Acesso à internet de no mínimo 10 Mbps"],
  ], [1, 2, 5]),
  after(),
  H3("2.3.2 Requisitos de Software Básico"),
  P("A tabela a seguir apresenta o software utilizado no desenvolvimento e na execução do sistema e o motivo da escolha de cada um, considerando as necessidades do A&R Chat: cobrança confiável por mensagem, processamento de uma fila em segundo plano e atendimento em tempo próximo do real."),
  ...table(["Número", "Tipo", "Descrição", "Motivo da escolha"], [
    ["1", "Sistema Operacional (servidor)", "Linux Ubuntu 24.04 com Docker",
      "Gratuito, estável e padrão em servidores. Com o Docker, o sistema roda da mesma forma no computador de desenvolvimento e no servidor."],
    ["2", "Sistema Operacional (usuário)", "Qualquer sistema com navegador (Chrome, Edge, Firefox ou Safari)",
      "O sistema é web: o supermercado usa pelo navegador, no computador ou no celular, sem instalar nada."],
    ["3", "Gerenciador de Banco de Dados", "PostgreSQL 16",
      "Gratuito e de código aberto, com transações ACID: a cobrança, o registro financeiro e a mensagem são gravados juntos ou nada é gravado. O bloqueio de registro (SELECT … FOR UPDATE) impede que dois envios simultâneos usem o mesmo saldo, e o tipo numeric guarda valores em reais sem erro de arredondamento."],
    ["4", "Linguagem de Programação (backend)", "Java 21 (versão LTS)",
      "Linguagem orientada a objetos e fortemente tipada, adequada às regras de negócio do sistema (planos, cobrança, estados). A versão 21 tem suporte de longo prazo e recursos como records, e o Java trata bem threads, necessárias para o worker que processa a fila em segundo plano."],
    ["5", "Framework (backend)", "Spring Boot 4 (Spring Web, Spring Data JPA, Bean Validation)",
      "Framework Java mais usado no mercado: cria a API REST, faz a validação dos dados, a injeção de dependências e o acesso ao banco com pouca configuração, deixando o foco nas regras de negócio."],
    ["6", "Versionamento do banco", "Flyway",
      "Cria e atualiza as tabelas automaticamente por scripts numerados (V1, V2…), garantindo que o banco de todos os ambientes tenha a mesma estrutura."],
    ["7", "Linguagem e biblioteca (frontend)", "React com TypeScript",
      "Monta a tela em componentes reutilizáveis (lista de conversas, chat, abas de atendimento) e atualiza o status das mensagens sem recarregar a página. O TypeScript aponta erros de tipo antes da execução."],
    ["8", "Gerenciador de dependências e build", "Maven",
      "Baixa as bibliotecas, compila o projeto e executa todos os testes com um único comando."],
    ["9", "Testes automatizados", "JUnit 5, AssertJ, ArchUnit e banco H2 em memória",
      "Garantem que as regras críticas (cobrança, fila, estorno, atendimento) continuem funcionando a cada alteração. O ArchUnit verifica a separação das camadas da arquitetura, e o H2 permite testar sem depender do banco real."],
    ["10", "Ferramentas e IDEs", "IntelliJ IDEA e Visual Studio Code",
      "IntelliJ tem suporte completo a Java e Spring (execução, depuração e testes); o VS Code é leve e indicado para o frontend."],
    ["11", "Contêineres", "Docker e Docker Compose",
      "Sobem o banco de dados e o sistema com um comando, sem instalar o PostgreSQL manualmente, e repetem o mesmo ambiente na apresentação e no servidor."],
    ["12", "Controle de versão", "Git e GitHub",
      "Registram o histórico de alterações e permitem que a dupla trabalhe no mesmo código sem perder trabalho."],
    ["13", "Teste e documentação da API", "Postman e Swagger (OpenAPI)",
      "O Postman testa as rotas e guarda a sequência de testes; o Swagger gera a documentação da API automaticamente a partir do código, sempre atualizada."],
    ["14", "Modelagem", "PlantUML e draw.io",
      "Com o PlantUML, os diagramas são escritos em texto, versionados junto com o código e gerados automaticamente; o draw.io é usado para ajustes visuais."],
    ["15", "Canal de envio", "WhatsApp Web via Z-API (WhatsApp Business: em breve)",
      "Conecta o número do supermercado por QR Code, sem processo de aprovação, com custo fixo por número. Fica isolado numa interface do sistema, o que permite trocá-lo pela API oficial do WhatsApp Business sem alterar as regras de negócio."],
  ], [0.8, 2, 2.4, 5]),
  after(),
  H3("2.3.3 Outros Requisitos Não Funcionais"),
  P("Restrições legais, expansibilidade, tempo de resposta e demais requisitos que não estão diretamente ligados a uma função do sistema."),
  ...table(["Número", "Tipo", "Descrição"], [
    ["1", "Tempo de Resposta", "No máximo 500 ms para aceitar e enfileirar uma mensagem (o envio é assíncrono e não bloqueia a requisição)"],
    ["2", "Priorização justa", "Mensagens urgentes são processadas primeiro; a cada 3 urgentes consecutivas, 1 mensagem normal é processada, evitando que as normais fiquem paradas indefinidamente (starvation)"],
    ["3", "Consistência financeira", "A cobrança, o registro da transação e a gravação da mensagem ocorrem em uma única transação de banco de dados; em falha definitiva de envio, o valor é estornado"],
    ["4", "Segurança", "Acesso por e-mail e senha (mínimo de 8 caracteres, com letras e números), senhas e códigos de recuperação armazenados com hash (bcrypt), mensagem de erro igual para e-mail inexistente ou senha errada, uso de token nas requisições e CPF/CNPJ com validação dos dígitos verificadores"],
    ["5", "Navegabilidade", "Interface responsiva (desktop e celular), no estilo de aplicativos de mensagens, com indicadores visuais de prioridade e status"],
    ["6", "Portabilidade", "Execução de todo o ambiente (banco, backend e frontend) com um único comando: docker compose up"],
    ["7", "Qualidade", "Testes automatizados da fila de prioridade e das regras financeiras; documentação da API em Swagger/OpenAPI"],
    ["8", "Legal (LGPD)", "Campanhas só podem ser enviadas a destinatários que consentiram em receber ofertas; o consentimento fica registrado no cadastro do contato"],
    ["9", "Ritmo de envio", "Intervalo configurável entre envios para disparos em massa, reduzindo o risco de bloqueio do número de WhatsApp"],
  ], [1, 2, 5]),
  after(),
  H3("2.3.4 Requisitos Adiados"),
  P("Lista de requisitos levantados, mas que não serão implantados na primeira versão do sistema. Vários deles já são oferecidos pelo canal de envio (Z-API); o que falta é o tratamento dentro do sistema, por isso foram adiados para o Estágio Supervisionado II."),
  ...table(["Número", "Descrição", "Detalhes"], [
    ["1", "Armazenamento externo e preço por tipo de mídia",
      "Os arquivos enviados pela tela ficam guardados no banco de dados (até 16 MB). Ficam para depois o armazenamento em um serviço de arquivos, como um bucket, e o preço diferenciado por tipo de mídia."],
    ["2", "Outros formatos de mensagem: localização, link com prévia, botões e listas",
      "Também já disponíveis no canal; dependem de novas telas e regras de cobrança no sistema."],
    ["3", "Criação e gestão de grupos pelo sistema",
      "O canal já permite criar grupos e adicionar ou remover participantes. Na primeira versão, os grupos existentes são apenas cadastrados como destinatários."],
    ["4", "Indicador “digitando…” e presença online",
      "O canal já informa a presença no chat por webhook; falta exibi-la na tela de atendimento."],
    ["5", "Canal WhatsApp Business API (oficial da Meta)",
      "Exibido como “em breve”. Será uma nova implementação da interface de canal, sem alterar as regras de negócio."],
    ["6", "Token JWT padrão (RFC 7519) e envio real de e-mail",
      "Na primeira versão, o token é assinado com HMAC-SHA256 num formato próprio, com validade de 12 horas, e o código de recuperação de senha enviado por e-mail é registrado no log da aplicação (pelo WhatsApp, ele já é enviado de verdade)."],
    ["7", "Distribuição automática das conversas",
      "Cada operador já tem login próprio e assume as conversas; falta distribuir as novas conversas automaticamente entre os operadores do departamento."],
    ["8", "Comunicação em tempo real via WebSocket",
      "A primeira versão atualiza o status das mensagens por consulta periódica (polling)."],
    ["9", "Fila distribuída (Redis ou RabbitMQ)",
      "A fila é mantida em memória e reconstruída a partir do banco de dados quando o sistema reinicia."],
    ["10", "Faturamento mensal do pós-pago",
      "Emissão de fatura e cobrança do consumo do mês."],
  ], [0.8, 3, 5]),
  brk(),
];

// 3 PLANEJAMENTO
const planejamento = [
  H1("3 PLANEJAMENTO E ESTIMATIVA"),
  P("Estimativa inicial de atividades, a ser revisada com o professor orientador."),
  ...table(["Mês", "Atividade prevista"], [
    ["Mês 1", "Levantamento de requisitos com o Supermercado São Pedro, modelagem UML (casos de uso, classes, estados, atividades e sequência) e do banco de dados."],
    ["Mês 2", "Estrutura do projeto (Docker, Spring Boot e PostgreSQL) em arquitetura limpa, autenticação e os três cadastros: clientes, destinatários e administradores."],
    ["Mês 3", "Movimentações financeiras (créditos, limites e conversão de plano) e envio de mensagens com validação de saldo/limite."],
    ["Mês 4", "Fila com prioridade e prevenção de starvation, worker assíncrono, novas tentativas e estorno."],
    ["Mês 5", "Atendimento com status (aguardando, em andamento, em espera, finalizado), webhooks do canal, campanhas de marketing com consentimento (LGPD) e os relatórios de atendimentos finalizados, faturamento por cliente e desempenho das campanhas."],
    ["Mês 6", "Testes automatizados, documentação da API (Swagger), testes de aceitação, ajustes finais e documentação do sistema."],
  ], [1, 6]),
  brk(),
];

// 4 ESPECIFICAÇÃO
const especificacao = [
  H1("4 ESPECIFICAÇÃO"),
  H2("4.1 Funcionamento atual"),
  P("Atualmente, muitas pequenas e médias empresas — como o Supermercado São Pedro, empresa onde este estágio é realizado — atendem os seus clientes pelo WhatsApp instalado em um celular da loja ou nos aparelhos pessoais dos funcionários. As conversas ficam espalhadas: não há uma visão de quem está esperando resposta, quem já foi atendido e quem precisa de retorno mais tarde. Em horários de movimento, mensagens se perdem no meio de outras e clientes ficam sem resposta."),
  P("A divulgação de ofertas é feita copiando a mesma mensagem para vários contatos ou por listas de transmissão, sem registro de quem autorizou receber promoções (exigência da LGPD), sem saber quantas mensagens foram entregues e lidas e sem controle do custo. Avisos importantes, como “seu pedido está pronto”, concorrem com as promoções e podem demorar."),
  P("O sistema proposto centraliza o atendimento em uma tela única com as conversas separadas por status, registra cada atendimento do início ao fim, dispara campanhas apenas para quem consentiu, prioriza os avisos urgentes e controla o custo de cada mensagem enviada."),
  H2("4.2 Descrição dos requisitos funcionais"),
  P("A informatização do processo deve ocorrer visando o funcionamento dos requisitos de forma a atender os objetivos da plataforma, portanto o sistema deve executá-los da seguinte forma:"),
  sub("Cadastros"),
  P([run("RF1 – Cadastrar Clientes – ", { bold: true }), run("o administrador cadastra as empresas que utilizarão a plataforma, informando nome, documento (CPF ou CNPJ), tipo de plano (pré-pago ou pós-pago), saldo inicial ou limite mensal e situação (ativo/inativo). O documento deve ser válido e único no sistema. Clientes com histórico não são excluídos, apenas inativados.")]),
  P([run("RF2 – Cadastrar Destinatários – ", { bold: true }), run("o cliente cadastra os seus destinatários — contatos (nome e telefone com DDD) ou grupos do WhatsApp (nome e ID do grupo, ex.: 120363019502650977-group) —, podendo incluir, alterar, excluir e consultar. Uma mensagem para um grupo é cobrada como uma única mensagem. O telefone não pode se repetir entre os contatos do mesmo cliente. Ao iniciar o envio para um destinatário, o sistema abre a conversa correspondente. Destinatários com mensagens não podem ser excluídos.")]),
  P([run("RF3 – Cadastrar Administradores – ", { bold: true }), run("um administrador cadastra outros administradores da plataforma, informando nome, login e senha. O login é único e a senha é armazenada somente na forma de hash. O sistema cria um administrador inicial na primeira execução.")]),
  sub("Movimentações"),
  P([run("RF4 – Realizar Atendimento – ", { bold: true }), run("quando um cliente final envia mensagem, a conversa entra em “Aguardando”. A empresa responde (escolhendo prioridade normal ou urgente), e o atendimento passa para “Em andamento”; pode colocá-lo “Em espera” (por exemplo, enquanto verifica um pedido), retomá-lo e, ao terminar, “Finalizar”. A resposta pode ser um texto ou uma imagem, um documento (ex.: PDF), um áudio ou um vídeo, informados pelo link do arquivo, com o texto como legenda (o áudio não tem legenda); o preço é o mesmo de uma mensagem de texto. Cada resposta é cobrada: o sistema verifica o saldo (pré-pago) ou o limite (pós-pago), debita o valor, registra a transação e coloca a mensagem na fila; sem saldo ou limite, a resposta é recusada e nada é cobrado. Uma nova mensagem do cliente final depois da finalização abre um novo atendimento.")]),
  P([run("RF5 – Disparar Campanha de Marketing – ", { bold: true }), run("o cliente escreve uma mensagem de marketing — que pode levar uma mídia, como o encarte de ofertas em imagem — e a envia de uma vez para vários contatos, escolhidos um a um ou todos os que aceitam receber ofertas (LGPD). O sistema calcula o custo total (quantidade × custo da prioridade) e só dispara se o saldo ou limite cobrir a campanha inteira: ou cobra e enfileira todas as mensagens, ou recusa sem cobrar nada. Mensagens de campanha não abrem atendimento; se o cliente final responder, aí sim a conversa entra em “Aguardando”.")]),
  P([run("RF6 – Movimentar Créditos e Limites – ", { bold: true }), run("o administrador adiciona créditos ao saldo de clientes pré-pagos, ajusta o limite mensal de clientes pós-pagos e converte clientes entre planos. Toda movimentação gera uma transação financeira no histórico. Na conversão de pré-pago para pós-pago, o saldo residual vira crédito no consumo do mês; na conversão inversa, o consumo pendente do mês precisa estar quitado. O consumo dos clientes pós-pagos é zerado automaticamente no início de cada mês.")]),
  sub("Relatórios"),
  P([run("RF7 – Emitir Relatório de Atendimentos Finalizados – ", { bold: true }), run("o cliente (os próprios) ou o administrador (de todos) emite a relação dos atendimentos finalizados no período: cliente final atendido, quem iniciou, início, primeira resposta, finalização, tempo até a primeira resposta, duração e mensagens trocadas, com a quantidade por dia e as médias. Mostra se a empresa está respondendo rápido. Pode ser exportado em CSV.")]),
  P([run("RF8 – Emitir Relatório de Faturamento por Cliente – ", { bold: true }), run("o administrador emite o ranking dos clientes no período, ordenado pela receita líquida (débitos − estornos), com quantidade de mensagens, percentual de urgentes, estornos, ticket médio por mensagem e participação de cada cliente na receita total. Apoia decisões de preço e acompanhamento de clientes. Pode ser exportado em CSV.")]),
  P([run("RF9 – Emitir Relatório de Desempenho das Campanhas – ", { bold: true }), run("o cliente (as próprias) ou o administrador (de todos) emite, por campanha do período, quantas mensagens foram enviadas, entregues, lidas, falharam ou estão pendentes, o valor estornado, o custo líquido, as taxas de entrega e de leitura e o custo por mensagem entregue. Pode ser exportado em CSV.")]),
  sub("Apoio"),
  P([run("RF10 – Autenticar – ", { bold: true }), run("a empresa cria a própria conta informando nome, e-mail, telefone, CPF ou CNPJ (com os dígitos verificadores validados) e senha, e aceita os Termos de Uso e a Política de Privacidade; a conta começa no plano pré-pago, sem saldo. Há três tipos de acesso: o administrador do backoffice, dono da plataforma A&R Chat, entra com login e senha; o administrador da empresa e os operadores entram com e-mail e senha. O operador não cria a própria conta: ela é criada pelo administrador da empresa. O sistema devolve um token que identifica o usuário nas requisições seguintes. Quem esquece a senha recebe por e-mail, ou pelo WhatsApp quando o número da empresa está conectado, um código de 6 dígitos, válido por 15 minutos e por até 5 tentativas, para cadastrar uma nova. As senhas são guardadas só na forma de hash e clientes inativos não acessam o sistema.")]),
  P([run("RF11 – Processar Fila de Envio e Receber Mensagens – ", { bold: true }), run("processo interno: um worker retira continuamente as mensagens da fila — urgentes primeiro, uma normal a cada três urgentes (anti-starvation), em ordem de chegada dentro de cada prioridade — e as entrega pelo canal. Em caso de erro, tenta de novo até completar 3 tentativas (contando a primeira); depois disso, marca a falha e estorna o valor. As mensagens recebidas (texto, imagem, documento, áudio ou vídeo) e os avisos de entrega e leitura chegam pelos webhooks da Z-API e atualizam as conversas e os atendimentos. Quando a empresa lê a conversa, o sistema avisa o WhatsApp do cliente final, marcando como lida a última mensagem recebida. Ao reiniciar, a fila é reconstruída a partir do banco de dados.")]),
  P([run("RF12 – Conectar Canal de Atendimento – ", { bold: true }), run("o administrador da empresa escolhe o canal: WhatsApp Web, conectado por QR Code por meio da Z-API (disponível), ou WhatsApp Business API, a API oficial da Meta (exibido como “em breve”). O sistema mostra se o número está conectado e se o celular está online, gera o QR Code ou, como alternativa, um código para digitar no WhatsApp do celular (“conectar com número de telefone”), e lista os grupos do número. Desconectar ou conectar outro número não apaga nada: as conversas, mensagens e atendimentos ficam guardados no banco de dados do sistema e continuam visíveis na tela de atendimento, que avisa quando o WhatsApp está desconectado; as novas mensagens passam a sair pelo número conectado. Cada empresa usa a sua própria instância da Z-API, vinculada pelo backoffice; o backoffice não conecta números.")]),
  sub("Funcionalidades adicionais"),
  P([run("RF13 – Gerenciar Operadores – ", { bold: true }), run("o administrador da empresa cadastra os operadores (atendentes) com nome, e-mail e senha, altera, ativa ou desativa e redefine a senha deles. O e-mail é único na plataforma e a quantidade de operadores ativos respeita o limite do plano. O próprio administrador da empresa também tem um perfil de atendente, para assumir e responder conversas.")]),
  P([run("RF14 – Gerenciar Planos e Assinaturas – ", { bold: true }), run("o administrador do backoffice cria e altera planos com nome, mensalidade, quantidade de operadores incluídos e preço da mensagem normal e da urgente, e vincula cada empresa a um plano. A primeira mensalidade é cobrada ao assinar e as seguintes no início de cada mês; sem saldo ou limite, a assinatura fica em atraso. Sem plano, valem os preços padrão (R$ 0,25 e R$ 0,50).")]),
  P([run("RF15 – Configurar Departamentos e URA – ", { bold: true }), run("o administrador da empresa cadastra os departamentos (por exemplo, Vendas e Financeiro) e configura a URA: mensagem de boas-vindas, pergunta, número máximo de tentativas (de 1 a 10), departamento padrão e se grupos devem ser ignorados. Com a URA ligada, quando um cliente final inicia um atendimento, ele recebe o menu e responde com o número do departamento; esgotadas as tentativas, a conversa vai para o departamento padrão. As mensagens da URA não são cobradas.")]),
  P([run("RF16 – Atender em Equipe – ", { bold: true }), run("um operador pode participar de um atendimento em andamento para responder junto com o responsável, deixar notas internas que não vão para o WhatsApp e não são cobradas, e transferir a conversa para outro operador ou departamento com um recado. Quando a conversa tem um responsável, só ele e quem participa podem responder.")]),
  P([run("RF17 – Consultar Painel do Gestor – ", { bold: true }), run("o administrador da empresa acompanha, no período e por operador, o total de atendimentos, quantos estão abertos, aguardando e finalizados, a média do tempo até a primeira resposta, os atendimentos por dia e por operador e os contatos mais frequentes.")]),
  brk(),
];

// 5 ATORES
const atores = [
  H1("5 IDENTIFICAÇÃO DOS ATORES"),
  H2("5.1 Lista de Atores do Sistema"),
  ...table(["Número", "Nome", "Tipo", "Descrição", "Permissão de Acesso", "Requisitos"], [
    ["1", "Cliente (Empresa)", "Usuário", "Administrador da empresa (PF ou PJ) que atende os seus clientes finais, dispara campanhas, conecta o WhatsApp e gerencia a equipe", "Permissão restrita aos dados da própria empresa", "RF2, RF4, RF5, RF7, RF9, RF10, RF12, RF13, RF15, RF16, RF17"],
    ["2", "Operador", "Usuário", "Atendente da empresa, com login criado pelo administrador da empresa", "Permissão restrita ao atendimento da própria empresa", "RF4, RF10, RF16"],
    ["3", "Administrador (Backoffice)", "Usuário", "Dono da plataforma A&R Chat: gerencia todas as empresas, os planos e as finanças", "Permissão total aos requisitos administrativos", "RF1, RF3, RF6, RF7, RF8, RF9, RF10, RF14"],
    ["4", "Worker da Fila", "Software", "Processo em segundo plano que consome a fila e entrega as mensagens pelo canal", "Permissão restrita ao requisito", "RF11"],
    ["5", "Canal WhatsApp (Z-API)", "Comunicação", "Serviço externo que entrega as mensagens e avisa, por webhook, mensagens recebidas, entregas e leituras", "Permissão restrita ao requisito", "RF11"],
  ], [0.9, 1.6, 1.1, 2.8, 2, 1.8]),
  P([run("Tipos de Atores: Usuário / Hardware / Software / Comunicação.", { size: 20 })]),
  brk(),
];

// 6 CASOS DE USO
const casosDeUso = [
  H1("6 VISÃO DE CASOS DE USO"),
  H2("6.1 Diagrama de Casos de Uso"),
  ...fig("01-casos-de-uso.png", "Diagrama de casos de uso do A&R Chat", CW, 700),
  ...table(["Caso de uso", "Tipo", "Requisito"], [
    ["UC01 Autenticar", "Apoio", "RF10"],
    ["UC02 Cadastrar Clientes", "Cadastro", "RF1"],
    ["UC03 Cadastrar Destinatários", "Cadastro", "RF2"],
    ["UC04 Cadastrar Administradores", "Cadastro", "RF3"],
    ["UC05 Realizar Atendimento (inclui Validar Saldo/Limite)", "Movimentação", "RF4"],
    ["UC06 Processar Fila de Envio", "Apoio (interno)", "RF11"],
    ["UC07 Movimentar Créditos e Limites", "Movimentação", "RF6"],
    ["UC08 Emitir Relatório de Atendimentos Finalizados", "Relatório", "RF7"],
    ["UC09 Emitir Relatório de Faturamento", "Relatório", "RF8"],
    ["UC10 Emitir Relatório de Desempenho das Campanhas", "Relatório", "RF9"],
    ["UC11 Conectar Canal de Atendimento", "Apoio", "RF12"],
    ["Receber Mensagem do Cliente Final (estende UC05)", "Apoio (interno)", "RF11"],
    ["UC12 Disparar Campanha de Marketing (inclui Validar Saldo/Limite)", "Movimentação", "RF5"],
    ["UC13 Gerenciar Operadores", "Adicional", "RF13"],
    ["UC14 Gerenciar Planos e Assinaturas", "Adicional", "RF14"],
    ["UC15 Configurar Departamentos e URA", "Adicional", "RF15"],
    ["URA: escolher departamento (estende UC05)", "Adicional (interno)", "RF15"],
    ["UC16 Atender em Equipe (estende UC05)", "Adicional", "RF16"],
    ["UC17 Consultar Painel do Gestor", "Adicional", "RF17"],
  ], [4.5, 1.6, 1], { titulo: "Relação entre casos de uso, tipos e requisitos" }),
  P("O diagrama a seguir mostra os casos de uso das funcionalidades adicionais. O administrador da empresa também atende, por isso herda o papel de operador."),
  ...fig("01b-casos-de-uso-adicionais.png", "Diagrama de casos de uso – funcionalidades adicionais", CW, 520),
  brk(),

  H2("6.2 Descrição dos Casos de Uso"),
  H3("6.2.1 Cadastrar Clientes"),
  sub("a) Descrição Resumida"),
  P("O administrador cadastra os clientes da plataforma, podendo incluir, alterar, inativar ou consultar clientes. Os clientes são identificados pelo CPF ou CNPJ e possuem um plano pré-pago (com saldo) ou pós-pago (com limite mensal)."),
  sub("b) Realização do Caso de Uso"),
  kv("Nome", "Cadastrar Clientes"),
  kv("Frequência", "Esporádico"),
  kv("Regra de negócio", "Este caso de uso deve ser executado sempre que uma nova empresa contratar a plataforma ou quando houver alteração nos dados cadastrais de um cliente."),
  kv("Exceções", "O caso de uso não deve permitir a gravação de cliente com nome ou documento em branco, com CPF/CNPJ inválido ou já cadastrado; nesses casos o sistema deve bloquear a ação e exibir a mensagem de erro correspondente. O caso de uso não deve permitir a exclusão de clientes com mensagens ou transações; nesses casos o cliente é apenas inativado."),
  fluxo([
    ["1 – O ator deve ter 4 opções de tarefas a serem executadas (incluir, alterar, inativar e consultar)", "1 – O sistema exibe as opções disponíveis"],
    ["2 – Se a opção for incluir, o ator informa nome, documento, tipo de documento, tipo de plano e saldo inicial ou limite mensal e confirma a operação", "2 – O sistema valida o documento, verifica se ele já existe, grava o cliente como ativo e registra a transação inicial"],
    ["3 – Se a opção for consultar, o ator informa os parâmetros (nome ou documento)", "3 – O sistema exibe os clientes encontrados"],
    ["3.1 – O ator identifica o cliente e escolhe alterar", "3.1 – O sistema habilita os campos para alteração (exceto o documento)"],
    ["3.2 – O ator informa os novos dados e confirma a operação", "3.2 – O sistema regrava os dados do cliente"],
    ["3.3 – O ator identifica o cliente e escolhe inativar, confirmando a operação", "3.3 – O sistema marca o cliente como inativo, impedindo novos acessos e envios"],
  ]),
  after(),

  H3("6.2.2 Cadastrar Destinatários"),
  sub("a) Descrição Resumida"),
  P("O cliente cadastra os seus contatos, que são os destinatários das mensagens, podendo incluir, alterar, excluir ou consultar."),
  sub("b) Realização do Caso de Uso"),
  kv("Nome", "Cadastrar Destinatários"),
  kv("Frequência", "Diária"),
  kv("Regra de negócio", "Cada destinatário pertence a um único cliente; o telefone, com DDD, não pode se repetir entre os contatos do mesmo cliente."),
  kv("Exceções", "O caso de uso não deve permitir nome em branco nem telefone com menos de 10 ou mais de 15 dígitos. Não deve permitir a exclusão de destinatário que já possua mensagens."),
  fluxo([
    ["1 – O ator escolhe incluir, alterar, excluir ou consultar", "1 – O sistema exibe os contatos do cliente"],
    ["2 – Se a opção for incluir, o ator informa nome e telefone e confirma", "2 – O sistema valida os dados, verifica a duplicidade do telefone e grava o destinatário"],
    ["3 – Se a opção for alterar, o ator identifica o contato e informa os novos dados", "3 – O sistema regrava o destinatário"],
    ["4 – Se a opção for excluir, o ator identifica o contato e confirma", "4 – O sistema verifica se há mensagens e exclui o destinatário"],
  ]),
  after(),

  H3("6.2.3 Cadastrar Administradores"),
  sub("a) Descrição Resumida"),
  P("O administrador cadastra outros administradores, que terão acesso à área administrativa da plataforma."),
  sub("b) Realização do Caso de Uso"),
  kv("Nome", "Cadastrar Administradores"),
  kv("Frequência", "Esporádico"),
  kv("Regra de negócio", "O login é único e a senha deve ter no mínimo 8 caracteres; ela é gravada somente na forma de hash."),
  kv("Exceções", "O caso de uso não deve permitir login repetido nem a exclusão do próprio administrador que está conectado."),
  fluxo([
    ["1 – O ator escolhe incluir, excluir ou consultar", "1 – O sistema exibe os administradores cadastrados"],
    ["2 – Se a opção for incluir, o ator informa nome, login e senha e confirma", "2 – O sistema valida os dados, gera o hash da senha e grava o administrador"],
    ["3 – Se a opção for excluir, o ator identifica o administrador e confirma", "3 – O sistema verifica a regra de exclusão e remove o administrador"],
  ]),
  after(),

  H3("6.2.4 Realizar Atendimento"),
  sub("a) Descrição Resumida"),
  P("A empresa atende o cliente final na tela de atendimento: responde as mensagens (com prioridade normal ou urgente), coloca o atendimento em espera quando precisa de tempo, retoma e finaliza. Cada resposta é cobrada e entra na fila de envio."),
  sub("b) Realização do Caso de Uso"),
  kv("Nome", "Realizar Atendimento"),
  kv("Frequência", "Contínuo"),
  kv("Pré-condição", "O cliente deve estar autenticado e ativo."),
  kv("Regra de negócio", "Status do atendimento: Aguardando (o cliente final escreveu), Em andamento (a empresa respondeu), Em espera (pausado pela empresa) e Finalizado. Custo fixo por resposta: R$ 0,25 (normal) ou R$ 0,50 (urgente); no pré-pago o saldo deve cobrir o custo, no pós-pago o consumo do mês mais o custo não pode passar do limite. Toda cobrança gera uma transação."),
  kv("Exceções", "O caso de uso não deve permitir resposta em branco ou com prioridade inválida (422), nem resposta sem saldo ou limite (402, nada é cobrado). Não permite retomar um atendimento que não está em espera, nem alterar um atendimento finalizado."),
  fluxo([
    ["1 – O ator abre a aba “Aguardando” e seleciona uma conversa", "1 – O sistema exibe o histórico, marca as mensagens como lidas e mostra o saldo/limite"],
    ["2 – O ator digita a resposta, escolhe a prioridade e aciona Enviar", "2 – O sistema valida os dados, cobra o valor, registra a transação e coloca a mensagem na fila"],
    ["", "3 – O sistema muda o atendimento para “Em andamento” e mostra o status da mensagem (enviada, entregue, lida)"],
    ["4 – Se precisar de tempo, o ator aciona “Em espera” e, depois, “Retomar”", "4 – O sistema atualiza o status do atendimento"],
    ["5 – Ao concluir, o ator aciona “Finalizar”", "5 – O sistema registra o fim do atendimento; uma nova mensagem do cliente abre outro"],
  ]),
  after(),
  P("A Figura 3 apresenta os estados do atendimento e as ações que provocam cada mudança."),
  ...fig("08-estados-atendimento.png", "Diagrama de estados – Atendimento", CW, 560),
  P("A Figura 4 apresenta o fluxo de atividades do envio de uma resposta, com as decisões de validação e a verificação de saldo (pré-pago) ou de limite (pós-pago). As Figuras 5 e 6 apresentam a troca de mensagens entre os objetos do sistema: primeiro, o envio e a cobrança da resposta; depois, o processamento pela fila e a confirmação de entrega enviada pelo canal."),
  ...fig("04-atividade-enviar-mensagem.png", "Diagrama de atividades – Enviar resposta do atendimento", CW, 860),
];

const sequencia = [
  ...fig("05-sequencia-enviar-resposta.png", "Diagrama de sequência – Enviar uma resposta do atendimento", CW, 820),
  ...fig("05-sequencia-processar-fila.png", "Diagrama de sequência – Processar a fila e confirmar a entrega", CW, 820),
];

const relatorio = (n, nome, quem, desc, cab, det, rod, regra, excecao) => [
  H3(`6.2.${n} ${nome}`),
  sub("a) Descrição Resumida"),
  P(desc),
  sub("b) Interface"),
  kv("Cabeçalho", cab),
  kv("Linha detalhe", det),
  kv("Rodapé", rod),
  sub("c) Realização do Caso de Uso"),
  kv("Nome", nome),
  kv("Ator", quem),
  kv("Frequência", "Eventual"),
  kv("Regra de negócio", regra),
  kv("Exceções", excecao),
  fluxo([
    ["1 – Seleciona o período e os filtros", "1 – Exibe os filtros disponíveis"],
    ["2 – Seleciona a ordem de emissão", "2 – Organiza a lista conforme o critério selecionado"],
    ["3 – Aciona o comando visualizar ou exportar", "3 – Exibe o relatório na tela ou gera o arquivo"],
  ]),
  after(),
];

const casosDeUso2 = [
  H3("6.2.5 Processar Fila de Envio (interno)"),
  sub("a) Descrição Resumida"),
  P("O worker da fila retira continuamente a próxima mensagem a ser enviada, respeitando a prioridade e a ordem de chegada, simula o envio e atualiza o status da mensagem."),
  sub("b) Realização do Caso de Uso"),
  kv("Nome", "Processar Fila de Envio"),
  kv("Frequência", "Contínuo"),
  kv("Regra de negócio", "Mensagens urgentes têm precedência. Para evitar starvation, após 3 urgentes consecutivas é processada 1 normal, se houver. Dentro de cada prioridade, a ordem é FIFO. São permitidas até 3 tentativas por mensagem."),
  kv("Exceções", "Fila vazia: o worker aguarda 1 segundo e consulta novamente. Após a 3ª falha, a mensagem recebe o status “falha” e o valor é estornado ao cliente (crédito no saldo ou redução do consumo do mês)."),
  fluxo([
    ["1 – O worker solicita a próxima mensagem da fila", "1 – A fila devolve a próxima mensagem pela regra de prioridade, ou nenhuma se estiver vazia"],
    ["2 – O worker marca a mensagem como “processando” e simula o envio", "2 – O sistema grava o novo status"],
    ["3 – Se o envio tiver sucesso, o worker marca a mensagem como “enviada”", "3 – O sistema grava o status; a entrega é confirmada pela simulação"],
    ["4 – Se o envio falhar, o worker incrementa as tentativas", "4 – O sistema reenfileira a mensagem ou, na 3ª tentativa, marca “falha” e estorna o valor"],
  ]),
  after(),
  P("A Figura 7 apresenta os estados pelos quais uma mensagem passa durante o seu ciclo de vida. Com o canal real, a entrega e a leitura são confirmadas pelos webhooks de status da Z-API."),
  ...fig("03-estados-mensagem.png", "Diagrama de estados – Mensagem", CW, 620),

  H3("6.2.6 Movimentar Créditos e Limites"),
  sub("a) Descrição Resumida"),
  P("O administrador adiciona créditos a clientes pré-pagos, ajusta o limite mensal de clientes pós-pagos ou converte um cliente de plano."),
  sub("b) Realização do Caso de Uso"),
  kv("Nome", "Movimentar Créditos e Limites"),
  kv("Frequência", "Eventual"),
  kv("Regra de negócio", "Toda operação gera uma transação (CREDITO ou AJUSTE_LIMITE) com o valor disponível resultante, mantendo o histórico financeiro do cliente."),
  kv("Exceções", "O caso de uso não deve permitir valores menores ou iguais a zero, nem limite menor que o consumo já realizado no mês. A conversão de pós-pago para pré-pago não é permitida com consumo pendente no mês."),
  fluxo([
    ["1 – O ator localiza o cliente", "1 – O sistema exibe o plano, o saldo ou o limite e o consumo do mês"],
    ["2 – O ator escolhe adicionar crédito, ajustar limite ou converter plano e informa o valor", "2 – O sistema valida o valor conforme a regra do plano"],
    ["3 – O ator confirma a operação", "3 – O sistema atualiza o cliente, registra a transação e exibe os valores atualizados"],
  ]),
  after(),

  ...relatorio(7, "Emitir Relatório de Atendimentos Finalizados", "Cliente (os próprios) ou Administrador (todos)",
    "Emite os atendimentos finalizados no período, com os tempos de resposta e de duração de cada um.",
    "Cliente; período; data de emissão.",
    "Cliente final; Iniciado por (cliente final ou empresa); Aberto em; Primeira resposta em; Finalizado em; Tempo até a 1ª resposta; Duração; Mensagens enviadas; Mensagens recebidas.",
    "Total de atendimentos; atendimentos por dia; tempo médio até a primeira resposta; duração média; média de mensagens por atendimento.",
    "O tempo até a primeira resposta só se aplica aos atendimentos iniciados pelo cliente final. Atendimentos ainda abertos não entram no relatório.",
    "O sistema não deve emitir o relatório se a data final for anterior à data inicial."),
  ...relatorio(8, "Emitir Relatório de Faturamento por Cliente", "Administrador",
    "Emite o ranking dos clientes no período pela receita líquida gerada para a plataforma.",
    "Período; data de emissão.",
    "Cliente; Plano; Mensagens; Urgentes; % de urgentes; Receita bruta; Estornos; Receita líquida; Ticket médio; Participação (%). Ordenado pela receita líquida (maior primeiro).",
    "Total de mensagens; receita bruta; estornos; receita líquida da plataforma.",
    "Receita líquida = débitos − estornos. Ticket médio = receita líquida ÷ mensagens cobradas (sem as falhas). Participação = receita do cliente ÷ receita total.",
    "O sistema não deve emitir o relatório se a data final for anterior à data inicial; clientes sem movimento no período não aparecem."),
  ...relatorio(9, "Emitir Relatório de Desempenho das Campanhas", "Cliente (as próprias) ou Administrador (todas)",
    "Emite, por campanha criada no período, o resultado do disparo e quanto custou cada mensagem entregue.",
    "Período; cliente; data de emissão.",
    "Campanha; Prioridade; Data; Destinatários; Enviadas; Entregues; Lidas; Falhas; Pendentes; Custo; Estornado; Custo líquido; Taxa de entrega; Taxa de leitura; Custo por entregue.",
    "Total de destinatários; custo líquido total; taxa de entrega e taxa de leitura gerais.",
    "Enviadas = aceitas pelo canal; taxa de entrega = entregues ÷ mensagens finalizadas; taxa de leitura = lidas ÷ entregues; custo por entregue = custo líquido ÷ entregues.",
    "O sistema não deve emitir o relatório se a data final for anterior à data inicial; sem entregas, o custo por entregue fica em branco."),

  H3("6.2.10 Disparar Campanha de Marketing"),
  sub("a) Descrição Resumida"),
  P("O cliente envia a mesma mensagem de marketing para vários contatos de uma vez — por exemplo, as ofertas da semana de um supermercado. Só participam os contatos que consentiram em receber ofertas. Mensagens de campanha não abrem atendimento."),
  sub("b) Realização do Caso de Uso"),
  kv("Nome", "Disparar Campanha de Marketing"),
  kv("Frequência", "Eventual"),
  kv("Regra de negócio", "Custo total = quantidade de destinatários × custo da prioridade. A cobrança é tudo ou nada: se o saldo/limite não cobrir a campanha inteira, nada é cobrado nem enfileirado. Máximo de 1.000 destinatários por campanha; contatos repetidos são considerados uma única vez."),
  kv("Exceções", "O caso de uso não deve permitir campanha sem nome, sem conteúdo ou sem destinatários; não deve incluir contato sem consentimento (LGPD) nem contato de outro cliente; não deve disparar sem saldo/limite para todas as mensagens (erro 402)."),
  fluxo([
    ["1 – O ator informa nome, conteúdo e prioridade da campanha", "1 – O sistema exibe os contatos que aceitam receber ofertas"],
    ["2 – O ator escolhe os contatos ou a opção “todos que aceitam ofertas” e confirma", "2 – O sistema calcula o custo total e verifica o saldo/limite"],
    ["", "3 – O sistema cobra cada mensagem, registra as transações, grava a campanha e enfileira as mensagens"],
    ["3 – O ator acompanha a campanha", "4 – O sistema exibe quantas mensagens estão enfileiradas, entregues ou com falha"],
  ]),
  after(),
  brk(),
];

// 7 VISÃO ESTÁTICA
const visaoEstatica = [
  H1("7 VISÃO ESTÁTICA"),
  H2("7.1 Classes Conceituais"),
  P("O diagrama de classes foi dividido em três figuras para manter a leitura: a Figura 8 apresenta as entidades do domínio e seus relacionamentos, a Figura 9 apresenta as classes de cobrança, a fila de envio e as enumerações, e a Figura 10 apresenta as classes das funcionalidades adicionais (planos, departamentos, URA e o canal de cada empresa). Três decisões de projeto se destacam:"),
  bullet([run("PlanoPagamento", { bold: true }), run(" é uma interface com duas implementações (PlanoPrePago e PlanoPosPago), seguindo o padrão Strategy: a validação e a cobrança variam conforme o plano sem que o serviço de mensagens precise de condicionais para cada tipo.")]),
  bullet([run("Atendimento", { bold: true }), run(" representa um ciclo de atendimento de uma conversa, com uma máquina de estados própria (Figura 3). Uma conversa tem vários atendimentos ao longo do tempo, mas só um aberto por vez, o que permite medir o tempo até a primeira resposta e a duração de cada um.")]),
  bullet([run("FilaPrioridade", { bold: true }), run(" mantém duas filas FIFO e um contador de urgentes consecutivas. Inserir e remover custam O(1), e o contador garante que as mensagens normais não fiquem sem atendimento enquanto chegam urgentes.")]),
  ...fig("02a-classes-dominio.png", "Diagrama de classes – entidades do domínio", CW, 860),
  ...fig("02b-classes-cobranca-fila.png", "Diagrama de classes – cobrança, fila de envio e enumerações", CW, 760),
  ...fig("02c-classes-equipe-planos-ura.png", "Diagrama de classes – planos, departamentos, URA e canal por empresa", CW, 640),
  H2("7.2 Arquitetura do Sistema"),
  P("O backend segue a arquitetura limpa (Clean Architecture), organizado em quatro camadas. As dependências apontam sempre para dentro: o domínio não conhece nenhuma outra camada nem frameworks; a aplicação conhece apenas o domínio e define portas (interfaces); a infraestrutura implementa essas portas com Spring, JPA e PostgreSQL; e a apresentação expõe os casos de uso como API REST. Essa regra é verificada por testes automatizados (ArchUnit)."),
  ...fig("07-arquitetura.png", "Arquitetura limpa do backend do A&R Chat", CW, 520),
  brk(),
];

// 8 DER + dicionário
const dic = (nome, desc, campos) => [
  kv("Nome da Tabela", nome),
  kv("Descrição", desc),
  ...table(["Campo", "Tipo dado", "Tamanho", "Chave", "Descrição", "Nulo"], campos, [2.2, 1.4, 1.1, 1, 3.4, 0.9],
    { titulo: `Dicionário de dados – tabela ${nome}` }),
  after(),
];

const der = [
  H1("8 DIAGRAMA DE ENTIDADE E RELACIONAMENTO"),
  ...fig("06-der.png", "Diagrama de entidade e relacionamento", CW, 720),
  H2("8.1 Dicionário de Dados e atributos"),
  ...dic("cliente", "contém os dados cadastrais e financeiros das empresas que utilizam a plataforma", [
    ["id", "uuid", "-", "PK", "Identificador do cliente", "Não"],
    ["nome", "varchar", "120", "-", "Nome ou razão social", "Não"],
    ["documento", "varchar", "14", "UK", "CPF ou CNPJ, somente números", "Não"],
    ["tipo_documento", "varchar", "4", "-", "CPF ou CNPJ", "Não"],
    ["tipo_plano", "varchar", "10", "-", "PRE_PAGO ou POS_PAGO", "Não"],
    ["saldo", "numeric", "12,2", "-", "Saldo disponível (pré-pago)", "Não"],
    ["limite_mensal", "numeric", "12,2", "-", "Limite mensal de consumo (pós-pago)", "Não"],
    ["consumo_mes", "numeric", "12,2", "-", "Consumo acumulado no mês (pós-pago)", "Não"],
    ["ativo", "boolean", "-", "-", "Situação do cliente", "Não"],
    ["criado_em", "timestamptz", "-", "-", "Data e hora do cadastro", "Não"],
  ]),
  ...dic("destinatario", "contém os contatos (clientes finais) de cada cliente, que recebem as mensagens", [
    ["id", "uuid", "-", "PK", "Identificador do destinatário", "Não"],
    ["cliente_id", "uuid", "-", "FK", "Cliente dono do contato — tabela cliente", "Não"],
    ["tipo", "varchar", "10", "-", "CONTATO ou GRUPO (grupo do WhatsApp)", "Não"],
    ["nome", "varchar", "120", "-", "Nome do destinatário", "Não"],
    ["telefone", "varchar", "40", "-", "Telefone com DDD, ou ID do grupo (ex.: 120363019502650977-group)", "Não"],
    ["aceita_ofertas", "boolean", "-", "-", "Consentimento (LGPD) para receber campanhas", "Não"],
  ]),
  ...dic("conversa", "contém as conversas entre um cliente e um destinatário", [
    ["id", "uuid", "-", "PK", "Identificador da conversa", "Não"],
    ["cliente_id", "uuid", "-", "FK", "Cliente dono da conversa — tabela cliente", "Não"],
    ["destinatario_id", "uuid", "-", "FK/UK", "Destinatário (uma conversa por destinatário) — tabela destinatario", "Não"],
    ["ultima_mensagem", "varchar", "200", "-", "Trecho da última mensagem", "Sim"],
    ["data_ultima_mensagem", "timestamptz", "-", "-", "Data e hora da última mensagem", "Sim"],
    ["nao_lidas", "integer", "-", "-", "Quantidade de mensagens recebidas ainda não lidas", "Não"],
    ["status_atendimento", "varchar", "12", "-", "AGUARDANDO, EM_ANDAMENTO, EM_ESPERA ou FINALIZADO", "Não"],
    ["operador_id", "uuid", "-", "FK", "Operador responsável — tabela operador (nulo = na fila)", "Sim"],
    ["departamento_id", "uuid", "-", "FK", "Departamento escolhido na URA ou por transferência — tabela departamento", "Sim"],
    ["aguardando_ura", "boolean", "-", "-", "Indica se a conversa espera a escolha do departamento", "Não"],
    ["tentativas_ura", "integer", "-", "-", "Respostas inválidas ao menu da URA", "Não"],
  ]),
  ...dic("mensagem", "contém as mensagens trocadas nas conversas", [
    ["id", "uuid", "-", "PK", "Identificador da mensagem", "Não"],
    ["conversa_id", "uuid", "-", "FK", "Conversa — tabela conversa", "Não"],
    ["campanha_id", "uuid", "-", "FK", "Campanha de origem — tabela campanha (nulo em envios avulsos)", "Sim"],
    ["remetente_tipo", "varchar", "12", "-", "CLIENTE (empresa) ou DESTINATARIO (cliente final)", "Não"],
    ["conteudo", "text", "-", "-", "Texto da mensagem ou legenda da mídia (vazio se não houver)", "Não"],
    ["tipo_midia", "varchar", "10", "-", "IMAGEM, DOCUMENTO, AUDIO ou VIDEO (nulo nas mensagens só de texto)", "Sim"],
    ["url_midia", "varchar", "2048", "-", "Link público do arquivo de mídia", "Sim"],
    ["nome_arquivo", "varchar", "255", "-", "Nome exibido do documento", "Sim"],
    ["prioridade", "varchar", "10", "-", "NORMAL ou URGENTE", "Não"],
    ["status", "varchar", "12", "-", "ENFILEIRADA, PROCESSANDO, ENVIADA, ENTREGUE, LIDA ou FALHA", "Não"],
    ["custo", "numeric", "5,2", "-", "Valor cobrado (0,25 ou 0,50)", "Não"],
    ["tentativas", "integer", "-", "-", "Número de tentativas de envio", "Não"],
    ["id_externo", "varchar", "100", "UK", "Identificador da mensagem no canal (messageId da Z-API)", "Sim"],
    ["criada_em", "timestamptz", "-", "-", "Data e hora de criação", "Não"],
    ["atualizada_em", "timestamptz", "-", "-", "Data e hora da última mudança de status", "Sim"],
    ["automatica", "boolean", "-", "-", "Mensagem enviada pela URA (não cobrada)", "Não"],
    ["interna", "boolean", "-", "-", "Nota interna da equipe (não vai para o WhatsApp)", "Não"],
    ["operador_id", "uuid", "-", "FK", "Operador que escreveu — tabela operador", "Sim"],
    ["contato_nome", "varchar", "120", "-", "Nome do contato compartilhado", "Sim"],
    ["contato_telefone", "varchar", "20", "-", "Telefone do contato compartilhado", "Sim"],
  ]),
  ...dic("campanha", "contém as campanhas (disparos para vários destinatários) de cada cliente", [
    ["id", "uuid", "-", "PK", "Identificador da campanha", "Não"],
    ["cliente_id", "uuid", "-", "FK", "Cliente que disparou — tabela cliente", "Não"],
    ["nome", "varchar", "120", "-", "Nome da campanha", "Não"],
    ["conteudo", "text", "-", "-", "Texto enviado a todos os destinatários ou legenda da mídia", "Não"],
    ["tipo_midia", "varchar", "10", "-", "IMAGEM, DOCUMENTO, AUDIO ou VIDEO (nulo se só texto)", "Sim"],
    ["url_midia", "varchar", "2048", "-", "Link público do arquivo de mídia (ex.: encarte de ofertas)", "Sim"],
    ["nome_arquivo", "varchar", "255", "-", "Nome exibido do documento", "Sim"],
    ["prioridade", "varchar", "10", "-", "NORMAL ou URGENTE", "Não"],
    ["total_destinatarios", "integer", "-", "-", "Quantidade de destinatários", "Não"],
    ["custo_total", "numeric", "12,2", "-", "Valor total cobrado", "Não"],
    ["criada_em", "timestamptz", "-", "-", "Data e hora do disparo", "Não"],
  ]),
  ...dic("atendimento", "contém cada ciclo de atendimento das conversas", [
    ["id", "uuid", "-", "PK", "Identificador do atendimento", "Não"],
    ["conversa_id", "uuid", "-", "FK", "Conversa — tabela conversa", "Não"],
    ["cliente_id", "uuid", "-", "FK", "Cliente (empresa) — tabela cliente", "Não"],
    ["origem", "varchar", "13", "-", "CLIENTE_FINAL ou EMPRESA (quem iniciou)", "Não"],
    ["status", "varchar", "12", "-", "AGUARDANDO, EM_ANDAMENTO, EM_ESPERA ou FINALIZADO", "Não"],
    ["aberto_em", "timestamptz", "-", "-", "Início do atendimento", "Não"],
    ["primeira_resposta_em", "timestamptz", "-", "-", "Primeira resposta da empresa", "Sim"],
    ["finalizado_em", "timestamptz", "-", "-", "Fim do atendimento", "Sim"],
    ["mensagens_enviadas", "integer", "-", "-", "Mensagens enviadas pela empresa no atendimento", "Não"],
    ["mensagens_recebidas", "integer", "-", "-", "Mensagens recebidas do cliente final no atendimento", "Não"],
  ]),
  ...dic("transacao", "contém o histórico financeiro dos clientes", [
    ["id", "uuid", "-", "PK", "Identificador da transação", "Não"],
    ["cliente_id", "uuid", "-", "FK", "Cliente — tabela cliente", "Não"],
    ["mensagem_id", "uuid", "-", "FK", "Mensagem que gerou a transação — tabela mensagem", "Sim"],
    ["tipo", "varchar", "15", "-", "CREDITO, DEBITO, ESTORNO, AJUSTE_LIMITE ou MENSALIDADE", "Não"],
    ["valor", "numeric", "12,2", "-", "Valor da transação", "Não"],
    ["saldo_apos", "numeric", "12,2", "-", "Saldo ou limite restante após a transação", "Não"],
    ["data_hora", "timestamptz", "-", "-", "Data e hora da transação", "Não"],
  ]),
  ...dic("administrador", "contém os usuários administradores da plataforma", [
    ["id", "uuid", "-", "PK", "Identificador do administrador", "Não"],
    ["nome", "varchar", "120", "-", "Nome do administrador", "Não"],
    ["login", "varchar", "50", "UK", "Login de acesso", "Não"],
    ["email", "varchar", "160", "UK", "E-mail de contato", "Sim"],
    ["telefone", "varchar", "13", "-", "Telefone de contato com DDD", "Sim"],
    ["senha_hash", "varchar", "255", "-", "Senha armazenada com hash (bcrypt)", "Não"],
  ]),
  ...dic("conta_acesso", "contém o acesso de cada cliente ao painel (e-mail e senha) e o código de recuperação de senha", [
    ["cliente_id", "uuid", "-", "PK/FK", "Cliente — tabela cliente", "Não"],
    ["email", "varchar", "160", "UK", "E-mail de acesso", "Não"],
    ["telefone", "varchar", "13", "-", "Telefone de contato com DDD", "Não"],
    ["senha_hash", "varchar", "100", "-", "Senha armazenada com hash (bcrypt)", "Não"],
    ["versao_termos", "varchar", "20", "-", "Versão dos Termos de Uso aceita", "Não"],
    ["termos_aceitos_em", "timestamptz", "-", "-", "Data e hora do aceite dos termos", "Não"],
    ["codigo_hash", "varchar", "100", "-", "Hash do código de recuperação de senha", "Sim"],
    ["codigo_expira_em", "timestamptz", "-", "-", "Validade do código (15 minutos)", "Sim"],
    ["tentativas_codigo", "integer", "-", "-", "Tentativas erradas do código (máximo 5)", "Não"],
  ]),
  ...dic("operador", "contém os atendentes de cada empresa, com login próprio", [
    ["id", "uuid", "-", "PK", "Identificador do operador", "Não"],
    ["cliente_id", "uuid", "-", "FK", "Empresa — tabela cliente", "Não"],
    ["nome", "varchar", "120", "-", "Nome do operador", "Não"],
    ["email", "varchar", "160", "UK", "E-mail de acesso", "Não"],
    ["senha_hash", "varchar", "100", "-", "Senha armazenada com hash (bcrypt)", "Não"],
    ["ativo", "boolean", "-", "-", "Situação do operador", "Não"],
    ["dono", "boolean", "-", "-", "Perfil de atendente do administrador da empresa", "Não"],
    ["criado_em", "timestamptz", "-", "-", "Data e hora do cadastro", "Não"],
  ]),
  ...dic("conversa_participante", "contém os operadores que participam de um atendimento junto com o responsável", [
    ["conversa_id", "uuid", "-", "PK/FK", "Conversa — tabela conversa", "Não"],
    ["operador_id", "uuid", "-", "PK/FK", "Operador — tabela operador", "Não"],
    ["desde", "timestamptz", "-", "-", "Início da participação", "Não"],
  ]),
  ...dic("plano", "contém os planos criados pelo backoffice", [
    ["id", "uuid", "-", "PK", "Identificador do plano", "Não"],
    ["nome", "varchar", "60", "UK", "Nome do plano", "Não"],
    ["mensalidade", "numeric", "10,2", "-", "Valor cobrado por mês", "Não"],
    ["operadores_incluidos", "integer", "-", "-", "Quantidade máxima de operadores ativos", "Não"],
    ["preco_normal", "numeric", "5,2", "-", "Preço da mensagem normal", "Não"],
    ["preco_urgente", "numeric", "5,2", "-", "Preço da mensagem urgente", "Não"],
    ["ativo", "boolean", "-", "-", "Situação do plano", "Não"],
    ["criado_em", "timestamptz", "-", "-", "Data e hora do cadastro", "Não"],
  ]),
  ...dic("assinatura", "contém o plano assinado por cada empresa", [
    ["cliente_id", "uuid", "-", "PK/FK", "Empresa — tabela cliente", "Não"],
    ["plano_id", "uuid", "-", "FK", "Plano — tabela plano", "Não"],
    ["situacao", "varchar", "10", "-", "ATIVA ou EM_ATRASO", "Não"],
    ["inicio", "timestamptz", "-", "-", "Início da assinatura", "Não"],
    ["ultima_cobranca", "timestamptz", "-", "-", "Data da última mensalidade cobrada", "Sim"],
  ]),
  ...dic("departamento", "contém os departamentos de cada empresa, oferecidos no menu da URA", [
    ["id", "uuid", "-", "PK", "Identificador do departamento", "Não"],
    ["cliente_id", "uuid", "-", "FK/UK", "Empresa — tabela cliente (nome único por empresa)", "Não"],
    ["nome", "varchar", "60", "UK", "Nome do departamento", "Não"],
    ["ativo", "boolean", "-", "-", "Situação do departamento", "Não"],
    ["criado_em", "timestamptz", "-", "-", "Data e hora do cadastro", "Não"],
  ]),
  ...dic("configuracao_ura", "contém a configuração da URA de cada empresa", [
    ["cliente_id", "uuid", "-", "PK/FK", "Empresa — tabela cliente", "Não"],
    ["ativa", "boolean", "-", "-", "Indica se a URA está ligada", "Não"],
    ["mensagem_boas_vindas", "varchar", "1000", "-", "Mensagem de boas-vindas ({nome} = nome do contato)", "Não"],
    ["mensagem_pergunta", "varchar", "1000", "-", "Pergunta exibida antes do menu", "Não"],
    ["maximo_tentativas", "integer", "-", "-", "Respostas inválidas aceitas (de 1 a 10)", "Não"],
    ["departamento_padrao_id", "uuid", "-", "FK", "Departamento usado quando as tentativas acabam — tabela departamento", "Sim"],
    ["ignorar_grupos", "boolean", "-", "-", "Indica se mensagens de grupos não passam pela URA", "Não"],
  ]),
  ...dic("arquivo", "contém os arquivos enviados pela tela de atendimento", [
    ["id", "uuid", "-", "PK", "Identificador do arquivo", "Não"],
    ["cliente_id", "uuid", "-", "FK", "Empresa — tabela cliente", "Não"],
    ["nome", "varchar", "255", "-", "Nome original do arquivo", "Não"],
    ["tipo_mime", "varchar", "120", "-", "Tipo do arquivo (ex.: image/png)", "Não"],
    ["tamanho", "integer", "-", "-", "Tamanho em bytes (até 16 MB)", "Não"],
    ["conteudo", "bytea", "-", "-", "Conteúdo do arquivo", "Não"],
    ["criado_em", "timestamptz", "-", "-", "Data e hora do envio", "Não"],
  ]),
  ...dic("instancia_whatsapp", "contém a instância da Z-API vinculada a cada empresa", [
    ["cliente_id", "uuid", "-", "PK/FK", "Empresa — tabela cliente", "Não"],
    ["instancia", "varchar", "64", "UK", "ID da instância na Z-API", "Não"],
    ["token", "varchar", "128", "-", "Token da instância", "Não"],
    ["vinculada_em", "timestamptz", "-", "-", "Data e hora do vínculo", "Não"],
  ]),
  brk(),
];

// ANEXOS
// script do banco = todas as migrations do backend, na ordem de versão
const SQL = fs.readdirSync(MIGRATIONS).filter((f) => /^V\d+__.*\.sql$/.test(f))
  .sort((a, b) => parseInt(a.slice(1)) - parseInt(b.slice(1)))
  .map((f) => fs.readFileSync(path.join(MIGRATIONS, f), "utf8").trimEnd())
  .join("\n\n").split("\n");
const anexos = [
  H1("ANEXOS"),
  H2("ANEXO A – PROTÓTIPOS DAS TELAS"),
  P("Protótipos de baixa fidelidade (wireframes) das telas principais, que orientam o desenvolvimento do frontend. As telas finais serão incluídas após a implementação."),
  ...fig("09-prototipo-atendimento.png", "Protótipo – tela de atendimento com as conversas separadas por status", CW, 420),
  ...fig("10-prototipo-canal.png", "Protótipo – seleção do canal de atendimento (WhatsApp Business: em breve)", CW, 360),
  ...fig("11-prototipo-campanha.png", "Protótipo – nova campanha de marketing", CW, 520),
  H2("ANEXO B – PRINTS DOS LAYOUTS DE RELATÓRIOS"),
  P([run("(Telas dos relatórios de atendimentos finalizados, faturamento por cliente e desempenho das campanhas, a serem incluídas após o desenvolvimento do frontend. Os três relatórios já podem ser exportados em CSV.)", { italics: true })]),
  H2("ANEXO C – ROTAS DA API (REST)"),
  ...table(["Método", "Rota", "Descrição"], [
    ["POST", "/auth/register", "Cria a conta da empresa e devolve o token"],
    ["POST", "/auth/login", "Autentica o administrador da empresa ou o operador por e-mail e senha"],
    ["POST", "/auth/forgot-password", "Envia o código de recuperação de senha por e-mail ou WhatsApp"],
    ["POST", "/auth/reset-password", "Troca a senha com o código recebido"],
    ["POST", "/auth", "Autentica o cliente por CPF/CNPJ (demonstração)"],
    ["POST", "/admin/auth", "Autentica o administrador por login e senha"],
    ["GET / POST", "/clients", "Lista / cadastra clientes (admin)"],
    ["GET / PUT", "/clients/{id}", "Consulta / altera um cliente (admin)"],
    ["GET", "/clients/{id}/balance", "Saldo ou limite e consumo (admin ou o próprio cliente)"],
    ["POST", "/clients/{id}/credits", "Adiciona créditos (admin)"],
    ["PUT", "/clients/{id}/limit", "Ajusta o limite mensal (admin)"],
    ["PUT", "/clients/{id}/plan", "Converte o plano (admin)"],
    ["GET", "/clients/me", "Dados da empresa autenticada"],
    ["GET", "/clients/me/transactions", "Extrato da empresa (últimos 90 dias)"],
    ["GET", "/clients/{id}/team", "Responsável e operadores da empresa (admin)"],
    ["GET / PUT / DELETE", "/clients/{id}/whatsapp-instance", "Consulta, vincula ou desvincula a instância da Z-API da empresa (admin)"],
    ["GET / POST", "/plans", "Lista / cria planos (admin)"],
    ["PUT", "/plans/{id}", "Altera um plano (admin)"],
    ["GET / PUT", "/clients/{id}/subscription", "Consulta / vincula o plano da empresa e cobra a primeira mensalidade (admin)"],
    ["GET", "/clients/me/subscription", "Plano, preços e operadores em uso da própria empresa"],
    ["GET / POST", "/admins", "Lista / cadastra administradores (admin)"],
    ["DELETE", "/admins/{id}", "Remove um administrador (admin)"],
    ["GET / PUT", "/admins/me", "Minha conta do administrador"],
    ["PUT", "/admins/me/password", "Troca a senha do administrador"],
    ["GET / POST", "/operators", "Lista / cadastra operadores (cadastro só pelo administrador da empresa)"],
    ["PUT", "/operators/{id}", "Altera, ativa ou desativa um operador"],
    ["PUT", "/operators/{id}/password", "Define uma nova senha para o operador"],
    ["GET / POST", "/departments", "Lista / cria departamentos"],
    ["PUT", "/departments/{id}", "Renomeia, ativa ou desativa um departamento"],
    ["GET / PUT", "/ivr", "Consulta / configura a URA"],
    ["GET / POST", "/recipients", "Lista / cadastra destinatários do cliente"],
    ["PUT / DELETE", "/recipients/{id}", "Altera / exclui um destinatário"],
    ["GET", "/recipients/whatsapp-contacts", "Contatos da agenda do WhatsApp conectado"],
    ["POST", "/recipients/import", "Importa contatos da agenda como destinatários"],
    ["GET", "/conversations", "Conversas do cliente, com filtro por status do atendimento"],
    ["GET", "/conversations/summary", "Quantidade de conversas por status (abas)"],
    ["POST", "/conversations/{id}/hold | resume | finish", "Coloca em espera, retoma ou finaliza o atendimento"],
    ["POST", "/conversations/{id}/read", "Marca as mensagens recebidas como lidas (e avisa o WhatsApp)"],
    ["POST", "/conversations/{id}/simulate-reply", "Simula uma mensagem do cliente final (demonstração)"],
    ["POST", "/conversations/{id}/assign", "Assume, transfere ou devolve a conversa para a fila"],
    ["PUT", "/conversations/{id}/department", "Transfere a conversa para outro departamento"],
    ["POST", "/conversations/{id}/join | leave", "Participa / deixa de participar do atendimento"],
    ["POST", "/conversations/{id}/notes", "Nota interna para a equipe (não vai para o WhatsApp)"],
    ["POST", "/conversations/{id}/transfer", "Transfere para outro operador ou departamento, com recado"],
    ["GET", "/conversations/{id}/messages", "Mensagens de uma conversa"],
    ["POST", "/messages", "Envia uma mensagem de texto, com mídia ou com contato (cobra e enfileira)"],
    ["POST", "/media", "Recebe um arquivo do computador para enviar na mensagem"],
    ["GET", "/media/{id}", "Conteúdo de um arquivo enviado (somente a própria empresa)"],
    ["GET", "/messages/{id}/status", "Status de uma mensagem"],
    ["POST", "/messages/{id}/read", "Simula a leitura pelo destinatário"],
    ["GET / POST", "/campaigns", "Lista / dispara campanhas do cliente"],
    ["GET", "/campaigns/{id}", "Andamento de uma campanha"],
    ["GET", "/reports/attendances", "Atendimentos finalizados"],
    ["GET", "/reports/revenue", "Faturamento por cliente (admin)"],
    ["GET", "/reports/campaigns", "Desempenho das campanhas"],
    ["GET", "/reports/dashboard", "Painel do gestor, com filtro por operador e período"],
    ["GET", "/queue/status", "Monitoramento da fila (admin)"],
    ["GET", "/channels", "Canais de atendimento (WhatsApp Web; WhatsApp Business: em breve)"],
    ["GET", "/whatsapp/status", "Situação da conexão do WhatsApp (administrador da empresa)"],
    ["GET", "/whatsapp/groups", "Grupos do número conectado, com o ID para cadastro (administrador da empresa)"],
    ["GET", "/whatsapp/qr-code", "QR Code para conectar o número via Z-API (administrador da empresa)"],
    ["GET", "/whatsapp/phone-code/{telefone}", "Código para conectar o número pelo telefone, alternativa ao QR Code (administrador da empresa)"],
    ["POST", "/whatsapp/disconnect", "Desconecta o número (administrador da empresa)"],
    ["POST", "/webhooks/zapi/{segredo}/received", "Webhook da Z-API: mensagem recebida"],
    ["POST", "/webhooks/zapi/{segredo}/status", "Webhook da Z-API: entregue / lida"],
  ], [1.4, 3, 4], { titulo: "Rotas da API (REST)" }),
  after(),
  H2("ANEXO D – SCRIPT DO BANCO"),
  ...SQL.map((l) => new Paragraph({ spacing: { line: 240, after: 0 }, children: [run(l || " ", { font: "Courier New", size: 17 })] })),
];

// ---------- documento ----------
const portrait = { page: { size: PAGE, margin: MARGIN } };
const landscape = { page: { size: { ...PAGE, orientation: PageOrientation.LANDSCAPE }, margin: MARGIN } };

const doc = new Document({
  creator: "Ryan Andrade e Arthur Mian",
  title: "Documento de Projeto de Estágio Supervisionado I – A&R Chat",
  features: { updateFields: true },
  styles: {
    default: {
      document: { run: { font: "Arial", size: 24 }, paragraph: { spacing: { after: 120, line: 360 } } },
    },
    paragraphStyles: [
      // ABNT NBR 6024: títulos no mesmo corpo do texto (12 pt), diferenciados por caixa alta, negrito e itálico
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: "Arial", size: 24, bold: true, allCaps: true, color: "000000" },
        paragraph: { spacing: { before: 0, after: 360, line: 360 }, keepNext: true, keepLines: true, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: "Arial", size: 24, bold: true, color: "000000" },
        paragraph: { spacing: { before: 360, after: 240, line: 360 }, keepNext: true, keepLines: true, outlineLevel: 1 } },
      { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: "Arial", size: 24, bold: true, italics: true, color: "000000" },
        paragraph: { spacing: { before: 360, after: 240, line: 360 }, keepNext: true, keepLines: true, outlineLevel: 2 } },
      { id: "LegendaFigura", name: "Legenda de Figura", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: "Arial", size: 20, bold: false, color: "000000" },
        paragraph: { alignment: AlignmentType.CENTER, spacing: { before: 240, after: 120, line: 240 }, keepNext: true } },
      { id: "LegendaQuadro", name: "Legenda de Quadro", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: "Arial", size: 20, color: "000000" },
        paragraph: { alignment: AlignmentType.LEFT, spacing: { before: 240, after: 120, line: 240 }, keepNext: true } },
      { id: "FonteIlustracao", name: "Fonte de Ilustracao", basedOn: "Normal", next: "Normal",
        run: { font: "Arial", size: 20, color: "000000" },
        paragraph: { alignment: AlignmentType.LEFT, spacing: { before: 60, after: 240, line: 240 } } },
    ],
  },
  numbering: {
    config: [{ reference: "bullets", levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] }],
  },
  sections: [
    { properties: portrait, headers: { default: headerSemNumero }, footers: { default: emptyFooter },
      children: [...capa, ...rosto, ...sumario, ...listas].flat(Infinity) },
    { properties: portrait, headers: { default: headerNumerado }, footers: { default: emptyFooter },
      children: [...intro, ...requisitos, ...planejamento, ...especificacao, ...atores, ...casosDeUso].flat(Infinity) },
    { properties: portrait, headers: { default: headerNumerado }, footers: { default: emptyFooter }, children: [...sequencia].flat(Infinity) },
    { properties: portrait, headers: { default: headerNumerado }, footers: { default: emptyFooter },
      children: [...casosDeUso2, ...visaoEstatica, ...der, ...anexos].flat(Infinity) },
  ],
});

Packer.toBuffer(doc).then((b) => {
  fs.writeFileSync(OUT, b);
  console.log("ok", OUT, figuras.join(" | "));
});
