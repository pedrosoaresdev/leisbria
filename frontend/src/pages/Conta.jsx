import { useState } from 'react';
import { Navigate, useSearchParams } from 'react-router-dom';
import Icon from '../components/Icon';
import { Badge, Notice, Spinner, formatData } from '../components/ui';
import { useAuth } from '../auth/AuthContext';
import { api } from '../api/client';

function EscolherUsuario({ onPronto }) {
  const [u, setU] = useState('');
  const [erro, setErro] = useState(null);
  const enviar = async (e) => {
    e.preventDefault(); setErro(null);
    try { await api.post('/auth/google/username', { username: u.trim().toLowerCase() }); onPronto(); } catch (err) { setErro(err.message); }
  };
  return (
    <form className="panel" onSubmit={enviar}>
      <div className="panel-head"><h2>Escolha seu nome de usuário</h2></div>
      <div className="panel-body stack">
        <p className="small" style={{ margin: 0 }}>Você entrou com o Google. Defina um nome de usuário para também poder entrar com usuário e senha.</p>
        <input className="input" value={u} onChange={(e) => setU(e.target.value)} pattern="[a-zA-Z0-9._]{3,30}" required aria-label="Nome de usuário" />
        {erro && <div className="error-text">{erro}</div>}
        <button className="btn btn-primary">Salvar</button>
      </div>
    </form>
  );
}

function AlterarSenha() {
  const { reautenticarGoogle, perfil } = useAuth();
  const [s, setS] = useState({ nova: '', confirma: '' });
  const [msg, setMsg] = useState(null);
  const [precisaReauth, setPrecisaReauth] = useState(false);

  const enviar = async (e) => {
    e.preventDefault(); setMsg(null);
    if (s.nova !== s.confirma) { setMsg({ kind: 'error', text: 'As senhas não conferem.' }); return; }
    try {
      await api.post('/me/senha', { novaSenha: s.nova });
      setS({ nova: '', confirma: '' });
      setMsg({ kind: 'ok', text: 'Senha alterada. Use-a no próximo login com usuário e senha.' });
      setPrecisaReauth(false);
    } catch (err) {
      if (err.status === 403) setPrecisaReauth(true);
      setMsg({ kind: 'error', text: err.message });
    }
  };

  return (
    <form className="panel" onSubmit={enviar} id="alterar-senha">
      <div className="panel-head"><Icon name="user" /><h2>Alterar senha</h2></div>
      <div className="panel-body stack">
        <p className="small muted" style={{ margin: 0 }}>Por segurança, a troca exige um login feito nos últimos 5 minutos. Se você esqueceu a senha, entre com o Google vinculado e altere aqui.</p>
        <div className="field"><label htmlFor="ns">Nova senha</label><input id="ns" type="password" className="input" autoComplete="new-password" minLength={10} required value={s.nova} onChange={(e) => setS({ ...s, nova: e.target.value })} /></div>
        <div className="field"><label htmlFor="cs2">Confirmar nova senha</label><input id="cs2" type="password" className="input" autoComplete="new-password" required value={s.confirma} onChange={(e) => setS({ ...s, confirma: e.target.value })} /></div>
        {msg && <Notice kind={msg.kind}>{msg.text}</Notice>}
        {precisaReauth && perfil?.provedores?.includes('google.com') && (
          <button type="button" className="btn btn-ghost" onClick={async () => { await reautenticarGoogle(); setMsg({ kind: 'info', text: 'Identidade confirmada. Envie a nova senha novamente.' }); }}>
            <Icon name="google" /> Confirmar identidade com Google
          </button>
        )}
        <button className="btn btn-primary">Salvar nova senha</button>
      </div>
    </form>
  );
}

export default function Conta() {
  const { user, perfil, carregando, vincularGoogle, sair, recarregarPerfil, disponivel } = useAuth();
  const [sp] = useSearchParams();
  const [erro, setErro] = useState(null);

  if (!disponivel) return <div className="container page"><Notice kind="warn">Contas indisponíveis nesta pré-visualização.</Notice></div>;
  if (carregando) return <div className="container page"><Spinner /></div>;
  if (!user) return <Navigate to="/entrar" replace />;

  const temGoogle = perfil?.provedores?.includes('google.com');
  return (
    <div className="container page" style={{ maxWidth: 820 }}>
      <div className="page-title"><h1>Minha conta</h1><p>Conta → Configurações</p></div>
      {sp.get('novo') && <div style={{ marginBottom: 12 }}><Notice kind="ok">Conta criada! Vincule sua conta Google abaixo para poder recuperar o acesso.</Notice></div>}
      <div className="stack">
        {perfil && !perfil.username && <EscolherUsuario onPronto={recarregarPerfil} />}
        <section className="panel">
          <div className="panel-head"><h2>Perfil</h2></div>
          <div className="panel-body">
            <dl className="kv">
              <dt>Nome</dt><dd>{perfil?.nome || user.displayName || '—'}</dd>
              <dt>Usuário</dt><dd>{perfil?.username || '—'}</dd>
              <dt>Conta desde</dt><dd>{formatData(perfil?.criadoEm)}</dd>
              <dt>Métodos de login</dt><dd className="row">{(perfil?.provedores || []).map((p) => <Badge key={p} color="navy">{p === 'google.com' ? 'Google' : p === 'password' ? 'Usuário e senha' : p}</Badge>)}</dd>
            </dl>
          </div>
        </section>
        <section className="panel">
          <div className="panel-head"><Icon name="google" /><h2>Conta Google</h2></div>
          <div className="panel-body stack">
            {temGoogle
              ? <Notice kind="ok">Conta Google vinculada. Use-a para entrar caso esqueça a senha.</Notice>
              : <>
                  <p className="small" style={{ margin: 0 }}>Vincule sua conta Google para recuperar o acesso caso esqueça a senha.</p>
                  <button className="btn btn-ghost" onClick={async () => { setErro(null); try { await vincularGoogle(); } catch (e) { setErro(e.code === 'auth/credential-already-in-use' ? 'Essa conta Google já está vinculada a outro usuário.' : 'Não foi possível vincular.'); } }}>
                    <Icon name="google" /> Vincular conta Google
                  </button>
                  {erro && <div className="error-text">{erro}</div>}
                </>}
          </div>
        </section>
        <AlterarSenha />
        <button className="btn btn-danger" onClick={sair}><Icon name="logout" /> Sair</button>
      </div>
    </div>
  );
}
