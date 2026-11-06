import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Icon from './Icon';

/**
 * Barra de pesquisa principal. Modos:
 *  - "pergunta": linguagem natural (aciona também o painel de IA nos resultados)
 *  - "termos": palavras-chave, número de lei, artigo, súmula ou processo
 */
export default function SearchBox({ inicial = '', modoInicial = 'pergunta', autoFocus = false, compacto = false }) {
  const [q, setQ] = useState(inicial);
  const [modo, setModo] = useState(modoInicial);
  const nav = useNavigate();
  const ref = useRef(null);

  useEffect(() => setQ(inicial), [inicial]);
  useEffect(() => setModo(modoInicial), [modoInicial]);
  useEffect(() => {
    const ajustar = () => {
      const el = ref.current;
      if (el) { el.style.height = 'auto'; el.style.height = `${Math.min(el.scrollHeight + 2, 200)}px`; }
    };
    const raf = requestAnimationFrame(ajustar);
    window.addEventListener('resize', ajustar);
    return () => { cancelAnimationFrame(raf); window.removeEventListener('resize', ajustar); };
  }, [q]);

  const enviar = (e) => {
    e?.preventDefault();
    const v = q.trim();
    if (!v) return;
    nav(`/busca?q=${encodeURIComponent(v)}${modo === 'pergunta' ? '&ia=1' : ''}`);
  };

  return (
    <form className="search-box" role="search" onSubmit={enviar}>
      {!compacto && (
        <div className="search-modes" role="tablist" aria-label="Tipo de pesquisa">
          <button type="button" role="tab" aria-selected={modo === 'pergunta'} className={modo === 'pergunta' ? 'on' : ''} onClick={() => setModo('pergunta')}>
            Pergunta em linguagem natural
          </button>
          <button type="button" role="tab" aria-selected={modo === 'termos'} className={modo === 'termos' ? 'on' : ''} onClick={() => setModo('termos')}>
            Termos, artigo ou número
          </button>
        </div>
      )}
      <div className="search-row">
        <label htmlFor="busca-principal" className="sr-only">Pesquisa jurídica</label>
        <textarea
          id="busca-principal" ref={ref} rows={1} className="search-input" value={q} autoFocus={autoFocus}
          maxLength={2000} onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) enviar(e); }}
          placeholder={modo === 'pergunta'
            ? 'Descreva a situação ou faça uma pergunta jurídica…'
            : 'Ex.: art. 5º, LXIII · Lei 11.343/2006 · Súmula 7 STJ · 0000000-00.0000.0.00.0000'}
        />
        <button className="btn btn-gold" type="submit"><Icon name="search" /> Pesquisar</button>
      </div>
    </form>
  );
}
