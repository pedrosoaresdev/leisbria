import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Icon from '../components/Icon';
import { Badge, Empty, Notice, Pagination, Spinner, useAsync } from '../components/ui';
import { api } from '../api/client';

const TIPOS = ['', 'Constituição', 'Emenda Constitucional', 'Lei Complementar', 'Lei', 'Decreto-Lei', 'Decreto', 'Medida Provisória', 'Resolução', 'Portaria', 'Instrução Normativa', 'Tratado'];

export default function Legislacao() {
  const nav = useNavigate();
  const [q, setQ] = useState('');
  const [busca, setBusca] = useState('');
  const [tipo, setTipo] = useState('');
  const [pagina, setPagina] = useState(0);
  const [ref, setRef] = useState('');
  const [erroRef, setErroRef] = useState(null);
  const { loading, data, error } = useAsync(() => api.get('/legislacao/normas', { q: busca, tipo, pagina, tamanho: 30 }), [busca, tipo, pagina]);

  const abrirRef = async (e) => {
    e.preventDefault();
    setErroRef(null);
    try {
      const r = await api.get('/legislacao/resolver', { ref });
      nav(`/norma/${encodeURIComponent(r.normaId)}?d=${encodeURIComponent(r.dispositivoId)}`);
    } catch (err) { setErroRef(err.message); }
  };

  return (
    <div className="container page">
      <div className="page-title"><h1>Legislação</h1><p>Constituição, códigos, leis, decretos e demais normas, com texto da fonte oficial.</p></div>

      <div className="two-col" style={{ marginBottom: 16 }}>
        <form className="panel panel-body stack" onSubmit={abrirRef}>
          <label htmlFor="ref" style={{ fontWeight: 600 }}>Ir direto a um dispositivo</label>
          <div className="row" style={{ flexWrap: 'nowrap' }}>
            <input id="ref" className="input" value={ref} onChange={(e) => setRef(e.target.value)} placeholder="Ex.: art. 5º, LXIII · art. 310 do CPP · art. 33 da Lei 11.343/2006" />
            <button className="btn btn-primary">Abrir</button>
          </div>
          {erroRef && <div className="error-text">{erroRef}</div>}
          <span className="hint">Sem a norma indicada, considera-se a Constituição Federal.</span>
        </form>
        <form className="panel panel-body stack" onSubmit={(e) => { e.preventDefault(); setPagina(0); setBusca(q); }}>
          <label htmlFor="qn" style={{ fontWeight: 600 }}>Procurar norma por número, nome ou assunto</label>
          <div className="row" style={{ flexWrap: 'nowrap' }}>
            <input id="qn" className="input" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Ex.: 13.709 · Lei de Licitações · improbidade" />
            <select className="select" style={{ width: 'auto' }} value={tipo} onChange={(e) => { setTipo(e.target.value); setPagina(0); }} aria-label="Tipo de norma">
              {TIPOS.map((t) => <option key={t} value={t}>{t || 'Todos os tipos'}</option>)}
            </select>
            <button className="btn btn-primary" aria-label="Procurar"><Icon name="search" /></button>
          </div>
          <span className="hint">Para pesquisar no texto dos artigos, use a <Link to="/busca?aba=legislacao">pesquisa de legislação</Link>.</span>
        </form>
      </div>

      {error && <Notice kind="error">{error.message}</Notice>}
      {loading ? <Spinner /> : data && (
        data.itens.length === 0 ? <Empty title="Nenhuma norma encontrada" /> : (
          <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))' }}>
            {data.itens.map((n) => (
              <Link key={n.id} to={`/norma/${encodeURIComponent(n.id)}`} className="card" style={{ color: 'inherit', textDecoration: 'none' }}>
                <div className="row">{n.categoria && <Badge color="navy">{n.categoria}</Badge>}{n.subtitulo && <Badge color="green">{n.subtitulo}</Badge>}{n.semTexto && <Badge color="gray">Sem texto sincronizado</Badge>}</div>
                <div className="card-title">{n.titulo}</div>
                {n.trecho && <div className="small muted">{n.trecho}</div>}
                <div className="xsmall muted" style={{ marginTop: 'auto' }}>{n.relevancia}</div>
              </Link>
            ))}
          </div>
        )
      )}
      {data && <Pagination pagina={pagina} total={data.total} tamanho={data.tamanho} onChange={setPagina} />}
    </div>
  );
}
