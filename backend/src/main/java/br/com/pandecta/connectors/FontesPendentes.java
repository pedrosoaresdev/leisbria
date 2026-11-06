package br.com.pandecta.connectors;

import java.util.List;

/**
 * Fontes desejadas cuja integração ainda NÃO existe, com o motivo e o que é necessário.
 * Elas aparecem na página "Fontes" com estado PENDENTE e não alimentam a base: nenhum dado é simulado.
 */
public final class FontesPendentes {
    private FontesPendentes() {}

    public record Pendente(String id, String nome, String descricao, String url, String requisitos) {}

    public static final List<Pendente> LISTA = List.of(
            new Pendente("stf", "Supremo Tribunal Federal — jurisprudência",
                    "Acórdãos, decisões monocráticas, súmulas vinculantes, repercussão geral e ações de controle concentrado (ADI, ADC, ADPF, ADO).",
                    "https://portal.stf.jus.br/",
                    "O STF não oferece API pública de jurisprudência, e o portal de pesquisa usa proteção anti-robô; a raspagem violaria os termos de uso. "
                            + "Caminhos: (1) solicitar acesso/convênio de dados ao STF (Secretaria de Altos Estudos / Corte Aberta); "
                            + "(2) importar os conjuntos CSV do portal Corte Aberta (estatísticas e listas de processos, sem inteiro teor); "
                            + "(3) captar o DJe do STF, se o tribunal passar a publicar no DJEN. O STF também não integra a API pública do DataJud."),
            new Pendente("tst-trts", "TST e TRTs — jurisprudência",
                    "Acórdãos da Justiça do Trabalho.",
                    "https://jurisprudencia.jt.jus.br/",
                    "A pesquisa unificada da Justiça do Trabalho não publica API aberta documentada. As publicações dos TRTs/TST chegam pelo DJEN "
                            + "(já integrado — inclua as siglas em DJEN_TRIBUNAIS) e os metadados pelo DataJud. Para inteiro teor estruturado, é necessário convênio com o CSJT."),
            new Pendente("tjs-portais", "Tribunais de Justiça — bases próprias de jurisprudência",
                    "Ementários e inteiro teor mantidos por cada TJ (e-SAJ, PJe, sistemas próprios).",
                    "https://www.cnj.jus.br/",
                    "Cada TJ tem sistema próprio, geralmente sem API pública e com termos que restringem coleta automatizada. A cobertura atual vem do DJEN "
                            + "(acórdãos/decisões publicados) e do DataJud (metadados). Integrações diretas exigem acordo com cada tribunal."),
            new Pendente("tse-tres", "TSE e TREs — jurisprudência eleitoral",
                    "Acórdãos e resoluções da Justiça Eleitoral.",
                    "https://www.tse.jus.br/jurisprudencia",
                    "Sem API pública de jurisprudência documentada. Resoluções do TSE podem ser obtidas pelo LexML; publicações processuais, via DJEN quando disponíveis."),
            new Pendente("stm", "Superior Tribunal Militar — jurisprudência",
                    "Acórdãos da Justiça Militar da União.",
                    "https://www.stm.jus.br/",
                    "Sem API pública de jurisprudência. Metadados processuais disponíveis via DataJud (alias stm)."),
            new Pendente("dou", "Diário Oficial da União (Imprensa Nacional)",
                    "Atos normativos publicados diariamente (leis, decretos, portarias, instruções normativas).",
                    "https://www.in.gov.br/",
                    "A Imprensa Nacional disponibiliza os arquivos XML do DOU pelo serviço INLABS, que exige cadastro gratuito e credenciais (usuário/senha). "
                            + "Configure as credenciais e implemente o conector INLABS para portarias e instruções normativas."),
            new Pendente("camara-senado", "Câmara dos Deputados e Senado Federal",
                    "Proposições legislativas e tramitação.",
                    "https://dadosabertos.camara.leg.br/",
                    "Ambas as casas têm APIs de dados abertos (sem chave). A integração de proposições está planejada para a próxima etapa; normas já publicadas vêm do LexML e do Planalto."),
            new Pendente("doutrina", "Doutrina",
                    "Livros e artigos jurídicos.",
                    null,
                    "Doutrina é protegida por direitos autorais. Só pode ser exibida com licença de editoras ou repositórios de acesso aberto com licença compatível (ex.: Creative Commons)."));
}
