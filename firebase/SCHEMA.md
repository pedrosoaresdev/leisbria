# Modelo de dados no Firestore

O Firestore guarda **dados de usuários e estado operacional**. O acervo jurídico (milhões de
documentos) fica no OpenSearch/Elasticsearch, que é o motor adequado para busca textual e escala.

| Caminho | Conteúdo |
|---|---|
| `usernames/{username}` | `{ uid, criadoEm }` — reserva única do nome de usuário (transação) |
| `users/{uid}` | `{ username, nome, emailTecnico, provedores[], criadoEm, atualizadoEm }` |
| `users/{uid}/favoritos/{id}` | `{ tipo: dispositivo\|norma\|decisao, refId, titulo, fonte, url, pastaId, nota, criadoEm }` |
| `users/{uid}/pastas/{id}` | `{ nome, descricao, criadoEm }` |
| `users/{uid}/pesquisas/{id}` | `{ consulta, aba, filtros{}, nome, criadoEm }` |
| `users/{uid}/historico/{id}` | `{ consulta, aba, totalResultados, criadoEm }` |
| `users/{uid}/alertas/{id}` | `{ termos, tribunais[], ativo, ultimaVerificacao, criadoEm }` |
| `users/{uid}/notificacoes/{id}` | `{ alertaId, titulo, tribunal, numeroProcesso, documentoId, urlOficial, lida, criadoEm }` |
| `qrSessions/{id}` | `{ secretHash, status: PENDENTE\|APROVADA\|CONSUMIDA\|EXPIRADA, uid, expiraEm, userAgent }` |
| `sources/{fonteId}` | `{ nome, estado, ultimaSincronizacao, ultimoSucesso, ultimoErro, cursor{}, documentos }` |
| `syncRuns/{id}` | `{ fonteId, iniciadoEm, finalizadoEm, status, lidos, gravados, ignorados, duplicados, erros[] }` |
| `processedResources/{hash}` | Controle de recursos já importados (ex.: arquivos JSON do STJ) |

## "Migrations"

O Firestore não possui esquema rígido. A evolução é tratada assim:

1. **OpenSearch**: arquivos `backend/src/main/resources/opensearch/<indice>.v<N>.json`.
   O `IndexMigrator` cria `prefixo_<indice>_v<N>` e aponta o alias `prefixo_<indice>` para a
   versão mais recente. Para mudar um mapeamento, crie `v2`, reindexe e o alias é trocado.
2. **Firestore**: regras e índices em `firebase/` — publique com `firebase deploy --only firestore`.
