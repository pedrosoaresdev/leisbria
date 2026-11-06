import { Link, useSearchParams } from 'react-router-dom';
import Icon from '../components/Icon';
import { Badge, Empty, Notice, Pagination, Spinner, formatData, useAsync } from '../components/ui';
import { api } from '../api/client';
import { TRIBUNAIS, UFS } from '../config';

const TIPOS = [
  ['', 'Todos os eventos'], ['DECISAO_PUBLICADA', 'Decisão publicada'], ['JULGAMENTO', 'Julgamento registrado'],
  ['RECURSO_REPETITIVO', 'Recurso repetitivo'], ['REPERCUSSAO_GERAL', 'Repercussão geral'], ['SUMULA', 'Nova súmula'],
  ['CONTROLE_CONCENTRADO', 'Controle concentrado']
];

export default function Radar() {
  const [sp, setSp] = useSearchParams();
  const p = Object.fromEntries(sp.entries());
  const pagina = Number(p.pagina || 0);
  const { loading, data, error } = useAsync(() => api.get('/radar', { ...p, pagina, tamanho: 25 }), [sp.toString()]);
  const set = (k) => (e) => { const n = new URLSearchParams(sp); e.target.value ? n.set(k, e.target.value) : n.delete(k); n.delete('pagina'); setSp(n); };
  const itens = data?.itens;

  return (
    <div className="container page">
      <div className="page-title"><h1>Radar Jurídico</h1><p>Novas publicações e julgamentos captados das fontes oficiais integradas.</p></div>

      <div className="panel" style={{ marginBottom: 16 }}>
        <div className="panel-body grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))' }}>
          <div className="field"><label htmlFor="r-t">Tribunal</label>
            <select id="r-t" className="select" value={p.tribunal || ''} onChange={set('tribunal')}>
              <option value="">Todos</option>
              {[...TRIBUNAIS.superiores, ...TRIBUNAIS.federais, ...TRIBUNAIS.estaduais, ...TRIBUNAIS.trabalho].map((t) => <option key={t}>{t}</option>)}
            </select></div>
          <div className="field"><label htmlFor="r-uf">Estado</label>
            <select id="r-uf" className="select" value={p.uf || ''} onChange={set('uf')}><option value="">Todos</option>{UFS.map((u) => <option key={u}>{u}</option>)}</select></div>
          <div className="field"><label htmlFor="r-tp">Tipo</label>
            <select id="r-tp" className="select" value={p.tipo || ''} onChange={set('tipo')}>{TIPOS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></div>
          <div className="field"><label htmlFor="r-st">Situação</label>
            <select id="r-st" className="select" value={p.status || ''} onChange={set('status')}>
              <option value="">Publicadas e julgadas</option><option value="PUBLICADA">Somente publicadas</option><option value="JULGADA">Somente julgadas (sem publicação)</option>
            </select></div>
          <div className="field"><label htmlFor="r-as">Assunto</label><input id="r-as" className="input" defaultValue={p.assunto || ''} onBlur={set('assunto')} onKeyDown={(e) => e.key === 'Enter' && set('assunto')(e)} /></div>
          <div className="field"><label htmlFor="r-i">De</label><input id="r-i" type="date" className="input" value={p.inicio || ''} onChange={set('inicio')} /></div>
          <div className="field"><label htmlFor="r-f">Até</label><input id="r-f" type="date" className="input" value={p.fim || ''} onChange={set('fim')} /></div>
        </div>
        <div className="panel-foot row">
          <Icon name="clock" size={15} />
          {data?.ultimaSincronizacao && Object.keys(data.ultimaSincronizacao).length
            ? Object.entries(data.ultimaSincronizacao).map(([f, ts]) => <span key={f}><strong>{f}</strong>: {formatData(ts, true)}</span>)
            : <span>Nenhuma sincronização registrada ainda.</span>}
          <span className="spacer" /><Link to="/fontes">Status das fontes</Link>
        </div>
      </div>

      <Notice kind="info">
        <strong>Publicada</strong> = disponibilizada em diário oficial (DJEN ou DJe do tribunal). <strong>Julgada</strong> = julgamento registrado
        nas movimentações processuais (DataJud), ainda sem publicação localizada. A atualização depende da frequência de cada fonte; nada aqui é em tempo real absoluto.
      </Notice>

      <div className="stack" style={{ marginTop: 16 }}>
        {data?.aviso && <Notice kind="info">{data.aviso}</Notice>}
        {error && <Notice kind="error">{error.message}</Notice>}
        {loading ? <Spinner /> : itens && (itens.itens.length === 0
          ? <Empty icon="radar" title="Nenhum evento no período">Ajuste os filtros ou aguarde a próxima sincronização.</Empty>
          : itens.itens.map((ev) => (
            <article key={ev.id} className="card">
              <div className="card-meta">
                <Badge color="gray">{ev.tribunal}</Badge>
                {ev.statusPublicacao === 'PUBLICADA' ? <Badge color="green">Publicada</Badge> : <Badge color="amber">Julgada</Badge>}
                {ev.tipoEvento && <Badge color="navy">{ev.tipoEvento.replaceAll('_', ' ').toLowerCase()}</Badge>}
                <span className="xsmall muted">{ev.horarioDisponivel ? formatData(ev.dataEvento, true) : formatData(ev.dataEvento)}{!ev.horarioDisponivel && ' (horário não informado pela fonte)'}</span>
              </div>
              <h3 className="card-title">{ev.documentoId ? <Link to={`/decisao/${encodeURIComponent(ev.documentoId)}`}>{ev.titulo}</Link> : ev.titulo}</h3>
              <div className="card-sub">{[ev.numeroProcesso, ev.orgaoJulgador, ev.relator && `Rel. ${ev.relator}`].filter(Boolean).join(' · ')}</div>
              {(ev.ementa || ev.resumo) && <div className="card-text">{(ev.ementa || ev.resumo).slice(0, 600)}{(ev.ementa || ev.resumo).length > 600 ? '…' : ''}</div>}
              {ev.legislacaoRelacionada?.length > 0 && <div className="xsmall muted">Legislação relacionada: {ev.legislacaoRelacionada.join('; ')}</div>}
              <div className="card-actions">
                {ev.urlOficial && <a className="btn btn-ghost btn-sm" href={ev.urlOficial} target="_blank" rel="noreferrer noopener"><Icon name="external" size={15} /> Documento original</a>}
                <span className="spacer" /><span className="xsmall muted">Fonte: {ev.fonteNome}</span>
              </div>
            </article>
          )))}
        {itens && <Pagination pagina={pagina} total={itens.total} tamanho={itens.tamanho} onChange={(pg) => { const n = new URLSearchParams(sp); n.set('pagina', pg); setSp(n); }} />}
      </div>
    </div>
  );
}
