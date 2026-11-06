import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Icon from './Icon';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useToast } from './ui';

/** Salva um dispositivo, norma ou decisão nos favoritos do usuário. */
export default function FavoriteButton({ item, small = false }) {
  const { user, disponivel } = useAuth();
  const toast = useToast();
  const nav = useNavigate();
  const [salvo, setSalvo] = useState(false);
  const [ocupado, setOcupado] = useState(false);

  const salvar = async () => {
    if (!disponivel) { toast('Favoritos exigem login (indisponível na pré-visualização).'); return; }
    if (!user) { nav('/entrar'); return; }
    setOcupado(true);
    try {
      await api.post('/me/favoritos', {
        tipo: item.tipo, refId: item.id, titulo: item.titulo, fonte: item.fonteNome || item.fonte,
        url: item.urlOficial || null, normaId: item.normaId || null
      });
      setSalvo(true);
      toast('Salvo em Favoritos');
    } catch (e) {
      toast(e.message);
    } finally {
      setOcupado(false);
    }
  };

  return (
    <button className={`btn btn-ghost ${small ? 'btn-sm' : 'btn-sm'}`} onClick={salvar} disabled={ocupado || salvo}
      aria-label={salvo ? 'Salvo nos favoritos' : 'Salvar nos favoritos'} title="Salvar nos favoritos">
      <Icon name={salvo ? 'starFill' : 'star'} size={16} style={{ color: salvo ? 'var(--gold-500)' : undefined }} />
    </button>
  );
}
