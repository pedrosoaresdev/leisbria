import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import Icon, { BrandMark } from './Icon';
import { useAuth } from '../auth/AuthContext';
import { PREVIEW } from '../config';
import { api } from '../api/client';

const LINKS = [
  ['/legislacao', 'Legislação'],
  ['/constituicao', 'Constituição'],
  ['/jurisprudencia', 'Jurisprudência'],
  ['/controle', 'Controle Constitucional'],
  ['/radar', 'Radar Jurídico']
];

function Brand() {
  return (
    <Link to="/" className="brand" aria-label="Pandecta — início">
      <BrandMark />
      <span>Pandecta<small>Pesquisa jurídica</small></span>
    </Link>
  );
}

function HeaderSearch() {
  const [q, setQ] = useState('');
  const nav = useNavigate();
  return (
    <form className="header-search" role="search" onSubmit={(e) => { e.preventDefault(); if (q.trim()) nav(`/busca?q=${encodeURIComponent(q.trim())}`); }}>
      <div className="search-compact">
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Pesquisar lei, artigo, súmula, processo…" aria-label="Pesquisar" style={{ color: 'var(--gray-900)' }} />
        <button aria-label="Pesquisar"><Icon name="search" /></button>
      </div>
    </form>
  );
}

function Header() {
  const [aberto, setAberto] = useState(false);
  const [naoLidas, setNaoLidas] = useState(0);
  const { user, perfil, sair } = useAuth();
  const loc = useLocation();
  const isHome = loc.pathname === '/';

  useEffect(() => setAberto(false), [loc.pathname]);
  useEffect(() => {
    if (!user) { setNaoLidas(0); return undefined; }
    const carregar = () => api.get('/me/notificacoes', { somenteNaoLidas: true, tamanho: 1 })
      .then((r) => setNaoLidas(r?.total || 0)).catch(() => {});
    carregar();
    const t = setInterval(carregar, 120000);
    return () => clearInterval(t);
  }, [user]);

  return (
    <header className="header">
      <div className="container header-inner">
        <button className="icon-btn menu-btn" onClick={() => setAberto(true)} aria-label="Abrir menu"><Icon name="menu" size={22} /></button>
        <Brand />
        <nav className="nav" aria-label="Principal">
          {LINKS.map(([to, label]) => <NavLink key={to} to={to}>{label}</NavLink>)}
        </nav>
        <div className="header-actions">
          {!isHome && <HeaderSearch />}
          {user ? (
            <>
              <Link to="/notificacoes" className="icon-btn" aria-label={`Notificações${naoLidas ? `: ${naoLidas} não lidas` : ''}`}>
                <Icon name="bell" size={20} />{naoLidas > 0 && <span className="dot">{naoLidas > 99 ? '99+' : naoLidas}</span>}
              </Link>
              <Link to="/meus-itens" className="icon-btn" aria-label="Favoritos e pesquisas"><Icon name="star" size={20} /></Link>
              <Link to="/conta" className="icon-btn" aria-label={`Conta de ${perfil?.username || 'usuário'}`}><Icon name="user" size={20} /></Link>
            </>
          ) : (
            <Link to="/entrar" className="btn btn-ghost btn-sm">Entrar</Link>
          )}
          <Link to="/apoiar" className="btn btn-gold btn-sm apoiar-btn">Apoiar</Link>
        </div>
      </div>
      {aberto && (
        <>
          <div className="drawer-backdrop" onClick={() => setAberto(false)} />
          <nav className="drawer" aria-label="Menu">
            <div className="row" style={{ marginBottom: 8 }}>
              <Brand /><span className="spacer" />
              <button className="icon-btn" onClick={() => setAberto(false)} aria-label="Fechar menu"><Icon name="close" /></button>
            </div>
            <NavLink to="/" end>Início</NavLink>
            {LINKS.map(([to, label]) => <NavLink key={to} to={to}>{label}</NavLink>)}
            <hr />
            {user ? (
              <>
                <NavLink to="/meus-itens">Favoritos, pastas e pesquisas</NavLink>
                <NavLink to="/notificacoes">Notificações</NavLink>
                <NavLink to="/conta">Minha conta</NavLink>
                <a href="#sair" onClick={(e) => { e.preventDefault(); sair(); }}>Sair</a>
              </>
            ) : (
              <>
                <NavLink to="/entrar">Entrar</NavLink>
                <NavLink to="/cadastro">Criar conta</NavLink>
              </>
            )}
            <hr />
            <NavLink to="/fontes">Fontes oficiais e sincronização</NavLink>
            <NavLink to="/sobre">Sobre a plataforma</NavLink>
            <NavLink to="/apoiar">Apoie o projeto (Bitcoin)</NavLink>
          </nav>
        </>
      )}
    </header>
  );
}

function Footer() {
  return (
    <footer className="footer no-print">
      <div className="container footer-grid">
        <div>
          <div className="row" style={{ color: '#fff', fontFamily: 'var(--font-serif)', fontSize: '1.1rem' }}>Pandecta</div>
          <p style={{ marginTop: 8 }}>
            Legislação e jurisprudência obtidas de fontes oficiais. Resumos produzidos por IA são sempre identificados
            e não substituem a leitura do documento original nem a orientação de um advogado.
          </p>
        </div>
        <div className="stack" style={{ gap: 6 }}>
          <strong style={{ color: '#fff' }}>Plataforma</strong>
          <Link to="/fontes">Fontes e sincronização</Link>
          <Link to="/sobre">Sobre e metodologia</Link>
          <Link to="/apoiar" style={{ color: 'var(--gold-500)' }}>Apoie com Bitcoin</Link>
        </div>
        <div className="stack" style={{ gap: 6 }}>
          <strong style={{ color: '#fff' }}>Fontes oficiais</strong>
          <a href="https://www.planalto.gov.br/ccivil_03/" target="_blank" rel="noreferrer">Planalto</a>
          <a href="https://www.lexml.gov.br/" target="_blank" rel="noreferrer">LexML</a>
          <a href="https://www.cnj.jus.br/sistemas/datajud/api-publica/" target="_blank" rel="noreferrer">DataJud (CNJ)</a>
          <a href="https://comunica.pje.jus.br/" target="_blank" rel="noreferrer">DJEN</a>
        </div>
      </div>
    </footer>
  );
}

export default function Layout() {
  const loc = useLocation();
  useEffect(() => { if (!loc.hash) window.scrollTo(0, 0); }, [loc.pathname]);
  return (
    <div className="app">
      <a href="#conteudo" className="sr-only">Pular para o conteúdo</a>
      <Header />
      {PREVIEW && (
        <div className="preview-banner no-print">
          <div className="container"><Icon name="info" />
            <span><strong>Pré-visualização da interface.</strong> Backend desconectado: só há um trecho real da Constituição; jurisprudência, radar e IA aparecem vazios até a sincronização com as fontes oficiais.</span>
          </div>
        </div>
      )}
      <main id="conteudo" className="main"><Outlet /></main>
      <Footer />
    </div>
  );
}
