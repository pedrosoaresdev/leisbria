import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import JurisFilters, { filtrosParaParams } from '../components/JurisFilters';
import ResultCard from '../components/ResultCard';
import Icon from '../components/Icon';
import { Badge, Empty, Notice, Pagination, Spinner, formatData, useAsync } from '../components/ui';
import { api } from '../api/client';

const KEYS = ['tribunal', 'uf', 'orgaoJulgador', 'relator', 'numeroProcesso', 'classe', 'assunto', 'legislacao', 'tipoDecisao', 'julgamentoInicio', 'julgamentoFim', 'publicacaoInicio', 'publicacaoFim'];

function ConsultaProcesso() {
  const [numero, setNumero] = useState('');
  const [estado, setEstado] = useState({});
  const consultar = async (e) => {
    e.preventDefault();
    setEstado({ loading: true });
    try { setEstado({ data: await api.get(`/processos/${encodeURIComponent(numero.trim())}`) }); }
    catch (err) { setEstado({ erro: err.message }); }
  };
  const d = estado.data;
  return (
    <section className="panel" style={{ marginBottom: 16 }}>
      <div className="panel-head"><Icon name="database" /><h3>Consulta processual (DataJud/CNJ)</h3></div>
      <form className="panel-body row" style={{ flexWrap: 'nowrap' }} onSubmit={consultar}>
        <input className="input" inputMode="numeric" value={numero} onChange={(e) => setNumero(e.target.value)} placeholder="Número CNJ: 0000000-00.0000.0.00.0000" aria-label="Número do processo" />
        <button className="btn btn-primary" disabled={estado.loading}>Consultar</button>
      </form>
      <div className="panel-body" style={{ paddingTop: 0 }}>
        {estado.loading && <Spinner label="Consultando DataJud…" />}
        {estado.erro && <Notice kind="error">{estado.erro}</Notice>}
        {d && (
          <div className="stack">
            <div className="row"><Badge color="gray">{d.tribunal}</Badge><Badge color="navy">{d.grau}</Badge><strong>{d.classe}</strong></div>
            <dl className="kv">
              <dt>Processo</dt><dd>{d.numeroProcesso}</dd>
              <dt>Órgão julgador</dt><dd>{d.orgaoJulgador || '—'}</dd>
              <dt>Assuntos</dt><dd>{d.assuntos?.join('; ') || '—'}</dd>
              <dt>Ajuizamento</dt><dd>{formatData(d.dataAjuizamento)}</dd>
              <dt>Última atualização</dt><dd>{formatData(d.dataHoraUltimaAtualizacao, true)}</dd>
            </dl>
            <h4 style={{ margin: '8px 0 0' }}>Movimentações</h4>
            <ul className="list-plain small">
              {(d.movimentos || []).slice(0, 30).map((m, i) => <li key={i}><strong>{formatData(m.dataHora, true)}</strong> — {m.nome}{m.complemento ? ` (${m.complemento})` : ''}</li>)}
            </ul>
            <p className="xsmall muted">Fonte: API Pública do DataJud (CNJ) — somente metadados; processos sigilosos não são exibidos. Consultado em {formatData(d.consultadoEm, true)}.</p>
          </div>
        )}
      </div>
    </section>
  );
}

export default function Jurisprudencia() {
  const [sp, setSp] = useSearchParams();
  const q = sp.get('q') || '';
  const [texto, setTexto] = useState(q);
  const pagina = Number(sp.get('pagina') || 0);
  const ordenacao = sp.get('ordem') || 'relevancia';
  const filtros = Object.fromEntries(KEYS.map((k) => [k, sp.get(k)]).filter(([, v]) => v));
  const { loading, data, error } = useAsync(() => api.get('/jurisprudencia', { q, pagina, ordenacao, tamanho: 20, ...filtrosParaParams(filtros) }), [sp.toString()]);

  const set = (mud) => { const n = new URLSearchParams(sp); Object.entries(mud).forEach(([k, v]) => (v ? n.set(k, v) : n.delete(k))); if (!('pagina' in mud)) n.delete('pagina'); setSp(n); };

  return (
    <div className="container page">
      <div className="page-title"><h1>Jurisprudência</h1><p>Decisões de tribunais superiores, federais, estaduais, do trabalho, eleitorais e militares disponíveis nas fontes integradas.</p></div>
      <form className="search-compact" style={{ marginBottom: 16 }} onSubmit={(e) => { e.preventDefault(); set({ q: texto }); }}>
        <input value={texto} onChange={(e) => setTexto(e.target.value)} placeholder="Palavras-chave, tese, ementa…" aria-label="Pesquisar jurisprudência" />
        <button aria-label="Pesquisar"><Icon name="search" /></button>
      </form>
      <ConsultaProcesso />
      <div className="results-layout">
        <JurisFilters key={JSON.stringify(filtros)} valores={filtros} facetas={data?.facetas}
          onAplicar={(f) => { const n = new URLSearchParams(); if (q) n.set('q', q); Object.entries(f).forEach(([k, v]) => v && n.set(k, v)); setSp(n); }} />
        <div className="stack" style={{ minWidth: 0 }}>
          <div className="toolbar">
            <span>{data ? `${data.total.toLocaleString('pt-BR')} decisão(ões)` : ''}</span>
            <select className="select" style={{ width: 'auto', minHeight: 34, padding: '4px 8px' }} value={ordenacao} onChange={(e) => set({ ordem: e.target.value })} aria-label="Ordenação">
              <option value="relevancia">Relevância</option><option value="data">Mais recentes</option><option value="tribunal">Tribunal</option>
            </select>
          </div>
          {data?.aviso && <Notice kind="info">{data.aviso}</Notice>}
          {error && <Notice kind="error">{error.message}</Notice>}
          {loading ? <Spinner /> : data && (data.itens.length
            ? data.itens.map((r) => <ResultCard key={r.id} r={r} />)
            : <Empty icon="scale" title="Nenhuma decisão encontrada">A base contém apenas o que já foi sincronizado das fontes oficiais. Veja a cobertura em “Fontes”.</Empty>)}
          {data && <Pagination pagina={pagina} total={data.total} tamanho={data.tamanho} onChange={(p) => set({ pagina: String(p) })} />}
        </div>
      </div>
    </div>
  );
}
