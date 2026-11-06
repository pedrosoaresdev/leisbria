import { Link, useNavigate } from 'react-router-dom';
import SearchBox from '../components/SearchBox';
import Icon from '../components/Icon';
import { Badge, Empty, Spinner, formatData, useAsync, Notice } from '../components/ui';
import { api } from '../api/client';
import { EXEMPLOS } from '../config';
import { linkInterno } from '../components/ResultCard';

const ATALHOS = [
  { to: '/constituicao', icon: 'columns', titulo: 'Constituição', sub: 'CF/88 e ADCT, artigo por artigo' },
  { to: '/legislacao', icon: 'book', titulo: 'Códigos e leis', sub: 'Civil, Penal, Processo, CLT, CDC…' },
  { to: '/jurisprudencia', icon: 'scale', titulo: 'Jurisprudência', sub: 'STF, STJ, TRFs, TJs, TRTs, TREs' },
  { to: '/radar', icon: 'radar', titulo: 'Radar Jurídico', sub: 'Novas decisões e publicações' }
];

function Lista({ itens, vazio }) {
  if (!itens?.length) return <Empty icon="database" title="Nada sincronizado ainda">{vazio}</Empty>;
  return (
    <ul className="list-plain">
      {itens.map((r) => (
        <li key={r.id}>
          <div className="row xsmall" style={{ marginBottom: 2 }}>
            {r.tribunal && <Badge color="gray">{r.tribunal}</Badge>}
            {r.statusPublicacao === 'PUBLICADA' && <Badge color="green">Publicada</Badge>}
            {r.statusPublicacao === 'JULGADA' && <Badge color="amber">Julgada</Badge>}
            <span className="muted">{formatData(r.data, r.horarioDisponivel)}</span>
          </div>
          <Link to={linkInterno(r)} style={{ fontWeight: 600 }}>{r.titulo}</Link>
          {r.trecho && <div className="small muted" style={{ marginTop: 2 }}>{r.trecho.replace(/[\u0001\u0002]/g, '').slice(0, 220)}{r.trecho.length > 220 ? '…' : ''}</div>}
        </li>
      ))}
    </ul>
  );
}

export default function Home() {
  const nav = useNavigate();
  const { loading, data, error } = useAsync(() => api.get('/inicio'), []);

  return (
    <>
      <section className="hero">
        <div className="container">
          <div className="hero-eyebrow">Legislação · Jurisprudência · Inteligência artificial</div>
          <h1>Pesquise o Direito brasileiro em fontes oficiais.</h1>
          <p className="lead">Descreva a situação em linguagem natural ou busque diretamente por lei, artigo, súmula ou número de processo. Cada resultado traz a fonte oficial para conferência.</p>
          <SearchBox autoFocus />
          <div className="examples" aria-label="Exemplos de pesquisa">
            {EXEMPLOS.map((ex) => (
              <button key={ex} type="button" onClick={() => nav(`/busca?q=${encodeURIComponent(ex)}${ex.length > 40 ? '&ia=1' : ''}`)}>
                {ex.length > 70 ? `${ex.slice(0, 68)}…` : ex}
              </button>
            ))}
          </div>
        </div>
      </section>

      <div className="container">
        <div className="shortcuts">
          {ATALHOS.map((a) => (
            <Link key={a.to} to={a.to} className="shortcut">
              <Icon name={a.icon} size={22} />
              <strong>{a.titulo}</strong>
              <span>{a.sub}</span>
            </Link>
          ))}
        </div>

        <div className="home-columns" style={{ paddingBottom: 40 }}>
          <div className="stack">
            {data?.aviso && <Notice kind="info">{data.aviso}</Notice>}
            {error && <Notice kind="error">{error.message}</Notice>}
            <section className="panel">
              <div className="panel-head"><Icon name="gavel" /><h2>Últimas decisões publicadas</h2><span className="spacer" /><Link to="/radar" className="small">Ver Radar</Link></div>
              <div className="panel-body">{loading ? <Spinner /> : <Lista itens={data?.ultimasDecisoes} vazio="As decisões aparecem aqui após a sincronização com DJEN, STJ e DataJud." />}</div>
              {data?.sincronizacao?.radar && <div className="panel-foot">Última sincronização: {formatData(data.sincronizacao.radar, true)}</div>}
            </section>
            <section className="panel">
              <div className="panel-head"><Icon name="sparkle" /><h2>Decisões relevantes do dia</h2></div>
              <div className="panel-body">{loading ? <Spinner /> : <Lista itens={data?.relevantesDoDia} vazio="Súmulas, temas repetitivos, repercussão geral e controle concentrado publicados hoje." />}</div>
            </section>
          </div>
          <aside className="stack">
            <section className="panel">
              <div className="panel-head"><Icon name="book" /><h2>Atualizações legislativas</h2></div>
              <div className="panel-body">{loading ? <Spinner /> : <Lista itens={data?.atualizacoesLegislativas} vazio="Novas normas publicadas, a partir do LexML." />}</div>
              {data?.sincronizacao?.lexml && <div className="panel-foot">Última sincronização: {formatData(data.sincronizacao.lexml, true)}</div>}
            </section>
            <section className="panel">
              <div className="panel-body small">
                <strong className="serif" style={{ color: 'var(--navy-800)', fontSize: '1rem' }}>Compromisso com a verificabilidade</strong>
                <ul style={{ paddingLeft: 18, marginBottom: 0 }}>
                  <li>Texto de lei e decisões vêm das fontes oficiais, com link para o original.</li>
                  <li>Resumos de IA são identificados e citam apenas documentos existentes na base.</li>
                  <li>Data e hora da última sincronização de cada fonte ficam visíveis.</li>
                </ul>
                <Link to="/fontes" className="btn btn-ghost btn-sm" style={{ marginTop: 10 }}>Ver status das fontes</Link>
              </div>
            </section>
          </aside>
        </div>
      </div>
    </>
  );
}
