import { useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import Icon from '../components/Icon';
import { Badge, Empty, Notice, Spinner, formatData, useAsync, useToast } from '../components/ui';
import { useAuth } from '../auth/AuthContext';
import { api } from '../api/client';
import { linkInterno } from '../components/ResultCard';
import { TRIBUNAIS } from '../config';

const ABAS = [['favoritos', 'Favoritos'], ['pesquisas', 'Pesquisas salvas'], ['historico', 'Histórico'], ['alertas', 'Alertas']];

function Favoritos() {
  const toast = useToast();
  const [rev, setRev] = useState(0);
  const [pasta, setPasta] = useState('');
  const [novaPasta, setNovaPasta] = useState('');
  const pastas = useAsync(() => api.get('/me/pastas'), [rev]);
  const favs = useAsync(() => api.get('/me/favoritos', { pastaId: pasta }), [rev, pasta]);

  const criarPasta = async (e) => {
    e.preventDefault();
    if (!novaPasta.trim()) return;
    try { await api.post('/me/pastas', { nome: novaPasta.trim() }); setNovaPasta(''); setRev((r) => r + 1); } catch (err) { toast(err.message); }
  };
  const mover = async (f, pastaId) => { await api.put(`/me/favoritos/${f.id}`, { pastaId: pastaId || null }); setRev((r) => r + 1); };
  const remover = async (f) => { await api.del(`/me/favoritos/${f.id}`); setRev((r) => r + 1); toast('Removido'); };
  const removerPasta = async (p) => { await api.del(`/me/pastas/${p.id}`); if (pasta === p.id) setPasta(''); setRev((r) => r + 1); };

  return (
    <div className="results-layout">
      <aside className="panel">
        <div className="panel-head"><Icon name="folder" /><h3>Pastas de pesquisa</h3></div>
        <div className="panel-body stack">
          <ul className="facet-list">
            <li><button className={!pasta ? 'on' : ''} onClick={() => setPasta('')}>Todos os favoritos</button></li>
            {pastas.data?.map((p) => (
              <li key={p.id} className="row" style={{ flexWrap: 'nowrap' }}>
                <button className={pasta === p.id ? 'on' : ''} onClick={() => setPasta(p.id)}>{p.nome}</button>
                <button className="btn-link" aria-label={`Excluir pasta ${p.nome}`} onClick={() => removerPasta(p)}><Icon name="trash" size={15} /></button>
              </li>
            ))}
          </ul>
          <form className="row" style={{ flexWrap: 'nowrap' }} onSubmit={criarPasta}>
            <input className="input" placeholder="Nova pasta" value={novaPasta} onChange={(e) => setNovaPasta(e.target.value)} maxLength={60} />
            <button className="btn btn-primary btn-sm">Criar</button>
          </form>
        </div>
      </aside>
      <div className="stack">
        {favs.loading ? <Spinner /> : favs.error ? <Notice kind="error">{favs.error.message}</Notice> : favs.data?.length === 0
          ? <Empty icon="star" title="Nenhum favorito aqui">Use a estrela nos resultados, artigos e decisões para salvar.</Empty>
          : favs.data.map((f) => (
            <article key={f.id} className="card">
              <div className="row"><Badge color="navy">{f.tipo}</Badge><span className="xsmall muted">Salvo em {formatData(f.criadoEm)}</span></div>
              <Link className="card-title" to={linkInterno({ tipo: f.tipo, id: f.refId, normaId: f.normaId })}>{f.titulo}</Link>
              <div className="card-actions">
                <select className="select" style={{ width: 'auto', minHeight: 32, padding: '2px 8px' }} value={f.pastaId || ''} onChange={(e) => mover(f, e.target.value)} aria-label="Mover para pasta">
                  <option value="">Sem pasta</option>
                  {pastas.data?.map((p) => <option key={p.id} value={p.id}>{p.nome}</option>)}
                </select>
                {f.url && <a className="btn btn-ghost btn-sm" href={f.url} target="_blank" rel="noreferrer noopener"><Icon name="external" size={15} /> Fonte</a>}
                <span className="spacer" />
                <button className="btn btn-danger btn-sm" onClick={() => remover(f)}><Icon name="trash" size={15} /> Remover</button>
              </div>
            </article>
          ))}
      </div>
    </div>
  );
}

function linkPesquisa(p) {
  const usp = new URLSearchParams({ q: p.consulta || '' });
  if (p.aba && p.aba !== 'todos') usp.set('aba', p.aba);
  Object.entries(p.filtros || {}).forEach(([k, v]) => v && usp.set(k, v));
  return `/busca?${usp}`;
}

function Pesquisas() {
  const [rev, setRev] = useState(0);
  const { loading, data, error } = useAsync(() => api.get('/me/pesquisas'), [rev]);
  if (loading) return <Spinner />;
  if (error) return <Notice kind="error">{error.message}</Notice>;
  if (!data.length) return <Empty title="Nenhuma pesquisa salva">Use “Salvar pesquisa” na página de resultados.</Empty>;
  return (
    <ul className="list-plain panel panel-body">
      {data.map((p) => (
        <li key={p.id} className="row">
          <div style={{ flex: 1, minWidth: 0 }}>
            <Link to={linkPesquisa(p)} style={{ fontWeight: 600 }}>{p.nome || p.consulta}</Link>
            <div className="xsmall muted">{p.aba && p.aba !== 'todos' ? `Aba: ${p.aba} · ` : ''}{formatData(p.criadoEm, true)}</div>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={async () => { await api.del(`/me/pesquisas/${p.id}`); setRev((r) => r + 1); }} aria-label="Excluir"><Icon name="trash" size={15} /></button>
        </li>
      ))}
    </ul>
  );
}

function Historico() {
  const [rev, setRev] = useState(0);
  const { loading, data, error } = useAsync(() => api.get('/me/historico'), [rev]);
  if (loading) return <Spinner />;
  if (error) return <Notice kind="error">{error.message}</Notice>;
  return (
    <div className="panel">
      <div className="panel-head"><h3>Últimas pesquisas</h3><span className="spacer" />
        {data.length > 0 && <button className="btn btn-ghost btn-sm" onClick={async () => { await api.del('/me/historico'); setRev((r) => r + 1); }}>Limpar histórico</button>}
      </div>
      {data.length === 0 ? <Empty icon="history" title="Histórico vazio" /> : (
        <ul className="list-plain panel-body">
          {data.map((h) => (
            <li key={h.id}><Link to={linkPesquisa(h)}>{h.consulta}</Link>
              <div className="xsmall muted">{formatData(h.criadoEm, true)}{h.totalResultados != null ? ` · ${h.totalResultados} resultado(s)` : ''}</div></li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Alertas() {
  const toast = useToast();
  const [rev, setRev] = useState(0);
  const [form, setForm] = useState({ termos: '', tribunal: '' });
  const { loading, data, error } = useAsync(() => api.get('/me/alertas'), [rev]);
  const criar = async (e) => {
    e.preventDefault();
    try {
      await api.post('/me/alertas', { termos: form.termos.trim(), tribunais: form.tribunal ? [form.tribunal] : [] });
      setForm({ termos: '', tribunal: '' }); setRev((r) => r + 1); toast('Alerta criado');
    } catch (err) { toast(err.message); }
  };
  return (
    <div className="stack">
      <form className="panel" onSubmit={criar}>
        <div className="panel-head"><Icon name="bell" /><h3>Novo alerta</h3></div>
        <div className="panel-body grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
          <div className="field"><label htmlFor="al-t">Assunto ou termos</label><input id="al-t" className="input" required minLength={3} maxLength={200} value={form.termos} onChange={(e) => setForm({ ...form, termos: e.target.value })} placeholder="Ex.: audiência de custódia" /></div>
          <div className="field"><label htmlFor="al-tr">Tribunal (opcional)</label>
            <select id="al-tr" className="select" value={form.tribunal} onChange={(e) => setForm({ ...form, tribunal: e.target.value })}>
              <option value="">Todos</option>{[...TRIBUNAIS.superiores, ...TRIBUNAIS.federais, ...TRIBUNAIS.estaduais].map((t) => <option key={t}>{t}</option>)}
            </select></div>
        </div>
        <div className="panel-foot"><button className="btn btn-primary btn-sm">Criar alerta</button> <span className="xsmall">Você recebe uma notificação quando o Radar captar uma nova decisão correspondente.</span></div>
      </form>
      {loading ? <Spinner /> : error ? <Notice kind="error">{error.message}</Notice> : data.length === 0 ? <Empty icon="bell" title="Nenhum alerta" /> : (
        <ul className="list-plain panel panel-body">
          {data.map((a) => (
            <li key={a.id} className="row">
              <div style={{ flex: 1 }}><strong>{a.termos}</strong> {a.tribunais?.map((t) => <Badge key={t} color="gray">{t}</Badge>)}
                <div className="xsmall muted">Última verificação: {formatData(a.ultimaVerificacao, true)}</div></div>
              <label className="checkbox small"><input type="checkbox" checked={a.ativo} onChange={async (e) => { await api.put(`/me/alertas/${a.id}`, { ativo: e.target.checked }); setRev((r) => r + 1); }} /> Ativo</label>
              <button className="btn btn-ghost btn-sm" aria-label="Excluir alerta" onClick={async () => { await api.del(`/me/alertas/${a.id}`); setRev((r) => r + 1); }}><Icon name="trash" size={15} /></button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default function MeusItens() {
  const { user, carregando, disponivel } = useAuth();
  const [aba, setAba] = useState('favoritos');
  if (!disponivel) return <div className="container page"><Notice kind="warn">Favoritos, pastas, histórico e alertas exigem login (indisponível na pré-visualização).</Notice></div>;
  if (carregando) return <div className="container page"><Spinner /></div>;
  if (!user) return <Navigate to="/entrar" replace />;
  return (
    <div className="container page">
      <div className="page-title"><h1>Minha área de pesquisa</h1></div>
      <div className="tabs" role="tablist" style={{ marginBottom: 16 }}>
        {ABAS.map(([id, r]) => <button key={id} className={`tab ${aba === id ? 'on' : ''}`} onClick={() => setAba(id)}>{r}</button>)}
      </div>
      {aba === 'favoritos' && <Favoritos />}
      {aba === 'pesquisas' && <Pesquisas />}
      {aba === 'historico' && <Historico />}
      {aba === 'alertas' && <Alertas />}
    </div>
  );
}
