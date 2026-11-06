import { useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import Icon from '../components/Icon';
import { Badge, Empty, Notice, Pagination, Spinner, formatData, useAsync } from '../components/ui';
import { useAuth } from '../auth/AuthContext';
import { api } from '../api/client';

export function Notificacoes() {
  const { user, carregando, disponivel } = useAuth();
  const [pagina, setPagina] = useState(0);
  const [rev, setRev] = useState(0);
  const { loading, data, error } = useAsync(() => (user ? api.get('/me/notificacoes', { pagina, tamanho: 30 }) : Promise.resolve(null)), [user, pagina, rev]);
  if (!disponivel) return <div className="container page"><Notice kind="warn">Notificações exigem login (indisponível na pré-visualização).</Notice></div>;
  if (carregando) return <div className="container page"><Spinner /></div>;
  if (!user) return <Navigate to="/entrar" replace />;
  return (
    <div className="container page" style={{ maxWidth: 860 }}>
      <div className="page-title"><h1>Notificações</h1><p>Novas decisões que correspondem aos seus alertas.</p><span className="spacer" /><Link to="/meus-itens" className="btn btn-ghost btn-sm">Gerenciar alertas</Link></div>
      {error && <Notice kind="error">{error.message}</Notice>}
      {loading ? <Spinner /> : data && (data.itens.length === 0 ? <Empty icon="bell" title="Nenhuma notificação" /> : (
        <div className="stack">
          {data.itens.map((n) => (
            <article key={n.id} className="card" style={{ borderLeft: n.lida ? undefined : '4px solid var(--gold-500)' }}>
              <div className="row"><Badge color="gray">{n.tribunal}</Badge>{!n.lida && <Badge color="gold">Nova</Badge>}<span className="xsmall muted">{formatData(n.criadoEm, true)}</span><span className="xsmall muted">· Alerta: {n.alertaTermos}</span></div>
              <Link className="card-title" to={n.documentoId ? `/decisao/${encodeURIComponent(n.documentoId)}` : '/radar'}
                onClick={() => !n.lida && api.post(`/me/notificacoes/${n.id}/lida`, {})}>{n.titulo}</Link>
              <div className="card-actions">
                {!n.lida && <button className="btn btn-ghost btn-sm" onClick={async () => { await api.post(`/me/notificacoes/${n.id}/lida`, {}); setRev((r) => r + 1); }}><Icon name="check" size={15} /> Marcar como lida</button>}
                {n.urlOficial && <a className="btn btn-ghost btn-sm" href={n.urlOficial} target="_blank" rel="noreferrer noopener"><Icon name="external" size={15} /> Fonte oficial</a>}
              </div>
            </article>
          ))}
          <Pagination pagina={pagina} total={data.total} tamanho={data.tamanho} onChange={setPagina} />
        </div>
      ))}
    </div>
  );
}

export function Sobre() {
  return (
    <div className="container page" style={{ maxWidth: 860 }}>
      <h1>Sobre a plataforma e metodologia</h1>
      <div className="stack">
        <section className="panel panel-body">
          <h2>De onde vêm os dados</h2>
          <p>Legislação: Portal da Legislação do Planalto (texto compilado) e LexML (metadados). Jurisprudência e publicações: Portal de Dados Abertos do STJ, Diário de Justiça Eletrônico Nacional (DJEN) e API Pública do DataJud (CNJ). Cada documento guarda a fonte, o link original e a data da sincronização.</p>
          <p className="small muted">A cobertura de cada tribunal depende do que ele publica em formato aberto. Lacunas aparecem na página <Link to="/fontes">Fontes</Link>.</p>
        </section>
        <section className="panel panel-body">
          <h2>Como a IA é usada</h2>
          <p>A IA trabalha com recuperação aumentada (RAG): primeiro a pergunta é interpretada e a base oficial é consultada; depois o modelo redige a análise usando <strong>apenas</strong> os documentos recuperados, numerados como S1, S2…</p>
          <ul>
            <li>Referências a fontes inexistentes são removidas automaticamente.</li>
            <li>Números de processo, leis e súmulas mencionados pela IA são conferidos com o texto das fontes; o que não confere vai para “Informação não confirmada”.</li>
            <li>O texto literal da lei exibido vem da fonte oficial, nunca do modelo.</li>
          </ul>
        </section>
        <section className="panel panel-body">
          <h2>Legenda</h2>
          <div className="row"><Badge color="navy">Texto legal</Badge><span className="small">dispositivo transcrito da fonte oficial</span></div>
          <div className="row" style={{ marginTop: 6 }}><Badge color="violet">Resumo produzido pela IA</Badge><span className="small">texto gerado; confira o original</span></div>
          <div className="row" style={{ marginTop: 6 }}><Badge color="green">Publicada</Badge><span className="small">disponibilizada em diário oficial</span></div>
          <div className="row" style={{ marginTop: 6 }}><Badge color="amber">Julgada</Badge><span className="small">julgamento registrado, publicação não localizada</span></div>
        </section>
        <Notice kind="info">As informações têm caráter informativo e não substituem a consulta a um advogado ou ao documento oficial.</Notice>
      </div>
    </div>
  );
}

export function NotFound() {
  return <div className="container page"><Empty icon="search" title="Página não encontrada"><Link to="/">Voltar ao início</Link></Empty></div>;
}
