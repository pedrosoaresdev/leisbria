import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import QRCode from 'qrcode';
import Icon from '../components/Icon';
import { Notice, Spinner } from '../components/ui';
import { useAuth } from '../auth/AuthContext';
import { api } from '../api/client';

function Indisponivel() {
  return (
    <Notice kind="warn">
      Login indisponível: {import.meta.env.VITE_PREVIEW === 'true' ? 'esta é a pré-visualização da interface, sem backend.' : 'configure as variáveis VITE_FIREBASE_* do frontend.'}
    </Notice>
  );
}

function LoginQr() {
  const { entrarComToken } = useAuth();
  const nav = useNavigate();
  const canvas = useRef(null);
  const [sessao, setSessao] = useState(null);
  const [erro, setErro] = useState(null);
  const [expirado, setExpirado] = useState(false);

  const gerar = async () => {
    setErro(null); setExpirado(false);
    try {
      const s = await api.post('/auth/qr', {});
      setSessao(s);
    } catch (e) { setErro(e.message); }
  };

  useEffect(() => { gerar(); }, []);
  useEffect(() => {
    if (sessao && canvas.current) QRCode.toCanvas(canvas.current, sessao.urlAprovacao, { width: 210, margin: 1, color: { dark: '#0b1f3a' } });
  }, [sessao]);
  useEffect(() => {
    if (!sessao) return undefined;
    const t = setInterval(async () => {
      try {
        const r = await api.get(`/auth/qr/${sessao.id}`, { segredo: sessao.segredo });
        if (r.status === 'APROVADA' && r.customToken) { clearInterval(t); await entrarComToken(r.customToken); nav('/'); }
        if (r.status === 'EXPIRADA' || r.status === 'CONSUMIDA') { clearInterval(t); setExpirado(true); }
      } catch { /* tenta de novo no próximo ciclo */ }
    }, 2500);
    return () => clearInterval(t);
  }, [sessao]);

  return (
    <div className="qr-box">
      {erro && <Notice kind="error">{erro}</Notice>}
      {!sessao && !erro && <Spinner label="Gerando QR Code…" />}
      {sessao && !expirado && <canvas ref={canvas} aria-label="QR Code de login" />}
      {expirado && <Notice kind="warn">QR Code expirado. <button className="btn-link" onClick={gerar}>Gerar outro</button></Notice>}
      <p className="small muted">Abra a câmera do celular em que você já está conectado (por exemplo, com Google), leia o código e confirme o acesso. O código vale por 2 minutos.</p>
    </div>
  );
}

export function Entrar() {
  const { entrar, entrarComGoogle, disponivel, user } = useAuth();
  const nav = useNavigate();
  const [aba, setAba] = useState('senha');
  const [username, setUsername] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState(null);
  const [ocupado, setOcupado] = useState(false);

  useEffect(() => { if (user) nav('/'); }, [user]);

  const enviar = async (e) => {
    e.preventDefault(); setErro(null); setOcupado(true);
    try { await entrar(username.trim(), senha); nav('/'); } catch (err) { setErro(err.message); } finally { setOcupado(false); }
  };
  const google = async () => {
    setErro(null);
    try { await entrarComGoogle(); nav('/'); } catch (err) { setErro(err.code === 'auth/popup-closed-by-user' ? null : 'Não foi possível entrar com o Google.'); }
  };

  return (
    <div className="container page">
      <div className="auth-wrap panel">
        <div className="panel-head"><h1 style={{ fontSize: '1.4rem', margin: 0 }}>Entrar</h1></div>
        <div className="panel-body stack">
          {!disponivel && <Indisponivel />}
          <div className="search-modes" role="tablist">
            <button className={aba === 'senha' ? 'on' : ''} onClick={() => setAba('senha')}>Usuário e senha</button>
            <button className={aba === 'qr' ? 'on' : ''} onClick={() => setAba('qr')} disabled={!disponivel}><Icon name="qr" size={14} /> QR Code</button>
          </div>
          {aba === 'senha' ? (
            <form className="stack" onSubmit={enviar}>
              <div className="field"><label htmlFor="u">Nome de usuário</label>
                <input id="u" className="input" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required minLength={3} maxLength={30} /></div>
              <div className="field"><label htmlFor="s">Senha</label>
                <input id="s" type="password" className="input" autoComplete="current-password" value={senha} onChange={(e) => setSenha(e.target.value)} required /></div>
              {erro && <div className="error-text">{erro}</div>}
              <button className="btn btn-primary btn-block" disabled={!disponivel || ocupado}>{ocupado ? 'Entrando…' : 'Entrar'}</button>
            </form>
          ) : (disponivel && <LoginQr />)}
          <div className="divider">ou</div>
          <button className="btn btn-ghost btn-block" onClick={google} disabled={!disponivel}><Icon name="google" /> Entrar com Google</button>
          <Notice kind="info">
            <strong>Esqueceu a senha?</strong> Entre com a conta Google vinculada e, em <em>Conta → Configurações → Alterar senha</em>, defina uma nova.
          </Notice>
          <p className="small" style={{ textAlign: 'center', margin: 0 }}>Não tem conta? <Link to="/cadastro">Criar conta</Link></p>
        </div>
      </div>
    </div>
  );
}

export function Cadastro() {
  const { registrar, disponivel } = useAuth();
  const nav = useNavigate();
  const [f, setF] = useState({ username: '', nome: '', senha: '', confirma: '' });
  const [erro, setErro] = useState(null);
  const [ocupado, setOcupado] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const enviar = async (e) => {
    e.preventDefault(); setErro(null);
    if (f.senha !== f.confirma) { setErro('As senhas não conferem.'); return; }
    setOcupado(true);
    try { await registrar({ username: f.username.trim().toLowerCase(), nome: f.nome.trim(), senha: f.senha }); nav('/conta?novo=1'); }
    catch (err) { setErro(err.message); } finally { setOcupado(false); }
  };

  return (
    <div className="container page">
      <form className="auth-wrap panel" onSubmit={enviar}>
        <div className="panel-head"><h1 style={{ fontSize: '1.4rem', margin: 0 }}>Criar conta</h1></div>
        <div className="panel-body stack">
          {!disponivel && <Indisponivel />}
          <div className="field"><label htmlFor="n">Nome</label><input id="n" className="input" autoComplete="name" value={f.nome} onChange={set('nome')} required maxLength={80} /></div>
          <div className="field"><label htmlFor="cu">Nome de usuário</label>
            <input id="cu" className="input" autoComplete="username" value={f.username} onChange={set('username')} required pattern="[a-zA-Z0-9._]{3,30}" title="3 a 30 caracteres: letras, números, ponto ou sublinhado" />
            <span className="hint">3 a 30 caracteres: letras, números, ponto ou sublinhado.</span></div>
          <div className="field"><label htmlFor="cs">Senha</label>
            <input id="cs" type="password" className="input" autoComplete="new-password" value={f.senha} onChange={set('senha')} required minLength={10} />
            <span className="hint">Mínimo de 10 caracteres, com letras e números.</span></div>
          <div className="field"><label htmlFor="cc">Confirmar senha</label><input id="cc" type="password" className="input" autoComplete="new-password" value={f.confirma} onChange={set('confirma')} required /></div>
          {erro && <div className="error-text">{erro}</div>}
          <button className="btn btn-primary btn-block" disabled={!disponivel || ocupado}>{ocupado ? 'Criando…' : 'Criar conta'}</button>
          <p className="xsmall muted" style={{ margin: 0 }}>Depois de criar a conta, vincule sua conta Google em Configurações: ela será usada para recuperar o acesso se você esquecer a senha.</p>
          <p className="small" style={{ textAlign: 'center', margin: 0 }}>Já tem conta? <Link to="/entrar">Entrar</Link></p>
        </div>
      </form>
    </div>
  );
}

/** Aberta no celular ao ler o QR Code: confirma o login do outro dispositivo. */
export function QrAprovar() {
  const { id } = useParams();
  const [sp] = useSearchParams();
  const { user, perfil, disponivel, entrarComGoogle } = useAuth();
  const [estado, setEstado] = useState(null);
  const aprovar = async () => {
    setEstado('enviando');
    try { await api.post(`/auth/qr/${id}/aprovar`, { segredo: sp.get('s') }); setEstado('ok'); }
    catch (e) { setEstado(e.message); }
  };
  return (
    <div className="container page">
      <div className="auth-wrap panel">
        <div className="panel-head"><Icon name="qr" /><h1 style={{ fontSize: '1.3rem', margin: 0 }}>Confirmar acesso</h1></div>
        <div className="panel-body stack">
          {!disponivel && <Indisponivel />}
          {disponivel && !user && (
            <>
              <p>Entre neste dispositivo para autorizar o acesso no outro.</p>
              <button className="btn btn-ghost btn-block" onClick={() => entrarComGoogle()}><Icon name="google" /> Entrar com Google</button>
              <Link className="btn btn-primary btn-block" to="/entrar">Entrar com usuário e senha</Link>
            </>
          )}
          {user && estado !== 'ok' && (
            <>
              <Notice kind="warn">Só confirme se foi <strong>você</strong> quem abriu o QR Code agora, em um dispositivo seu.</Notice>
              <p>Autorizar o acesso como <strong>{perfil?.username || user.displayName || 'você'}</strong>?</p>
              <button className="btn btn-primary btn-block" onClick={aprovar} disabled={estado === 'enviando'}>Autorizar acesso</button>
              {estado && estado !== 'enviando' && <div className="error-text">{estado}</div>}
            </>
          )}
          {estado === 'ok' && <Notice kind="ok">Acesso autorizado. Você já pode voltar ao outro dispositivo.</Notice>}
        </div>
      </div>
    </div>
  );
}
