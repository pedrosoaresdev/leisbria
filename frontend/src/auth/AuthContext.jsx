import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import {
  onIdTokenChanged, signInWithCustomToken, signInWithPopup, signOut, linkWithPopup, reauthenticateWithPopup,
  GoogleAuthProvider
} from 'firebase/auth';
import { auth, googleProvider } from '../firebase';
import { api } from '../api/client';
import { FIREBASE_ENABLED, PREVIEW } from '../config';

const AuthCtx = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [perfil, setPerfil] = useState(null);
  const [carregando, setCarregando] = useState(FIREBASE_ENABLED && !PREVIEW);

  useEffect(() => {
    if (!auth || PREVIEW) return undefined;
    return onIdTokenChanged(auth, async (u) => {
      setUser(u);
      if (u) {
        try {
          setPerfil(await api.get('/me'));
        } catch {
          setPerfil(null);
        }
      } else {
        setPerfil(null);
      }
      setCarregando(false);
    });
  }, []);

  const value = useMemo(() => ({
    user,
    perfil,
    carregando,
    disponivel: FIREBASE_ENABLED && !PREVIEW,
    async recarregarPerfil() {
      if (auth?.currentUser) setPerfil(await api.get('/me'));
    },
    /** Login por nome de usuário e senha: o backend valida e devolve um token personalizado do Firebase. */
    async entrar(username, senha) {
      const { customToken } = await api.post('/auth/entrar', { username, senha });
      await signInWithCustomToken(auth, customToken);
    },
    async registrar(dados) {
      const { customToken } = await api.post('/auth/registrar', dados);
      await signInWithCustomToken(auth, customToken);
    },
    async entrarComToken(customToken) {
      await signInWithCustomToken(auth, customToken);
    },
    async entrarComGoogle() {
      await signInWithPopup(auth, googleProvider);
    },
    async vincularGoogle() {
      await linkWithPopup(auth.currentUser, new GoogleAuthProvider());
      await auth.currentUser.reload();
      setPerfil(await api.get('/me'));
    },
    /** Reautenticação recente exigida pelo servidor antes de trocar a senha. */
    async reautenticarGoogle() {
      await reauthenticateWithPopup(auth.currentUser, new GoogleAuthProvider());
      await auth.currentUser.getIdToken(true);
    },
    async sair() {
      await signOut(auth);
    }
  }), [user, perfil, carregando]);

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

export const useAuth = () => useContext(AuthCtx);
