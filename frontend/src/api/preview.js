/**
 * MODO PRÉ-VISUALIZAÇÃO (VITE_PREVIEW=true)
 * ---------------------------------------------------------------
 * Permite ver a interface sem o backend. Regras:
 *  - A única legislação incluída é um TRECHO real da Constituição Federal de 1988 (arts. 1º a 3º e
 *    parte do art. 5º), transcrito do texto oficial, com link para o Planalto.
 *  - NÃO há jurisprudência, decisões, súmulas ou radar simulados: essas áreas aparecem vazias
 *    com o aviso de que dependem da sincronização com as fontes oficiais.
 *  - A IA aparece como desativada.
 */
import { ApiError } from './client';

const URL_CF = 'https://www.planalto.gov.br/ccivil_03/constituicao/constituicao.htm';
const AGORA = new Date().toISOString();
const FONTE = { fonte: 'planalto', fonteNome: 'Portal da Legislação — Planalto', urlOficial: URL_CF };

const T1 = 'TÍTULO I — Dos Princípios Fundamentais';
const T2 = 'TÍTULO II — Dos Direitos e Garantias Fundamentais › CAPÍTULO I — Dos Direitos e Deveres Individuais e Coletivos';

const D = (id, nivel, marcador, rotulo, artigo, texto, hierarquia) => ({
  id: `cf1988:${id}`, normaId: 'cf1988', normaTitulo: 'Constituição Federal', nivel, marcador, rotulo, artigo,
  texto, hierarquia, vigente: true, versao: 1, ...FONTE
});

const DISPOSITIVOS = [
  D('art-1', 'artigo', 'Art. 1º', 'Art. 1º', '1', 'A República Federativa do Brasil, formada pela união indissolúvel dos Estados e Municípios e do Distrito Federal, constitui-se em Estado Democrático de Direito e tem como fundamentos:', T1),
  D('art-1:inc-I', 'inciso', 'I -', 'Art. 1º, I', '1', 'a soberania;', T1),
  D('art-1:inc-II', 'inciso', 'II -', 'Art. 1º, II', '1', 'a cidadania;', T1),
  D('art-1:inc-III', 'inciso', 'III -', 'Art. 1º, III', '1', 'a dignidade da pessoa humana;', T1),
  D('art-1:inc-IV', 'inciso', 'IV -', 'Art. 1º, IV', '1', 'os valores sociais do trabalho e da livre iniciativa;', T1),
  D('art-1:inc-V', 'inciso', 'V -', 'Art. 1º, V', '1', 'o pluralismo político.', T1),
  D('art-1:par-unico', 'paragrafo', 'Parágrafo único.', 'Art. 1º, parágrafo único', '1', 'Todo o poder emana do povo, que o exerce por meio de representantes eleitos ou diretamente, nos termos desta Constituição.', T1),
  D('art-2', 'artigo', 'Art. 2º', 'Art. 2º', '2', 'São Poderes da União, independentes e harmônicos entre si, o Legislativo, o Executivo e o Judiciário.', T1),
  D('art-3', 'artigo', 'Art. 3º', 'Art. 3º', '3', 'Constituem objetivos fundamentais da República Federativa do Brasil:', T1),
  D('art-3:inc-I', 'inciso', 'I -', 'Art. 3º, I', '3', 'construir uma sociedade livre, justa e solidária;', T1),
  D('art-3:inc-II', 'inciso', 'II -', 'Art. 3º, II', '3', 'garantir o desenvolvimento nacional;', T1),
  D('art-3:inc-III', 'inciso', 'III -', 'Art. 3º, III', '3', 'erradicar a pobreza e a marginalização e reduzir as desigualdades sociais e regionais;', T1),
  D('art-3:inc-IV', 'inciso', 'IV -', 'Art. 3º, IV', '3', 'promover o bem de todos, sem preconceitos de origem, raça, sexo, cor, idade e quaisquer outras formas de discriminação.', T1),
  D('art-5', 'artigo', 'Art. 5º', 'Art. 5º', '5', 'Todos são iguais perante a lei, sem distinção de qualquer natureza, garantindo-se aos brasileiros e aos estrangeiros residentes no País a inviolabilidade do direito à vida, à liberdade, à igualdade, à segurança e à propriedade, nos termos seguintes:', T2),
  D('art-5:inc-I', 'inciso', 'I -', 'Art. 5º, I', '5', 'homens e mulheres são iguais em direitos e obrigações, nos termos desta Constituição;', T2),
  D('art-5:inc-II', 'inciso', 'II -', 'Art. 5º, II', '5', 'ninguém será obrigado a fazer ou deixar de fazer alguma coisa senão em virtude de lei;', T2),
  D('art-5:inc-III', 'inciso', 'III -', 'Art. 5º, III', '5', 'ninguém será submetido a tortura nem a tratamento desumano ou degradante;', T2),
  D('art-5:inc-LXI', 'inciso', 'LXI -', 'Art. 5º, LXI', '5', 'ninguém será preso senão em flagrante delito ou por ordem escrita e fundamentada de autoridade judiciária competente, salvo nos casos de transgressão militar ou crime propriamente militar, definidos em lei;', T2),
  D('art-5:inc-LXII', 'inciso', 'LXII -', 'Art. 5º, LXII', '5', 'a prisão de qualquer pessoa e o local onde se encontre serão comunicados imediatamente ao juiz competente e à família do preso ou à pessoa por ele indicada;', T2),
  D('art-5:inc-LXIII', 'inciso', 'LXIII -', 'Art. 5º, LXIII', '5', 'o preso será informado de seus direitos, entre os quais o de permanecer calado, sendo-lhe assegurada a assistência da família e de advogado;', T2),
  D('art-5:inc-LXIV', 'inciso', 'LXIV -', 'Art. 5º, LXIV', '5', 'o preso tem direito à identificação dos responsáveis por sua prisão ou por seu interrogatório policial;', T2),
  D('art-5:inc-LXV', 'inciso', 'LXV -', 'Art. 5º, LXV', '5', 'a prisão ilegal será imediatamente relaxada pela autoridade judiciária;', T2),
  D('art-5:inc-LXVI', 'inciso', 'LXVI -', 'Art. 5º, LXVI', '5', 'ninguém será levado à prisão ou nela mantido, quando a lei admitir a liberdade provisória, com ou sem fiança;', T2),
  D('art-5:inc-LXVIII', 'inciso', 'LXVIII -', 'Art. 5º, LXVIII', '5', 'conceder-se-á habeas corpus sempre que alguém sofrer ou se achar ameaçado de sofrer violência ou coação em sua liberdade de locomoção, por ilegalidade ou abuso de poder;', T2)
].map((d, i) => ({ ...d, ordem: i }));

const ESTRUTURA = [
  { nivel: 'titulo', rotulo: 'TÍTULO I', nome: 'Dos Princípios Fundamentais', antesDe: 'cf1988:art-1' },
  { nivel: 'titulo', rotulo: 'TÍTULO II', nome: 'Dos Direitos e Garantias Fundamentais', antesDe: 'cf1988:art-5' },
  { nivel: 'capitulo', rotulo: 'CAPÍTULO I', nome: 'Dos Direitos e Deveres Individuais e Coletivos', antesDe: 'cf1988:art-5' }
];

const NORMA_CF = {
  id: 'cf1988', tipo: 'Constituição', ano: 1988, titulo: 'Constituição da República Federativa do Brasil de 1988',
  apelido: 'Constituição Federal', siglas: ['CF', 'CF/88'], dataPublicacao: '1988-10-05', situacao: 'Vigente',
  ementa: 'Texto constitucional promulgado em 5 de outubro de 1988.', textoDisponivel: true,
  totalDispositivos: DISPOSITIVOS.length, estrutura: ESTRUTURA, sincronizadoEm: AGORA, ...FONTE,
  amostra: true
};

const CATALOGO = [
  ['cc2002', 'Código Civil', 'Lei nº 10.406, de 10 de janeiro de 2002'],
  ['cp1940', 'Código Penal', 'Decreto-Lei nº 2.848, de 7 de dezembro de 1940'],
  ['cpc2015', 'Código de Processo Civil', 'Lei nº 13.105, de 16 de março de 2015'],
  ['cpp1941', 'Código de Processo Penal', 'Decreto-Lei nº 3.689, de 3 de outubro de 1941'],
  ['ctn1966', 'Código Tributário Nacional', 'Lei nº 5.172, de 25 de outubro de 1966'],
  ['cdc1990', 'Código de Defesa do Consumidor', 'Lei nº 8.078, de 11 de setembro de 1990'],
  ['clt1943', 'Consolidação das Leis do Trabalho', 'Decreto-Lei nº 5.452, de 1º de maio de 1943'],
  ['eca1990', 'Estatuto da Criança e do Adolescente', 'Lei nº 8.069, de 13 de julho de 1990'],
  ['lep1984', 'Lei de Execução Penal', 'Lei nº 7.210, de 11 de julho de 1984'],
  ['lmp2006', 'Lei Maria da Penha', 'Lei nº 11.340, de 7 de agosto de 2006'],
  ['lgpd2018', 'Lei Geral de Proteção de Dados Pessoais', 'Lei nº 13.709, de 14 de agosto de 2018']
];

const fold = (s) => (s || '').normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase();
const STOP = new Set(['de', 'da', 'do', 'das', 'dos', 'e', 'a', 'o', 'as', 'os', 'que', 'um', 'uma', 'em', 'por', 'para', 'com', 'se', 'no', 'na', 'ao', 'quais', 'qual', 'sao', 'foi', 'mais', 'sem', 'art', 'artigo', 'inciso', 'inc', 'paragrafo', 'lei', 'cf', 'constituicao', 'federal']);
const ROMANOS = /^(?=[ivxlcdm]+$)m{0,3}(cm|cd|d?c{0,3})(xc|xl|l?x{0,3})(ix|iv|v?i{0,3})$/;

function resolver(ref) {
  const t = fold(ref);
  const ma = t.match(/\bart(?:igo)?\.?\s*(\d{1,4})/);
  if (!ma) return null;
  let id = `cf1988:art-${ma[1]}`;
  const resto = t.slice(ma.index + ma[0].length, ma.index + ma[0].length + 60);
  const mi = resto.match(/(?:inc(?:iso)?\.?\s*|,\s*)([ivxlcdm]{1,7})\b/);
  if (mi && ROMANOS.test(mi[1])) id += `:inc-${mi[1].toUpperCase()}`;
  const exato = DISPOSITIVOS.find((d) => d.id === id);
  const doArtigo = DISPOSITIVOS.find((d) => d.id === `cf1988:art-${ma[1]}`);
  const d = exato || doArtigo;
  return d ? { normaId: 'cf1988', dispositivoId: d.id, rotulo: d.rotulo, exato: Boolean(exato) } : null;
}

function buscar(q) {
  const termos = fold(q).split(/[^a-z0-9]+/).filter((w) => w.length > 2 && !STOP.has(w) && !ROMANOS.test(w) && !/^\d+$/.test(w));
  const raizes = termos.map((w) => w.slice(0, Math.max(4, w.length - 2)));
  const res = [];
  DISPOSITIVOS.forEach((d) => {
    const alvo = fold(`${d.texto} ${d.rotulo}`);
    const achados = raizes.filter((r) => alvo.includes(r));
    if (achados.length) {
      const encontrados = termos.filter((_, i) => achados.includes(raizes[i]));
      res.push({
        tipo: 'dispositivo', id: d.id, titulo: `Constituição Federal — ${d.rotulo}`, subtitulo: d.hierarquia,
        fonte: d.fonte, fonteNome: d.fonteNome, trecho: marcarFold(d.texto, raizes), termosEncontrados: encontrados,
        relevancia: `Termos da pesquisa presentes no texto legal: ${encontrados.join(', ')}.`,
        score: achados.length, urlOficial: URL_CF, documentoOficial: true, vigente: true, normaId: 'cf1988'
      });
    }
  });
  return res.sort((a, b) => b.score - a.score);
}

function marcarFold(texto, raizes) {
  // marca palavras cujo início (sem acento) corresponde a alguma raiz
  return texto.replace(/[\p{L}\d]+/gu, (w) => (raizes.some((r) => fold(w).startsWith(r)) ? `\u0001${w}\u0002` : w));
}

const vazio = (pagina = 0, tamanho = 20) => ({ total: 0, pagina, tamanho, itens: [], facetas: {} });

const FONTES = [
  ['planalto', 'Portal da Legislação — Planalto', 'Texto integral compilado das normas do catálogo (HTML oficial).'],
  ['lexml', 'LexML Brasil (SRU)', 'Metadados de normas federais, estaduais e municipais; novas publicações.'],
  ['stj-dados-abertos', 'STJ — Portal de Dados Abertos', 'Espelhos de acórdãos (ementa, tese, referências legislativas).'],
  ['djen', 'Diário de Justiça Eletrônico Nacional (DJEN)', 'Comunicações processuais publicadas: acórdãos, sentenças e decisões.'],
  ['datajud', 'DataJud — CNJ (API Pública)', 'Metadados processuais e movimentações de tribunais (exceto STF).'],
  ['stf', 'Supremo Tribunal Federal', 'Jurisprudência, súmulas vinculantes, repercussão geral e controle concentrado.']
].map(([id, nome, descricao]) => ({
  id, nome, descricao, estado: 'PRE_VISUALIZACAO', documentos: null, ultimaSincronizacao: null,
  observacao: 'Pré-visualização da interface: o backend não está conectado, nenhuma sincronização foi executada.'
}));

const AVISO = 'Pré-visualização da interface: esta área é preenchida pela sincronização com as fontes oficiais quando o backend está em execução.';

export async function previewHandle(method, path, params = {}) {
  await new Promise((r) => setTimeout(r, 220));
  const p = path.split('?')[0];

  if (p === '/inicio') {
    return { ultimasDecisoes: [], atualizacoesLegislativas: [], relevantesDoDia: [], sincronizacao: {}, aviso: AVISO };
  }
  if (p === '/busca') {
    const q = params.q || '';
    const ref = resolver(q);
    let itens = buscar(q);
    if (ref) {
      const alvo = DISPOSITIVOS.find((d) => d.id === ref.dispositivoId);
      itens = [{ tipo: 'dispositivo', id: alvo.id, titulo: `Constituição Federal — ${alvo.rotulo}`, subtitulo: alvo.hierarquia,
        fonte: alvo.fonte, fonteNome: alvo.fonteNome, trecho: alvo.texto, termosEncontrados: [],
        relevancia: 'Dispositivo correspondente ao artigo citado na pesquisa.', score: 99, urlOficial: URL_CF,
        documentoOficial: true, vigente: true, normaId: 'cf1988' }, ...itens.filter((i) => i.id !== alvo.id)];
    }
    const aba = params.aba || 'todos';
    const leg = ['todos', 'legislacao', 'constituicao'].includes(aba) ? itens : [];
    return {
      consulta: q,
      citacao: ref,
      dispositivoCitado: ref ? DISPOSITIVOS.find((d) => d.id === ref.dispositivoId) : null,
      abas: { legislacao: itens.length, constituicao: itens.length, jurisprudencia: 0, stf: 0, stj: 0, tjs: 0, sumulas: 0, temas: 0, controle: 0, doutrina: 0 },
      legislacao: { ...vazio(), total: leg.length, itens: leg.slice(0, aba === 'todos' ? 5 : 20) },
      normas: vazio(),
      jurisprudencia: vazio(),
      avisos: [AVISO + ' Na pré-visualização, a busca cobre apenas um trecho da Constituição (arts. 1º a 3º e parte do art. 5º).']
    };
  }
  if (p === '/legislacao/resolver') {
    const r = resolver(params.ref || '');
    if (!r) throw new ApiError('Dispositivo não encontrado na amostra de pré-visualização.', 404);
    return r;
  }
  if (p === '/legislacao/normas') {
    const itens = [
      { tipo: 'norma', id: 'cf1988', titulo: 'Constituição Federal (Constituição da República Federativa do Brasil de 1988)', subtitulo: 'Vigente', fonte: 'planalto', fonteNome: FONTE.fonteNome, data: '1988-10-05', trecho: 'Amostra de pré-visualização: arts. 1º a 3º e parte do art. 5º.', relevancia: 'Texto integral disponível no leitor.', urlOficial: URL_CF, documentoOficial: true, categoria: 'Constituição', normaId: 'cf1988' },
      ...CATALOGO.map(([id, ap, tit]) => ({ tipo: 'norma', id, titulo: `${ap} (${tit})`, fonte: 'planalto', fonteNome: FONTE.fonteNome, trecho: '', relevancia: 'Texto ainda não sincronizado nesta pré-visualização.', documentoOficial: true, normaId: id, semTexto: true }))
    ];
    return { total: itens.length, pagina: 0, tamanho: 50, itens, facetas: {} };
  }
  if (p.startsWith('/legislacao/normas/')) {
    const id = decodeURIComponent(p.split('/')[3]);
    if (id !== 'cf1988') throw new ApiError('Esta norma ainda não foi sincronizada (pré-visualização).', 404);
    return { norma: NORMA_CF, dispositivos: DISPOSITIVOS };
  }
  if (p.startsWith('/legislacao/dispositivos/') && p.endsWith('/historico')) return [];
  if (p.startsWith('/legislacao/dispositivos/')) {
    const id = decodeURIComponent(p.split('/')[3]);
    const d = DISPOSITIVOS.find((x) => x.id === id);
    if (!d) throw new ApiError('Dispositivo não encontrado.', 404);
    return d;
  }
  if (p === '/jurisprudencia' || p === '/controle') return { ...vazio(Number(params.pagina) || 0), aviso: AVISO };
  if (p === '/radar') return { ultimaSincronizacao: {}, itens: vazio(), aviso: AVISO };
  if (p === '/fontes') return FONTES;
  if (p === '/ia/perguntar') {
    return { habilitada: false, mensagem: 'A IA jurídica não está ativa na pré-visualização. Ela exige o backend em execução, a base indexada e a chave ANTHROPIC_API_KEY configurada.' };
  }
  if (p.startsWith('/processos/')) throw new ApiError('A consulta ao DataJud exige o backend em execução.', 503);
  throw new ApiError('Recurso indisponível na pré-visualização (requer backend e login).', 503);
}
