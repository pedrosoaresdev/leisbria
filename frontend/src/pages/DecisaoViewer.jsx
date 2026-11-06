import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import Icon from '../components/Icon';
import FavoriteButton from '../components/FavoriteButton';
import { Badge, HighlightTerms, Notice, Spinner, copiar, formatData, useAsync, useToast } from '../components/ui';
import { api } from '../api/client';

function citar(d) {
  const hoje = new Date().toLocaleDateString('pt-BR', { day: 'numeric', month: 'short', year: 'numeric' });
  return `BRASIL. ${d.tribunalNome || d.tribunal}. ${d.classe || ''} ${d.numeroProcesso || ''}. ${d.relator ? `Relator: ${d.relator}. ` : ''}${d.orgaoJulgador ? `${d.orgaoJulgador}. ` : ''}${d.dataJulgamento ? `Julgado em ${formatData(d.dataJulgamento)}. ` : ''}${d.dataPublicacao ? `Publicado em ${formatData(d.dataPublicacao)}. ` : ''}Disponível em: ${d.urlOficial || ''}. Acesso em: ${hoje}.`.replace(/\s+/g, ' ');
}

export default function DecisaoViewer() {
  const { id } = useParams();
  const toast = useToast();
  const [busca, setBusca] = useState('');
  const { loading, data: d, error } = useAsync(() => api.get(`/jurisprudencia/${encodeURIComponent(id)}`), [id]);

  if (loading) return <div className="container page"><Spinner /></div>;
  if (error) return <div className="container page"><Notice kind="error">{error.message}</Notice><p style={{ marginTop: 12 }}><Link to="/jurisprudencia">← Jurisprudência</Link></p></div>;

  return (
    <div className="container page" style={{ maxWidth: 980 }}>
      <div className="row small" style={{ marginBottom: 8 }}>
        <Badge color="gray">{d.tribunal}</Badge>
        {d.categoria && <Badge color="navy">{d.categoria.replaceAll('_', ' ').toLowerCase()}</Badge>}
        {d.statusPublicacao === 'PUBLICADA' ? <Badge color="green">Publicada</Badge> : d.statusPublicacao === 'JULGADA' ? <Badge color="amber">Julgada — publicação não localizada</Badge> : null}
        <Badge color="gray">Fonte: {d.fonteNome}</Badge>
      </div>
      <h1>{d.classe} {d.numeroProcesso}</h1>
      <div className="row" style={{ marginBottom: 16 }}>
        {d.urlOficial && <a className="btn btn-primary btn-sm" href={d.urlOficial} target="_blank" rel="noreferrer noopener"><Icon name="external" size={15} /> Abrir na fonte oficial</a>}
        <button className="btn btn-ghost btn-sm" onClick={() => copiar(citar(d), toast, 'Citação copiada')}><Icon name="copy" size={15} /> Copiar citação</button>
        <button className="btn btn-ghost btn-sm" onClick={() => copiar(window.location.href, toast, 'Link copiado')}><Icon name="share" size={15} /> Compartilhar</button>
        <button className="btn btn-ghost btn-sm" onClick={() => window.print()}><Icon name="download" size={15} /> PDF</button>
        <FavoriteButton item={{ tipo: 'decisao', id: d.id, titulo: `${d.tribunal} — ${d.classe || ''} ${d.numeroProcesso || ''}`, fonteNome: d.fonteNome, urlOficial: d.urlOficial }} />
      </div>

      <section className="panel" style={{ marginBottom: 16 }}>
        <div className="panel-body">
          <dl className="kv">
            <dt>Tribunal</dt><dd>{d.tribunal}{d.uf ? ` (${d.uf})` : ''}</dd>
            <dt>Órgão julgador</dt><dd>{d.orgaoJulgador || '—'}</dd>
            <dt>Relator(a)</dt><dd>{d.relator || '—'}</dd>
            <dt>Julgamento</dt><dd>{formatData(d.dataJulgamento)}</dd>
            <dt>Publicação</dt><dd>{formatData(d.dataPublicacao)}</dd>
            {d.tema && <><dt>Tema</dt><dd>{d.tema}</dd></>}
            {d.resultado && <><dt>Resultado</dt><dd>{d.resultado}</dd></>}
            {d.assuntos?.length > 0 && <><dt>Assuntos</dt><dd>{[].concat(d.assuntos).join('; ')}</dd></>}
            {d.dispositivosCitados?.length > 0 && <><dt>Dispositivos citados</dt><dd>{d.dispositivosCitados.join('; ')}</dd></>}
            <dt>Sincronizado em</dt><dd>{formatData(d.sincronizadoEm, true)}</dd>
          </dl>
        </div>
      </section>

      {d.tese && <section className="panel" style={{ marginBottom: 16 }}><div className="panel-head"><h2>Tese</h2></div><div className="panel-body literal">{d.tese}</div></section>}
      {d.ementa && <section className="panel" style={{ marginBottom: 16 }}><div className="panel-head"><h2>Ementa</h2></div><div className="panel-body literal"><HighlightTerms text={d.ementa} term={busca} /></div></section>}
      {d.referenciasLegislativas && <section className="panel" style={{ marginBottom: 16 }}><div className="panel-head"><h2>Referências legislativas</h2></div><div className="panel-body small" style={{ whiteSpace: 'pre-line' }}>{d.referenciasLegislativas}</div></section>}
      {d.inteiroTeor ? (
        <section className="panel">
          <div className="panel-head"><h2>Inteiro teor</h2><span className="spacer" />
            <input className="input" style={{ maxWidth: 240, minHeight: 34 }} placeholder="Pesquisar no texto" value={busca} onChange={(e) => setBusca(e.target.value)} aria-label="Pesquisar no inteiro teor" />
          </div>
          <div className="panel-body serif" style={{ whiteSpace: 'pre-line', lineHeight: 1.7 }}><HighlightTerms text={d.inteiroTeor} term={busca} /></div>
        </section>
      ) : (
        <Notice kind="info">O inteiro teor não está disponível nesta fonte. Consulte o documento original pelo link oficial.</Notice>
      )}
    </div>
  );
}
