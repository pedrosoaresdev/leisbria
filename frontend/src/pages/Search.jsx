import { useMemo } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import SearchBox from '../components/SearchBox';
import ResultCard from '../components/ResultCard';
import AiPanel from '../components/AiPanel';
import JurisFilters, { filtrosParaParams } from '../components/JurisFilters';
import Icon from '../components/Icon';
import { Empty, Notice, Pagination, Spinner, useAsync, useToast } from '../components/ui';
import { api } from '../api/client';
import { ABAS, ABAS_LEGISLACAO } from '../config';
import { useAuth } from '../auth/AuthContext';

const FILTRO_KEYS = ['tribunal', 'uf', 'orgaoJulgador', 'relator', 'numeroProcesso', 'classe', 'assunto', 'legislacao',
  'tipoDecisao', 'julgamentoInicio', 'julgamentoFim', 'publicacaoInicio', 'publicacaoFim'];

export default function Search() {
  const [sp, setSp] = useSearchParams();
  const nav = useNavigate();
  const toast = useToast();
  const { user } = useAuth();
  const q = sp.get('q') || '';
  const aba = sp.get('aba') || 'todos';
  const ia = sp.get('ia') === '1';
  const ordenacao = sp.get('ordem') || 'relevancia';
  const pagina = Number(sp.get('pagina') || 0);
  const filtros = useMemo(() => Object.fromEntries(FILTRO_KEYS.map((k) => [k, sp.get(k)]).filter(([, v]) => v)), [sp]);

  const { loading, data, error } = useAsync(
    () => api.get('/busca', { q, aba, ordenacao, pagina, tamanho: aba === 'todos' ? 5 : 20, ...filtrosParaParams(filtros) }),
    [q, aba, ordenacao, pagina, JSON.stringify(filtros)]
  );

  const atualizar = (mud) => {
    const n = new URLSearchParams(sp);
    Object.entries(mud).forEach(([k, v]) => (v === null || v === undefined || v === '' ? n.delete(k) : n.set(k, v)));
    if (!('pagina' in mud)) n.delete('pagina');
    setSp(n);
  };

  const aplicarFiltros = (f) => {
    const n = new URLSearchParams(sp);
    FILTRO_KEYS.forEach((k) => n.delete(k));
    Object.entries(f).forEach(([k, v]) => v && n.set(k, v));
    n.delete('pagina');
    setSp(n);
  };

  const salvarPesquisa = async () => {
    if (!user) { nav('/entrar'); return; }
    try {
      await api.post('/me/pesquisas', { consulta: q, aba, filtros });
      toast('Pesquisa salva');
    } catch (e) { toast(e.message); }
  };

  const ehJuris = !ABAS_LEGISLACAO.includes(aba) && aba !== 'todos' && aba !== 'doutrina';
  const lista = aba === 'todos' ? null : (ABAS_LEGISLACAO.includes(aba) ? data?.legislacao : data?.jurisprudencia);

  return (
    <div className="container page">
      <div style={{ marginBottom: 16 }}><SearchBox inicial={q} modoInicial={ia ? 'pergunta' : 'termos'} /></div>

      {data?.dispositivoCitado && (
        <div className="card" style={{ borderLeft: '4px solid var(--gold-500)', marginBottom: 16 }}>
          <div className="row small muted"><Icon name="check" size={16} /> Dispositivo identificado na pesquisa</div>
          <h2 className="card-title" style={{ fontSize: '1.15rem' }}>{data.dispositivoCitado.normaTitulo} — {data.dispositivoCitado.rotulo}</h2>
          <div className="literal">{data.dispositivoCitado.texto}</div>
          {data.citacao && data.citacao.exato === false && <Notice kind="warn">O trecho exato citado não foi localizado; exibindo o artigo correspondente.</Notice>}
          <div className="card-actions">
            <Link className="btn btn-primary btn-sm" to={`/norma/${encodeURIComponent(data.dispositivoCitado.normaId)}?d=${encodeURIComponent(data.dispositivoCitado.id)}`}>Abrir no leitor</Link>
            {data.dispositivoCitado.urlOficial && <a className="btn btn-ghost btn-sm" href={data.dispositivoCitado.urlOficial} target="_blank" rel="noreferrer noopener"><Icon name="external" size={16} /> Fonte oficial</a>}
          </div>
        </div>
      )}

      <div className="tabs" role="tablist" aria-label="Categorias de resultado">
        {ABAS.map((a) => (
          <button key={a.id} role="tab" aria-selected={aba === a.id} className={`tab ${aba === a.id ? 'on' : ''}`}
            onClick={() => atualizar({ aba: a.id === 'todos' ? null : a.id })}>
            {a.rotulo}
            {data?.abas && a.id !== 'todos' && <span className="count">{(data.abas[a.id] ?? 0).toLocaleString('pt-BR')}</span>}
          </button>
        ))}
      </div>

      <div className={`results-layout ${ehJuris ? '' : 'no-filters'}`}>
        {ehJuris && <JurisFilters key={JSON.stringify(filtros)} valores={filtros} onAplicar={aplicarFiltros} facetas={data?.jurisprudencia?.facetas} />}
        <div className="stack" style={{ minWidth: 0 }}>
          {ia && aba === 'todos' && q && <AiPanel pergunta={q} />}

          <div className="toolbar">
            <span>{loading ? 'Pesquisando…' : data && aba !== 'todos' && lista ? `${lista.total.toLocaleString('pt-BR')} resultado(s)` : ''}</span>
            <div className="row">
              <label htmlFor="ordem" className="small">Ordenar por</label>
              <select id="ordem" className="select" style={{ width: 'auto', minHeight: 34, padding: '4px 8px' }} value={ordenacao} onChange={(e) => atualizar({ ordem: e.target.value })}>
                <option value="relevancia">Relevância</option>
                <option value="data">Data</option>
                <option value="tribunal">Tribunal</option>
              </select>
              <button className="btn btn-ghost btn-sm" onClick={salvarPesquisa}><Icon name="star" size={15} /> Salvar pesquisa</button>
            </div>
          </div>

          {data?.avisos?.map((a, i) => <Notice key={i} kind="info">{a}</Notice>)}
          {error && <Notice kind="error">{error.message}</Notice>}
          {loading && <Spinner label="Consultando legislação e jurisprudência…" />}

          {!loading && data && aba === 'todos' && (
            <>
              <Secao titulo="Legislação" total={data.abas?.legislacao} onVer={() => atualizar({ aba: 'legislacao' })} itens={data.legislacao?.itens}
                vazio="Nenhum dispositivo legal encontrado para esta pesquisa." />
              {data.normas?.itens?.length > 0 && <Secao titulo="Normas" itens={data.normas.itens} />}
              <Secao titulo="Jurisprudência" total={data.abas?.jurisprudencia} onVer={() => atualizar({ aba: 'jurisprudencia' })} itens={data.jurisprudencia?.itens}
                vazio="Nenhuma decisão encontrada na base sincronizada." />
            </>
          )}

          {!loading && data && aba === 'doutrina' && (
            <Empty icon="book" title="Nenhuma fonte de doutrina licenciada">
              A plataforma só exibe doutrina de fontes com licença de uso. Nenhuma está configurada no momento.
            </Empty>
          )}

          {!loading && lista && (
            <>
              {lista.itens.length === 0
                ? <Empty title="Nenhum resultado nesta categoria">Tente outros termos ou remova filtros.</Empty>
                : lista.itens.map((r) => <ResultCard key={r.id} r={r} />)}
              <Pagination pagina={pagina} total={lista.total} tamanho={lista.tamanho} onChange={(p) => atualizar({ pagina: p })} />
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function Secao({ titulo, total, itens, onVer, vazio }) {
  return (
    <section className="stack" style={{ marginBottom: 12 }}>
      <div className="row"><h2 style={{ margin: 0 }}>{titulo}</h2>
        {typeof total === 'number' && <span className="muted small">{total.toLocaleString('pt-BR')} encontrado(s)</span>}
        <span className="spacer" />{onVer && total > 0 && <button className="btn-link small" onClick={onVer}>Ver todos →</button>}
      </div>
      {itens?.length ? itens.map((r) => <ResultCard key={r.id} r={r} />) : <div className="card muted small">{vazio}</div>}
    </section>
  );
}
