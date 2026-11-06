import { Badge, Notice, Spinner, formatData, useAsync } from '../components/ui';
import { api } from '../api/client';

const ESTADOS = {
  ATIVA: ['Ativa', 'green', '#1f7a4d'],
  SINCRONIZANDO: ['Sincronizando', 'navy', '#1d4275'],
  ERRO: ['Com erro', 'red', '#b42318'],
  INDISPONIVEL: ['Fonte indisponível', 'amber', '#a35d00'],
  NAO_CONFIGURADA: ['Requer configuração', 'amber', '#a35d00'],
  PENDENTE: ['Integração pendente', 'gray', '#7a8494'],
  PRE_VISUALIZACAO: ['Não conectada (pré-visualização)', 'gray', '#7a8494']
};

export default function Fontes() {
  const { loading, data, error } = useAsync(() => api.get('/fontes'), []);
  return (
    <div className="container page">
      <div className="page-title"><h1>Fontes oficiais e sincronização</h1><p>Origem de cada dado, situação da integração e horário da última atualização.</p></div>
      <Notice kind="info">
        Nem todos os tribunais oferecem API pública ou atualização instantânea. Fontes marcadas como <strong>integração pendente</strong> não alimentam a base:
        a plataforma não simula dados para elas.
      </Notice>
      {error && <Notice kind="error">{error.message}</Notice>}
      {loading ? <Spinner /> : (
        <div className="grid" style={{ marginTop: 16, gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))' }}>
          {data?.map((f) => {
            const [rotulo, cor, dot] = ESTADOS[f.estado] || [f.estado, 'gray', '#7a8494'];
            return (
              <article key={f.id} className="card">
                <div className="row"><span className="status-dot" style={{ background: dot }} /><h3 className="card-title" style={{ margin: 0 }}>{f.nome}</h3></div>
                <div><Badge color={cor}>{rotulo}</Badge></div>
                <p className="small" style={{ margin: 0 }}>{f.descricao}</p>
                <dl className="kv small">
                  <dt>Última sincronização</dt><dd>{formatData(f.ultimaSincronizacao, true)}</dd>
                  <dt>Último sucesso</dt><dd>{formatData(f.ultimoSucesso, true)}</dd>
                  <dt>Documentos</dt><dd>{f.documentos != null ? f.documentos.toLocaleString('pt-BR') : '—'}</dd>
                  {f.frequencia && <><dt>Frequência</dt><dd>{f.frequencia}</dd></>}
                </dl>
                {f.ultimoErro && <Notice kind="error">{f.ultimoErro}</Notice>}
                {f.requisitos && <Notice kind="warn"><strong>O que é necessário:</strong> {f.requisitos}</Notice>}
                {f.observacao && <div className="xsmall muted">{f.observacao}</div>}
                {f.url && <a className="small" href={f.url} target="_blank" rel="noreferrer noopener">Documentação / portal da fonte</a>}
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
