import React, { lazy, Suspense } from 'react';
import ReactDOM from 'react-dom/client';
import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import './styles/global.css';
import { AuthProvider } from './auth/AuthContext';
import { Spinner, ToastProvider } from './components/ui';
import Layout from './components/Layout';
import Home from './pages/Home';
import Search from './pages/Search';

const NormaViewer = lazy(() => import('./pages/NormaViewer'));
const Legislacao = lazy(() => import('./pages/Legislacao'));
const Jurisprudencia = lazy(() => import('./pages/Jurisprudencia'));
const DecisaoViewer = lazy(() => import('./pages/DecisaoViewer'));
const Radar = lazy(() => import('./pages/Radar'));
const Controle = lazy(() => import('./pages/Controle'));
const Fontes = lazy(() => import('./pages/Fontes'));
const Conta = lazy(() => import('./pages/Conta'));
const MeusItens = lazy(() => import('./pages/MeusItens'));
const Entrar = lazy(() => import('./pages/Auth').then((m) => ({ default: m.Entrar })));
const Cadastro = lazy(() => import('./pages/Auth').then((m) => ({ default: m.Cadastro })));
const QrAprovar = lazy(() => import('./pages/Auth').then((m) => ({ default: m.QrAprovar })));
const Notificacoes = lazy(() => import('./pages/Misc').then((m) => ({ default: m.Notificacoes })));
const Sobre = lazy(() => import('./pages/Misc').then((m) => ({ default: m.Sobre })));
const NotFound = lazy(() => import('./pages/Misc').then((m) => ({ default: m.NotFound })));

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <HashRouter>
      <AuthProvider>
        <ToastProvider>
          <Suspense fallback={<Spinner />}>
            <Routes>
              <Route element={<Layout />}>
                <Route index element={<Home />} />
                <Route path="busca" element={<Search />} />
                <Route path="legislacao" element={<Legislacao />} />
                <Route path="constituicao" element={<Navigate to="/norma/cf1988" replace />} />
                <Route path="norma/:id" element={<NormaViewer />} />
                <Route path="jurisprudencia" element={<Jurisprudencia />} />
                <Route path="decisao/:id" element={<DecisaoViewer />} />
                <Route path="radar" element={<Radar />} />
                <Route path="controle" element={<Controle />} />
                <Route path="fontes" element={<Fontes />} />
                <Route path="sobre" element={<Sobre />} />
                <Route path="entrar" element={<Entrar />} />
                <Route path="cadastro" element={<Cadastro />} />
                <Route path="qr/:id" element={<QrAprovar />} />
                <Route path="conta" element={<Conta />} />
                <Route path="meus-itens" element={<MeusItens />} />
                <Route path="notificacoes" element={<Notificacoes />} />
                <Route path="*" element={<NotFound />} />
              </Route>
            </Routes>
          </Suspense>
        </ToastProvider>
      </AuthProvider>
    </HashRouter>
  </React.StrictMode>
);
