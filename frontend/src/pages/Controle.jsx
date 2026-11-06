import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import ResultCard from '../components/ResultCard';
import Icon from '../components/Icon';
import { Empty, Notice, Pagination, Spinner, useAsync } from '../components/ui';
import { api } from '../api/client';
import { CLASSES_CONTROLE, UFS } from '../config';

export default function Controle() {
  const [sp, setSp] = useSearchParams();
  const p = Object.fromEntries(sp.entries());
  const [form, setForm] = useState(p);
  const pagina = Number(p.pagina || 0);
  const { loading, data, error } = useAsync(() => api.get('/controle', { ...p, pagina, tamanho: 20 }), [sp.toString()]);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const aplicar = (e) => { e.preventDefault(); const n = new URLSearchParams(); Object.entries(form).forEach(([k, v]) => v && k !== 'pagina' && n.set(k, v)); setSp(n); };
  const classe = p.classe || '';

  return (
    <div className="container page">
      <div className="page-title"><h1>Controle de Constitucionalidade</h1><p>ADI, ADC, ADPF, ADO, mandados de injunção, súmulas vinculantes, repercussão geral e recursos repetitivos.</p></div>

      <div className="tabs" role="tablist" style={{ marginBottom: 16 }}>
        <button className={`tab ${!classe ? 'on' : ''}`} onClick={() => { setForm({ ...form, classe: '' }); const n = new URLSearchParams(sp); n.delete('classe'); setSp(n); }}>Todas</button>
        {CLASSES_CONTROLE.map((c) => (
          <button key={c.id} title={c.nome} className={`tab ${classe === c.id ? 'on' : ''}`}
            onClick={() => { setForm({ ...form, classe: c.id }); const n = new URLSearchParams(sp); n.set('classe', c.id); n.delete('pagina'); setSp(n); }}>{c.id}</button>
        ))}
      </div>

      <form className="panel" onSubmit={aplicar} style={{ marginBottom: 16 }}>
        <div className="panel-body grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))' }}>
          <div className="field"><label htmlFor="c-n">Número da ação</label><input id="c-n" className="input" inputMode="numeric" value={form.numero || ''} onChange={set('numero')} placeholder="Ex.: 4277" /></div>
          <div className="field"><label htmlFor="c-r">Relator</label><input id="c-r" className="input" value={form.relator || ''} onChange={set('relator')} /></div>
          <div className="field"><label htmlFor="c-d">Dispositivo questionado</label><input id="c-d" className="input" value={form.dispositivo || ''} onChange={set('dispositivo')} placeholder="Ex.: art. 1.723 do CC" /></div>
          <div className="field"><label htmlFor="c-l">Lei questionada</label><input id="c-l" className="input" value={form.lei || ''} onChange={set('lei')} placeholder="Ex.: Lei 13.964/2019" /></div>
          <div className="field"><label htmlFor="c-a">Assunto</label><input id="c-a" className="input" value={form.assunto || ''} onChange={set('assunto')} /></div>
          <div className="field"><label htmlFor="c-uf">Estado</label><select id="c-uf" className="select" value={form.uf || ''} onChange={set('uf')}><option value="">Todos</option>{UFS.map((u) => <option key={u}>{u}</option>)}</select></div>
          <div className="field"><label htmlFor="c-res">Resultado</label><input id="c-res" className="input" value={form.resultado || ''} onChange={set('resultado')} placeholder="Ex.: procedente" /></div>
          <div className="field"><label htmlFor="c-i">Julgado a partir de</label><input id="c-i" type="date" className="input" value={form.inicio || ''} onChange={set('inicio')} /></div>
          <div className="field"><label htmlFor="c-f">Julgado até</label><input id="c-f" type="date" className="input" value={form.fim || ''} onChange={set('fim')} /></div>
        </div>
        <div className="panel-foot row"><button className="btn btn-primary btn-sm"><Icon name="search" size={15} /> Pesquisar</button></div>
      </form>

      <div className="stack">
        {data?.aviso && <Notice kind="info">{data.aviso}</Notice>}
        {error && <Notice kind="error">{error.message}</Notice>}
        {loading ? <Spinner /> : data && (data.itens.length
          ? data.itens.map((r) => <ResultCard key={r.id} r={r} />)
          : <Empty icon="columns" title="Nenhuma ação encontrada">O controle concentrado do STF depende da integração com o Supremo, que ainda não possui API pública de jurisprudência — veja “Fontes”.</Empty>)}
        {data && <Pagination pagina={pagina} total={data.total} tamanho={data.tamanho} onChange={(pg) => { const n = new URLSearchParams(sp); n.set('pagina', pg); setSp(n); }} />}
      </div>
    </div>
  );
}
