import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import Icon from './Icon';

/** Converte os marcadores \u0001 … \u0002 do backend em <mark>, sem HTML bruto (seguro contra XSS). */
export function Highlight({ text }) {
  if (!text) return null;
  const parts = String(text).split(/(\u0001[^\u0002]*\u0002)/g);
  return parts.map((p, i) => (p.startsWith('\u0001')
    ? <mark key={i}>{p.slice(1, -1)}</mark>
    : <span key={i}>{p}</span>));
}

/** Destaca termos (busca dentro do documento). */
export function HighlightTerms({ text, term }) {
  if (!term || term.length < 2) return text;
  const esc = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const re = new RegExp(`(${esc})`, 'gi');
  return String(text).split(re).map((p, i) => (i % 2 === 1 ? <mark key={i}>{p}</mark> : p));
}

export function Spinner({ label = 'Carregando…' }) {
  return <div className="loading" role="status"><div className="spinner" /><span>{label}</span></div>;
}

export function Empty({ icon = 'search', title, children }) {
  return (
    <div className="empty">
      <Icon name={icon} size={40} />
      {title && <h3>{title}</h3>}
      {children && <div className="small">{children}</div>}
    </div>
  );
}

export function Notice({ kind = 'info', children }) {
  const icon = { info: 'info', warn: 'alert', error: 'alert', ok: 'check' }[kind];
  return <div className={`notice notice-${kind}`} role={kind === 'error' ? 'alert' : undefined}><Icon name={icon} /><div>{children}</div></div>;
}

export function Badge({ color = 'navy', children, title }) {
  return <span className={`badge badge-${color}`} title={title}>{children}</span>;
}

export function Pagination({ pagina, total, tamanho, onChange }) {
  const paginas = Math.ceil(total / tamanho);
  if (paginas <= 1) return null;
  return (
    <nav className="pagination" aria-label="Paginação">
      <button className="btn btn-ghost btn-sm" disabled={pagina === 0} onClick={() => onChange(pagina - 1)}>Anterior</button>
      <span className="small muted">Página {pagina + 1} de {paginas.toLocaleString('pt-BR')}</span>
      <button className="btn btn-ghost btn-sm" disabled={pagina + 1 >= paginas} onClick={() => onChange(pagina + 1)}>Próxima</button>
    </nav>
  );
}

export function Modal({ title, onClose, children }) {
  useEffect(() => {
    const h = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [onClose]);
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head"><h3 style={{ margin: 0 }}>{title}</h3><span className="spacer" />
          <button className="icon-btn" style={{ color: 'var(--gray-700)' }} onClick={onClose} aria-label="Fechar"><Icon name="close" /></button>
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}

const ToastCtx = createContext(() => {});
export function ToastProvider({ children }) {
  const [msg, setMsg] = useState(null);
  const show = useCallback((m) => {
    setMsg(m);
    clearTimeout(window.__toastTimer);
    window.__toastTimer = setTimeout(() => setMsg(null), 2600);
  }, []);
  return (
    <ToastCtx.Provider value={show}>
      {children}
      {msg && <div className="toast" role="status">{msg}</div>}
    </ToastCtx.Provider>
  );
}
export const useToast = () => useContext(ToastCtx);

export function formatData(iso, comHora = false) {
  if (!iso) return '—';
  const d = new Date(iso.length === 10 ? `${iso}T12:00:00` : iso);
  if (Number.isNaN(d.getTime())) return iso;
  return comHora
    ? d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
    : d.toLocaleDateString('pt-BR');
}

export async function copiar(texto, toast, msg = 'Copiado para a área de transferência') {
  try {
    await navigator.clipboard.writeText(texto);
    toast(msg);
  } catch {
    toast('Não foi possível copiar automaticamente.');
  }
}

/** Hook simples de carregamento assíncrono com cancelamento. */
export function useAsync(fn, deps) {
  const [state, setState] = useState({ loading: true, data: null, error: null });
  useEffect(() => {
    let vivo = true;
    setState((s) => ({ ...s, loading: true, error: null }));
    fn().then((data) => vivo && setState({ loading: false, data, error: null }))
      .catch((error) => vivo && setState({ loading: false, data: null, error }));
    return () => { vivo = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return state;
}
