import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from './Icon';
import { api } from '../api/client';
import { Badge, Notice, Spinner } from './ui';
import { linkInterno } from './ResultCard';

/** Converte "[S3]" no texto da IA em botões que levam à fonte correspondente. */
function ComRefs({ texto, onRef }) {
  if (!texto) return null;
  return String(texto).split(/(\[S\d+\])/g).map((p, i) => {
    const m = p.match(/^\[S(\d+)\]$/);
    return m
      ? <button key={i} className="source-ref" onClick={() => onRef(`S${m[1]}`)} title={`Ver fonte S${m[1]}`}>S{m[1]}</button>
      : <span key={i}>{p}</span>;
  });
}

function Secao({ cor, rotulo, descricao, children }) {
  return (
    <div className="ai-section">
      <div className="ai-label"><Badge color={cor}>{rotulo}</Badge><span className="muted xsmall" style={{ textTransform: 'none', letterSpacing: 0, fontWeight: 500 }}>{descricao}</span></div>
      {children}
    </div>
  );
}

export default function AiPanel({ pergunta }) {
  const [estado, setEstado] = useState({ loading: true });

  useEffect(() => {
    let vivo = true;
    setEstado({ loading: true });
    api.post('/ia/perguntar', { pergunta })
      .then((r) => vivo && setEstado({ loading: false, r }))
      .catch((e) => vivo && setEstado({ loading: false, erro: e.message }));
    return () => { vivo = false; };
  }, [pergunta]);

  const irPara = (ref) => {
    const el = document.getElementById(`fonte-${ref}`);
    if (el) { el.scrollIntoView({ behavior: 'smooth', block: 'center' }); el.animate([{ background: 'var(--gold-100)' }, { background: 'transparent' }], { duration: 1600 }); }
  };

  const { r } = estado;
  return (
    <section className="panel ai-panel" aria-labelledby="ia-titulo">
      <div className="panel-head">
        <Icon name="sparkle" style={{ color: 'var(--violet-600)' }} />
        <h2 id="ia-titulo">Análise com IA</h2>
        <span className="spacer" />
        <Badge color="violet">RAG · somente fontes da base</Badge>
      </div>
      {estado.loading && <Spinner label="Interpretando a pergunta e consultando a base oficial…" />}
      {estado.erro && <div className="panel-body"><Notice kind="error">{estado.erro}</Notice></div>}
      {r && r.habilitada === false && <div className="panel-body"><Notice kind="info">{r.mensagem}</Notice></div>}
      {r && r.habilitada !== false && (
        <>
          {!r.suficiente && (
            <div className="ai-section">
              <Notice kind="warn">
                <strong>Dados insuficientes para uma conclusão segura.</strong> {r.motivoInsuficiencia || 'A base consultada não contém documentos suficientes sobre este ponto.'}
              </Notice>
            </div>
          )}
          {r.assuntos?.length > 0 && (
            <div className="ai-section">
              <div className="ai-label"><Badge color="gray">Assuntos identificados</Badge></div>
              <div className="row">{r.assuntos.map((a) => <Badge key={a} color="navy">{a}</Badge>)}</div>
            </div>
          )}
          {r.resumo && (
            <Secao cor="violet" rotulo="Resumo produzido pela IA" descricao="Texto gerado a partir das fontes citadas; confira sempre o original.">
              <p className="ai-text"><ComRefs texto={r.resumo} onRef={irPara} /></p>
            </Secao>
          )}
          {r.fontes?.some((f) => f.tipo === 'dispositivo') && (
            <Secao cor="navy" rotulo="Texto literal da legislação" descricao="Transcrição exata da fonte oficial — não é texto da IA.">
              <div className="stack">
                {r.fontes.filter((f) => f.tipo === 'dispositivo').map((f) => (
                  <div key={f.ref}>
                    <div className="row small" style={{ marginBottom: 4 }}><button className="source-ref" onClick={() => irPara(f.ref)}>{f.ref}</button><strong>{f.titulo}</strong></div>
                    <div className="literal">{f.trechoLiteral}</div>
                  </div>
                ))}
              </div>
            </Secao>
          )}
          {r.entendimentoJurisprudencial && (
            <Secao cor="gold" rotulo="Entendimento jurisprudencial" descricao="Síntese da IA sobre as decisões recuperadas.">
              <p className="ai-text"><ComRefs texto={r.entendimentoJurisprudencial} onRef={irPara} /></p>
            </Secao>
          )}
          {r.interpretacao && (
            <Secao cor="amber" rotulo="Interpretação jurídica (IA)" descricao="Raciocínio interpretativo; não é texto de lei nem decisão.">
              <p className="ai-text"><ComRefs texto={r.interpretacao} onRef={irPara} /></p>
            </Secao>
          )}
          {r.naoConfirmado?.length > 0 && (
            <Secao cor="red" rotulo="Informação não confirmada" descricao="Pontos sem respaldo nas fontes recuperadas ou removidos pela verificação automática.">
              <ul className="small" style={{ margin: 0, paddingLeft: 18 }}>{r.naoConfirmado.map((n, i) => <li key={i}>{n}</li>)}</ul>
            </Secao>
          )}
          {r.fontes?.length > 0 && (
            <Secao cor="green" rotulo="Fontes verificáveis" descricao="Cada referência acima corresponde a um documento existente na base.">
              <ol className="list-plain">
                {r.fontes.map((f) => {
                  const item = r.itens?.find((i) => i.fonte === f.ref);
                  return (
                    <li key={f.ref} id={`fonte-${f.ref}`} style={{ borderRadius: 6 }}>
                      <div className="row"><span className="source-ref" style={{ cursor: 'default' }}>{f.ref}</span>
                        <Link to={linkInterno({ tipo: f.tipo, id: f.id, normaId: f.normaId })} className="small" style={{ fontWeight: 600 }}>{f.titulo}</Link>
                      </div>
                      {item?.relacao && <div className="small" style={{ marginTop: 4 }}><strong>Relação com a pergunta (IA):</strong> {item.relacao}</div>}
                      <div className="row xsmall muted" style={{ marginTop: 4 }}>
                        <span>{f.fonteNome}</span>{f.data && <span>· {f.data}</span>}
                        {f.urlOficial && <a href={f.urlOficial} target="_blank" rel="noreferrer noopener">· Abrir na fonte oficial</a>}
                      </div>
                    </li>
                  );
                })}
              </ol>
            </Secao>
          )}
          {r.avisos?.length > 0 && <div className="ai-section">{r.avisos.map((a, i) => <Notice key={i} kind="warn">{a}</Notice>)}</div>}
          <div className="panel-foot">
            Gerado por {r.modelo || 'modelo de linguagem'} em {r.geradoEm ? new Date(r.geradoEm).toLocaleString('pt-BR') : '—'}.
            Conteúdo informativo; não constitui parecer jurídico.
          </div>
        </>
      )}
    </section>
  );
}
