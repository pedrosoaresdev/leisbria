import { Link } from 'react-router-dom';
import Icon from './Icon';
import { Badge, Highlight, formatData } from './ui';
import FavoriteButton from './FavoriteButton';

const CATEGORIAS = {
  SUMULA: ['Súmula', 'gold'],
  TEMA_REPETITIVO: ['Tema repetitivo', 'violet'],
  REPERCUSSAO_GERAL: ['Repercussão geral', 'violet'],
  CONTROLE_CONCENTRADO: ['Controle concentrado', 'violet'],
  ACORDAO: ['Acórdão', 'navy'],
  DECISAO_MONOCRATICA: ['Decisão monocrática', 'navy'],
  SENTENCA: ['Sentença', 'navy'],
  COMUNICACAO: ['Publicação no DJEN', 'gray']
};

export function linkInterno(r) {
  if (r.tipo === 'dispositivo') return `/norma/${encodeURIComponent(r.normaId)}?d=${encodeURIComponent(r.id)}`;
  if (r.tipo === 'norma') return `/norma/${encodeURIComponent(r.id)}`;
  return `/decisao/${encodeURIComponent(r.id)}`;
}

export default function ResultCard({ r }) {
  const cat = CATEGORIAS[r.categoria];
  return (
    <article className="card">
      <div className="card-top">
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="card-meta" style={{ marginBottom: 4 }}>
            {r.tipo === 'dispositivo' && <Badge color="navy">Texto legal</Badge>}
            {r.tipo === 'norma' && <Badge color="navy">{r.categoria || 'Norma'}</Badge>}
            {cat && <Badge color={cat[1]}>{cat[0]}</Badge>}
            {r.tribunal && <Badge color="gray">{r.tribunal}</Badge>}
            {r.statusPublicacao === 'PUBLICADA' && <Badge color="green" title="Publicada em diário oficial">Publicada</Badge>}
            {r.statusPublicacao === 'JULGADA' && <Badge color="amber" title="Julgamento registrado; publicação ainda não localizada">Julgada (não publicada)</Badge>}
            {r.vigente === false && <Badge color="red">Redação anterior / revogada</Badge>}
            {r.data && <span className="xsmall muted">{formatData(r.data)}</span>}
          </div>
          <h3 className="card-title"><Link to={linkInterno(r)}>{r.titulo}</Link></h3>
          {r.subtitulo && <div className="card-sub">{r.subtitulo}</div>}
        </div>
        <FavoriteButton item={r} />
      </div>
      {r.trecho && <div className="card-text"><Highlight text={r.trecho} /></div>}
      {r.relevancia && <div className="card-why"><strong>Por que apareceu:</strong> {r.relevancia}</div>}
      <div className="card-actions">
        <Link to={linkInterno(r)} className="btn btn-primary btn-sm">
          <Icon name="file" size={16} /> {r.tipo === 'decisao' ? 'Ver decisão completa' : 'Abrir no leitor'}
        </Link>
        {r.urlOficial && (
          <a href={r.urlOficial} target="_blank" rel="noreferrer noopener" className="btn btn-ghost btn-sm">
            <Icon name="external" size={16} /> Fonte oficial
          </a>
        )}
        <span className="spacer" />
        <span className="xsmall muted" title="Origem do documento">Fonte: {r.fonteNome || r.fonte}</span>
      </div>
    </article>
  );
}
