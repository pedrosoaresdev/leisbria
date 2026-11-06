import { useState } from 'react';
import Icon from './Icon';
import { TRIBUNAIS, UFS } from '../config';

const GRUPOS = [
  ['Superiores', TRIBUNAIS.superiores], ['Federais', TRIBUNAIS.federais], ['Estaduais', TRIBUNAIS.estaduais],
  ['Trabalho', TRIBUNAIS.trabalho], ['Eleitorais', TRIBUNAIS.eleitorais], ['Militares', TRIBUNAIS.militares]
];

/**
 * Filtros de jurisprudência. "valores" é o objeto de filtros vigente; "onAplicar" recebe o novo objeto.
 * As facetas (contagens do backend) aparecem ao lado de cada tribunal quando disponíveis.
 */
export default function JurisFilters({ valores, onAplicar, facetas = {}, extras = null }) {
  const [v, setV] = useState(valores);
  const [aberto, setAberto] = useState(false);
  const set = (k) => (e) => setV({ ...v, [k]: e.target.value });
  const contagem = Object.fromEntries((facetas.tribunal || []).map((f) => [f.valor, f.quantidade]));

  const aplicar = (e) => { e.preventDefault(); onAplicar(v); setAberto(false); };
  const limpar = () => { const z = {}; setV(z); onAplicar(z); };

  return (
    <aside className="filters">
      <button className="btn btn-ghost filters-toggle" onClick={() => setAberto(!aberto)} aria-expanded={aberto}>
        <Icon name="filter" size={16} /> Filtros
      </button>
      <form className={`panel filters-panel ${aberto ? 'open' : ''}`} onSubmit={aplicar}>
        <div className="panel-body stack">
          {extras}
          <div className="field">
            <label htmlFor="f-trib">Tribunal</label>
            <select id="f-trib" className="select" value={v.tribunal || ''} onChange={set('tribunal')}>
              <option value="">Todos</option>
              {GRUPOS.map(([g, lista]) => (
                <optgroup key={g} label={g}>
                  {lista.map((t) => <option key={t} value={t}>{t}{contagem[t] ? ` (${contagem[t]})` : ''}</option>)}
                </optgroup>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="f-uf">Estado</label>
            <select id="f-uf" className="select" value={v.uf || ''} onChange={set('uf')}>
              <option value="">Todos</option>
              {UFS.map((u) => <option key={u}>{u}</option>)}
            </select>
          </div>
          <div className="field"><label htmlFor="f-org">Câmara / Turma / Órgão</label><input id="f-org" className="input" value={v.orgaoJulgador || ''} onChange={set('orgaoJulgador')} /></div>
          <div className="field"><label htmlFor="f-rel">Relator</label><input id="f-rel" className="input" value={v.relator || ''} onChange={set('relator')} /></div>
          <div className="field"><label htmlFor="f-num">Número do processo</label><input id="f-num" className="input" inputMode="numeric" placeholder="0000000-00.0000.0.00.0000" value={v.numeroProcesso || ''} onChange={set('numeroProcesso')} /></div>
          <div className="field"><label htmlFor="f-cls">Classe processual</label><input id="f-cls" className="input" placeholder="Ex.: Habeas Corpus" value={v.classe || ''} onChange={set('classe')} /></div>
          <div className="field"><label htmlFor="f-ass">Assunto</label><input id="f-ass" className="input" value={v.assunto || ''} onChange={set('assunto')} /></div>
          <div className="field"><label htmlFor="f-leg">Legislação relacionada</label><input id="f-leg" className="input" placeholder="Ex.: art. 310 CPP" value={v.legislacao || ''} onChange={set('legislacao')} /></div>
          <div className="field">
            <label htmlFor="f-tipo">Tipo de decisão</label>
            <select id="f-tipo" className="select" value={v.tipoDecisao || ''} onChange={set('tipoDecisao')}>
              <option value="">Todos</option>
              <option value="ACORDAO">Acórdão</option>
              <option value="DECISAO_MONOCRATICA">Decisão monocrática</option>
              <option value="SENTENCA">Sentença</option>
              <option value="SUMULA">Súmula</option>
            </select>
          </div>
          <fieldset className="field" style={{ border: 0, padding: 0, margin: 0 }}>
            <label>Data do julgamento</label>
            <div className="row" style={{ flexWrap: 'nowrap' }}>
              <input type="date" className="input" aria-label="Julgamento a partir de" value={v.julgamentoInicio || ''} onChange={set('julgamentoInicio')} />
              <input type="date" className="input" aria-label="Julgamento até" value={v.julgamentoFim || ''} onChange={set('julgamentoFim')} />
            </div>
          </fieldset>
          <fieldset className="field" style={{ border: 0, padding: 0, margin: 0 }}>
            <label>Data da publicação</label>
            <div className="row" style={{ flexWrap: 'nowrap' }}>
              <input type="date" className="input" aria-label="Publicação a partir de" value={v.publicacaoInicio || ''} onChange={set('publicacaoInicio')} />
              <input type="date" className="input" aria-label="Publicação até" value={v.publicacaoFim || ''} onChange={set('publicacaoFim')} />
            </div>
          </fieldset>
          <div className="row">
            <button className="btn btn-primary" type="submit">Aplicar</button>
            <button className="btn btn-ghost" type="button" onClick={limpar}>Limpar</button>
          </div>
        </div>
      </form>
    </aside>
  );
}

/** Converte os filtros da interface nos parâmetros da API. */
export function filtrosParaParams(f) {
  return {
    tribunal: f.tribunal, uf: f.uf, orgaoJulgador: f.orgaoJulgador, relator: f.relator,
    numeroProcesso: f.numeroProcesso, classe: f.classe, assunto: f.assunto, legislacao: f.legislacao,
    tipoDecisao: f.tipoDecisao, julgamentoInicio: f.julgamentoInicio, julgamentoFim: f.julgamentoFim,
    publicacaoInicio: f.publicacaoInicio, publicacaoFim: f.publicacaoFim
  };
}
