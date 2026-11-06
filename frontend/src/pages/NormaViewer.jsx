import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import Icon from '../components/Icon';
import { Badge, HighlightTerms, Modal, Notice, Spinner, copiar, formatData, useAsync, useToast } from '../components/ui';
import FavoriteButton from '../components/FavoriteButton';
import { api } from '../api/client';

function citacaoABNT(norma, d) {
  const hoje = new Date().toLocaleDateString('pt-BR', { day: 'numeric', month: 'short', year: 'numeric' });
  const autor = norma.tipo === 'Constituição' ? `BRASIL. Constituição (${norma.ano}). ${norma.titulo}` : `BRASIL. ${norma.titulo}`;
  return `${autor}. ${d ? `${d.rotulo}. ` : ''}Disponível em: ${norma.urlOficial}. Acesso em: ${hoje}.`;
}

function Historico({ dispositivo, onClose }) {
  const { loading, data, error } = useAsync(() => api.get(`/legislacao/dispositivos/${encodeURIComponent(dispositivo.id)}/historico`), [dispositivo.id]);
  const versoes = data || [];
  const [a, setA] = useState(0);
  const [b, setB] = useState(null);
  const todas = [...versoes, { ...dispositivo, rotuloVersao: 'Redação vigente' }];
  const vb = b === null ? todas.length - 1 : b;
  return (
    <Modal title={`Histórico — ${dispositivo.rotulo}`} onClose={onClose}>
      {loading && <Spinner />}
      {error && <Notice kind="error">{error.message}</Notice>}
      {!loading && !error && (
        <div className="stack">
          {dispositivo.notasAlteracao && <Notice kind="info">Notas da fonte oficial: {dispositivo.notasAlteracao}</Notice>}
          {versoes.length === 0 ? (
            <Notice kind="info">Nenhuma redação anterior registrada na fonte para este dispositivo.</Notice>
          ) : (
            <>
              <div className="row">
                <select className="select" style={{ width: 'auto' }} value={a} onChange={(e) => setA(Number(e.target.value))} aria-label="Versão A">
                  {todas.map((v, i) => <option key={i} value={i}>{v.rotuloVersao || `Redação anterior ${i + 1}`}</option>)}
                </select>
                <span>×</span>
                <select className="select" style={{ width: 'auto' }} value={vb} onChange={(e) => setB(Number(e.target.value))} aria-label="Versão B">
                  {todas.map((v, i) => <option key={i} value={i}>{v.rotuloVersao || `Redação anterior ${i + 1}`}</option>)}
                </select>
              </div>
              <div className="diff-cols">
                <div className="col diff-del">{todas[a]?.texto}</div>
                <div className="col diff-add">{todas[vb]?.texto}</div>
              </div>
              <p className="xsmall muted">Redações anteriores são as que a fonte oficial mantém tachadas no texto compilado.</p>
            </>
          )}
        </div>
      )}
    </Modal>
  );
}

export default function NormaViewer() {
  const { id } = useParams();
  const [sp, setSp] = useSearchParams();
  const alvo = sp.get('d');
  const toast = useToast();
  const { loading, data, error } = useAsync(() => api.get(`/legislacao/normas/${encodeURIComponent(id)}`), [id]);
  const [busca, setBusca] = useState('');
  const [idx, setIdx] = useState(0);
  const [irArt, setIrArt] = useState('');
  const [hist, setHist] = useState(null);
  const [mostrarRevogados, setMostrarRevogados] = useState(false);
  const law = useRef(null);

  const norma = data?.norma;
  const dispositivos = useMemo(() => (data?.dispositivos || []).filter((d) => mostrarRevogados || d.vigente !== false), [data, mostrarRevogados]);
  const cabecalhos = useMemo(() => {
    const m = {};
    (norma?.estrutura || []).forEach((h) => { (m[h.antesDe] ||= []).push(h); });
    return m;
  }, [norma]);

  const acertos = useMemo(() => {
    if (busca.trim().length < 2) return [];
    const t = busca.trim().toLowerCase();
    return dispositivos.filter((d) => d.texto.toLowerCase().includes(t)).map((d) => d.id);
  }, [busca, dispositivos]);

  useEffect(() => {
    if (!alvo || !data) return;
    const t = setTimeout(() => document.getElementById(`d-${alvo}`)?.scrollIntoView({ block: 'center' }), 60);
    return () => clearTimeout(t);
  }, [alvo, data]);

  useEffect(() => {
    if (acertos.length) document.getElementById(`d-${acertos[idx % acertos.length]}`)?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }, [idx, acertos]);

  const irPara = (e) => {
    e.preventDefault();
    const n = irArt.replace(/\D/g, '');
    const d = dispositivos.find((x) => x.nivel === 'artigo' && x.artigo?.replace(/-.*/, '') === n);
    if (d) setSp({ d: d.id }); else toast('Artigo não encontrado neste documento.');
  };

  const linkDisp = (d) => `${window.location.origin}${window.location.pathname}#/norma/${encodeURIComponent(id)}?d=${encodeURIComponent(d.id)}`;

  if (loading) return <div className="container page"><Spinner label="Carregando norma…" /></div>;
  if (error) return <div className="container page"><Notice kind="error">{error.message}</Notice><p style={{ marginTop: 12 }}><Link to="/legislacao">← Voltar à legislação</Link></p></div>;

  const artigos = dispositivos.filter((d) => d.nivel === 'artigo');

  return (
    <div className="container page">
      <div className="page-title" style={{ marginBottom: 8 }}>
        <div style={{ flex: 1, minWidth: 260 }}>
          <div className="row small" style={{ marginBottom: 6 }}>
            <Badge color="navy">{norma.tipo}</Badge>
            {norma.situacao && <Badge color={/revogad/i.test(norma.situacao) ? 'red' : 'green'}>{norma.situacao}</Badge>}
            <Badge color="gray">Fonte: {norma.fonteNome}</Badge>
            {norma.amostra && <Badge color="gold">Amostra de pré-visualização</Badge>}
          </div>
          <h1 style={{ marginBottom: 4 }}>{norma.apelido || norma.titulo}</h1>
          {norma.apelido && <div className="muted">{norma.titulo}</div>}
          {norma.ementa && <p className="small" style={{ marginTop: 8 }}>{norma.ementa}</p>}
          <div className="xsmall muted">
            Publicação: {formatData(norma.dataPublicacao)} · Sincronizado em {formatData(norma.sincronizadoEm, true)}
          </div>
        </div>
      </div>
      {norma.amostra && <div style={{ marginBottom: 12 }}><Notice kind="warn">Pré-visualização: apenas um trecho do texto oficial (arts. 1º a 3º e parte do art. 5º). O texto integral é importado do Planalto pela sincronização.</Notice></div>}

      <div className="reader">
        <aside className="panel toc no-print" aria-label="Sumário">
          <div className="panel-head"><h3>Sumário</h3><span className="spacer" /><span className="xsmall muted">{artigos.length} artigos</span></div>
          <form className="row" style={{ padding: '10px 12px 0', flexWrap: 'nowrap' }} onSubmit={irPara}>
            <input className="input" placeholder="Ir para o art. nº" inputMode="numeric" value={irArt} onChange={(e) => setIrArt(e.target.value)} aria-label="Ir para o artigo" />
            <button className="btn btn-primary btn-sm">Ir</button>
          </form>
          <div className="toc-inner">
            {dispositivos.map((d) => (
              <div key={d.id}>
                {(cabecalhos[d.id] || []).map((h, i) => (
                  <a key={i} href={`#h-${d.id}-${i}`} onClick={(e) => { e.preventDefault(); document.getElementById(`h-${d.id}-${i}`)?.scrollIntoView({ block: 'start' }); }} className={`lvl-${h.nivel}`}>
                    {h.rotulo}{h.nome ? ` — ${h.nome}` : ''}
                  </a>
                ))}
                {d.nivel === 'artigo' && (
                  <a href={`#d-${d.id}`} className="lvl-artigo" onClick={(e) => { e.preventDefault(); setSp({ d: d.id }); }}>{d.marcador || d.rotulo}</a>
                )}
              </div>
            ))}
          </div>
        </aside>

        <article className="panel" style={{ minWidth: 0 }}>
          <div className="reader-toolbar no-print">
            <div className="search-compact" style={{ flex: '1 1 220px', boxShadow: 'none' }}>
              <input value={busca} onChange={(e) => { setBusca(e.target.value); setIdx(0); }} placeholder="Pesquisar neste documento" aria-label="Pesquisar no documento" />
            </div>
            {busca.trim().length >= 2 && (
              <div className="row small">
                <span className="muted">{acertos.length ? `${(idx % acertos.length) + 1}/${acertos.length}` : '0 resultados'}</span>
                <button className="btn btn-ghost btn-sm" disabled={!acertos.length} onClick={() => setIdx((i) => (i - 1 + acertos.length) % acertos.length)} aria-label="Anterior">↑</button>
                <button className="btn btn-ghost btn-sm" disabled={!acertos.length} onClick={() => setIdx((i) => i + 1)} aria-label="Próximo">↓</button>
              </div>
            )}
            <label className="checkbox small"><input type="checkbox" checked={mostrarRevogados} onChange={(e) => setMostrarRevogados(e.target.checked)} /> Redações anteriores</label>
            <button className="btn btn-ghost btn-sm" onClick={() => copiar(citacaoABNT(norma), toast, 'Citação copiada')}><Icon name="copy" size={15} /> Citar norma</button>
            <button className="btn btn-ghost btn-sm" onClick={() => window.print()} title="Use 'Salvar como PDF' na janela de impressão"><Icon name="download" size={15} /> PDF</button>
            <a className="btn btn-ghost btn-sm" href={norma.urlOficial} target="_blank" rel="noreferrer noopener"><Icon name="external" size={15} /> Original</a>
          </div>

          <div className="law" ref={law}>
            {dispositivos.map((d) => (
              <div key={d.id}>
                {(cabecalhos[d.id] || []).map((h, i) => (
                  <div key={i} id={`h-${d.id}-${i}`} className="law-heading">
                    <span className="lbl">{h.rotulo}</span>{h.nome && <span className="nm">{h.nome}</span>}
                  </div>
                ))}
                <div id={`d-${d.id}`} className={`disp nivel-${d.nivel} ${alvo === d.id ? 'target' : ''} ${d.vigente === false ? 'revogado' : ''}`} tabIndex={-1}>
                  <span className="disp-label">{d.marcador || d.rotulo}</span>{' '}
                  <HighlightTerms text={d.texto} term={busca.trim()} />
                  {d.notasAlteracao && <span className="disp-note">{d.notasAlteracao}</span>}
                  <span className="disp-tools">
                    <button onClick={() => copiar(`${norma.apelido || norma.titulo}, ${d.rotulo}: ${d.texto}`, toast, 'Dispositivo copiado')}>Copiar</button>
                    <button onClick={() => copiar(citacaoABNT(norma, d), toast, 'Citação copiada')}>Citar</button>
                    <button onClick={() => copiar(linkDisp(d), toast, 'Link copiado')}>Link</button>
                    <button onClick={() => setHist(d)}>Histórico</button>
                    <FavoriteButton small item={{ tipo: 'dispositivo', id: d.id, titulo: `${norma.apelido || norma.titulo} — ${d.rotulo}`, fonteNome: norma.fonteNome, urlOficial: norma.urlOficial, normaId: norma.id }} />
                  </span>
                </div>
              </div>
            ))}
          </div>
        </article>
      </div>
      {hist && <Historico dispositivo={hist} onClose={() => setHist(null)} />}
    </div>
  );
}
