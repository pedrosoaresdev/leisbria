# Pandecta — Pesquisa Jurídica Brasileira

Plataforma de pesquisa de **legislação**, **jurisprudência** e **decisões judiciais** do Brasil, com
**IA jurídica (RAG)** que só cita documentos existentes na base, **Radar Jurídico**, **controle de
constitucionalidade**, leitor de normas e área pessoal (favoritos, pastas, histórico, alertas).

> **Princípio do projeto:** nada é inventado. Todo dado exibido vem de uma fonte oficial identificada,
> com link para o original e horário da sincronização. Fontes sem integração aparecem como
> **pendentes** na página *Fontes*, sem dados simulados. A única exceção é o modo de **pré-visualização**
> da interface, que é sinalizado e usa somente um trecho real da Constituição.

---

## 1. Arquitetura

```
                ┌───────────────────────────── Frontend (React + Vite) ─────────────────────────────┐
 Navegador ───▶ │ Busca · IA · Leitor de normas · Jurisprudência · Radar · Controle · Conta · Apoiar │
                └───────────────┬────────────────────────────────────────────┬────────────────────┘
                                │ REST /api (JSON)                            │ Firebase Auth (JS SDK)
                ┌───────────────▼───────────── Backend (Java 21 + Spring Boot 3) ─────────────────────┐
                │ Segurança: token Firebase verificado · rate limiting · headers · validação          │
                │ API: busca, legislação, jurisprudência, processos, radar, controle, IA, conta       │
                │ RAG: interpretação → recuperação → geração com fontes [S#] → verificação            │
                │ Sincronização: conectores independentes + agendador + status/histórico              │
                └──────┬───────────────────────┬──────────────────────────┬───────────────────────────┘
                       │                       │                          │
          ┌────────────▼──────────┐  ┌─────────▼──────────┐   ┌───────────▼────────────────────────┐
          │ OpenSearch/Elastic    │  │ Firebase           │   │ Fontes oficiais                     │
          │ normas · dispositivos │  │ Auth (usuários)    │   │ Planalto · LexML · DataJud (CNJ)    │
          │ jurisprudência ·      │  │ Firestore (perfil, │   │ DJEN · STJ Dados Abertos            │
          │ radar · processos     │  │ favoritos, status) │   │ (+ pendentes: STF, TST, TJs, DOU…)  │
          └───────────────────────┘  └────────────────────┘   └─────────────────────────────────────┘
```

**Por que dois bancos?** O acervo jurídico (milhões de documentos, busca textual em português,
facetas, destaque de termos) fica no **OpenSearch/Elasticsearch**. O **Firestore** guarda dados de
usuários e o estado operacional das sincronizações. O cliente nunca acessa o Firestore diretamente
(regras negam tudo); todo acesso passa pelo backend.

## 2. Estrutura de diretórios

```
leisbria/
├── docker-compose.yml           # OpenSearch + backend + frontend
├── .env.example                 # todas as variáveis, comentadas
├── firebase/                    # regras, índices e esquema do Firestore
├── backend/
│   ├── pom.xml · Dockerfile
│   └── src/main/
│       ├── resources/
│       │   ├── application.yml
│       │   ├── opensearch/*.v1.json        # "migrations" dos índices (versionadas + alias)
│       │   └── sources/catalogo-normas.json # normas com texto integral (Planalto)
│       └── java/br/com/pandecta/
│           ├── api/          # controllers REST
│           ├── ai/           # LlmClient, RagService, CitationVerifier (anti-alucinação)
│           ├── auth/         # usuário/senha, Google, troca de senha, login por QR Code
│           ├── common/       # erros, sanitização (anti-XSS), utilitários
│           ├── config/       # propriedades, Firebase, HTTP, CORS, cache
│           ├── connectors/   # um pacote por fonte + orquestrador e status
│           │   ├── planalto/ (PlanaltoParser: HTML → artigos/§/incisos/alíneas, redações tachadas, ADCT)
│           │   ├── lexml/  datajud/  djen/  stj/
│           │   └── FontesPendentes.java (o que falta e o que é preciso para integrar)
│           ├── legislation/  # catálogo, ids de dispositivos, resolução "art. 5º, LXIII"
│           ├── search/       # cliente OpenSearch, migrator, consultas, parser de citações
│           ├── security/     # filtro de token Firebase, rate limit, cabeçalhos
│           └── user/         # favoritos, pastas, pesquisas, histórico, alertas, notificações
└── frontend/
    ├── package.json · vite.config.js · Dockerfile · nginx.conf
    └── src/
        ├── api/        # client.js (REST) e preview.js (modo pré-visualização)
        ├── auth/       # AuthContext (Firebase)
        ├── components/ # Layout, SearchBox, ResultCard, AiPanel, filtros, UI
        ├── pages/      # Home, Search, NormaViewer, Jurisprudencia, Radar, Controle, Fontes, Conta, Apoiar…
        └── styles/global.css  # identidade visual (azul-marinho, branco, cinza, dourado), mobile first
```

## 3. Fontes oficiais: o que está integrado

| Fonte | Como | O que traz | Configuração |
|---|---|---|---|
| **Planalto** | HTML compilado (não há API) | Texto integral, redações anteriores (tachadas), notas de alteração, estrutura | Nenhuma. Normas em `catalogo-normas.json` |
| **LexML** | Webservice SRU (XML) | Metadados e ementas de normas federais do ano corrente | Nenhuma |
| **DataJud (CNJ)** | API pública (Elasticsearch) | Capa e movimentações de processos; consulta por número em tempo real | `DATAJUD_API_KEY` (chave **pública** da wiki do CNJ) |
| **DJEN** | API pública `comunicaapi.pje.jus.br` | Acórdãos, sentenças e decisões publicados | Tribunais em `DJEN_TRIBUNAIS` |
| **STJ — Dados Abertos** | CKAN + arquivos JSON | Espelhos de acórdãos: ementa, tese, tema, referências legislativas | Nenhuma |

**Pendentes (sem dados na base, listadas em `/fontes` com o requisito):** STF (sem API pública de
jurisprudência; exige convênio), bases próprias de TST/TRTs, TJs, TSE/TREs, STM, Diário Oficial da
União (INLABS exige cadastro), Câmara/Senado (proposições) e doutrina (exige licença).

Cada conector tem: requisições com User-Agent identificado, backoff em 429/5xx, distinção entre
*fonte indisponível* e *erro*, cursor incremental, ids determinísticos (sem duplicidade), registro da
origem/horário em cada documento e histórico de execuções (`syncRuns`).

**Publicada × julgada:** eventos do DJEN/STJ são `PUBLICADA` (a data do DJEN é a de disponibilização).
Movimentações de julgamento do DataJud viram eventos `JULGADA` até a publicação ser localizada.

## 4. IA jurídica (RAG) e verificação

1. **Interpretação** — o modelo extrai assuntos, termos técnicos e *pistas* de dispositivos.
2. **Recuperação** — busca na base (dispositivos + jurisprudência). Pistas só entram se o dispositivo **existir no índice**.
3. **Geração** — o modelo recebe apenas as fontes numeradas `[S1…Sn]` e devolve JSON com seções separadas.
4. **Verificação** (`CitationVerifier`) — remove `[S#]` inexistentes e **frases** que mencionem artigo, lei,
   súmula, tema ou número de processo ausentes das fontes; o que for removido aparece em *Informação não confirmada*.

A interface separa: **texto literal da legislação** (vem do índice, nunca do modelo), **resumo da IA**,
**entendimento jurisprudencial**, **interpretação** e **informação não confirmada**. Sem chave de IA, a
busca funciona normalmente e o painel informa que a IA está desativada.

## 5. Autenticação

- **Cadastro/login com nome de usuário e senha**: o backend reserva o username (transação no Firestore),
  cria a conta no Firebase Auth com e-mail técnico interno (nunca exibido), valida a senha no Identity
  Toolkit e devolve um *custom token*. Mensagens de erro são genéricas.
- **Google**: login pelo Firebase JS SDK; quem entra primeiro pelo Google escolhe um username depois.
- **Esqueceu a senha?** Entre com a conta Google vinculada → *Conta → Configurações → Alterar senha*.
  O servidor exige login feito há no máximo 5 minutos e revoga as outras sessões após a troca.
- **QR Code**: o computador exibe um QR com sessão de 2 minutos; o celular autenticado aprova; o
  computador recebe o token uma única vez. O segredo é guardado só como hash.

## 6. Segurança e desempenho

Token Firebase verificado em toda requisição (com checagem de revogação) · rate limiting por IP
(mais restrito em login e IA) · consultas ao OpenSearch montadas como JSON estruturado (sem injeção;
não há SQL) · HTML externo sanitizado e trechos destacados com marcadores, sem `innerHTML` no React
(anti-XSS) · cabeçalhos de segurança e CSP · CORS restrito · validação de entradas · XML com proteção
XXE · LGPD: destinatários de publicações do DJEN não são armazenados · cache Caffeine · paginação ·
compressão · métricas Prometheus em `/actuator/prometheus`.

## 7. Como executar

### Pré-requisitos
- Docker e Docker Compose (ou Java 21 + Maven 3.9 e Node 20)
- Um projeto Firebase com **Authentication** (provedores *E-mail/senha* e *Google* ativados) e **Firestore**

### Passos
```bash
cp .env.example .env              # preencha as variáveis (veja abaixo)
mkdir secrets                     # coloque aqui firebase-service-account.json
docker compose up -d --build
# Frontend: http://localhost:5173   ·   API: http://localhost:8080/api   ·   OpenSearch: http://localhost:9200
```
Na primeira subida (`SYNC_ON_STARTUP=true`), os índices são criados e as fontes começam a sincronizar.
Acompanhe em **/fontes** ou nos logs: `docker compose logs -f backend`.

Publique as regras e índices do Firestore:
```bash
cd firebase && firebase deploy --only firestore --project SEU_PROJETO
```

### Desenvolvimento sem Docker
```bash
docker compose up -d opensearch
cd backend && mvn spring-boot:run          # lê variáveis do ambiente
cd frontend && npm install && npm run dev  # proxy /api → localhost:8080
```

### Pré-visualização da interface (sem backend)
```bash
cd frontend && npm run build:preview && npx vite preview
```

### Testes
```bash
cd backend && mvn test   # parser do Planalto, parser de citações, verificador anti-alucinação, tribunais
```

## 8. O que precisa ser configurado ou contratado

| Item | Obrigatório? | Onde obter | Custo |
|---|---|---|---|
| Projeto Firebase (Auth + Firestore) + conta de serviço | Sim | console.firebase.google.com | Plano gratuito atende o início; Blaze conforme uso |
| `FIREBASE_WEB_API_KEY` | Sim (login por senha) | Configurações do projeto Firebase | — |
| OpenSearch/Elasticsearch | Sim | Docker local; em produção, Amazon OpenSearch, Elastic Cloud ou cluster próprio | Conforme volume |
| `DATAJUD_API_KEY` | Para DataJud | Chave **pública** em datajud-wiki.cnj.jus.br/api-publica/acesso (o CNJ pode trocá-la) | Gratuito |
| `ANTHROPIC_API_KEY` | Para a IA | console.anthropic.com | Pago por uso |
| STF (jurisprudência, controle concentrado) | Para cobertura do STF | Convênio/solicitação de dados ao STF | Institucional |
| DOU (INLABS) | Para portarias/IN | Cadastro gratuito na Imprensa Nacional | Gratuito |
| Doutrina | Opcional | Licença com editoras | Contratual |

Produção: habilite TLS e o plugin de segurança do OpenSearch, use HTTPS no frontend, configure
`CORS_ALLOWED_ORIGINS`, `FRONTEND_PUBLIC_URL`, backups (snapshots do OpenSearch e exportação agendada
do Firestore) e um rate limiter distribuído (Redis ou gateway) se houver várias instâncias.

## 9. API (resumo)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/busca?q&aba&ordenacao&pagina&…filtros` | Pesquisa unificada, contagem por aba, dispositivo citado |
| POST | `/api/ia/perguntar` `{pergunta}` | Análise RAG com fontes verificáveis |
| GET | `/api/legislacao/normas` · `/normas/{id}` | Lista de normas · norma completa com estrutura |
| GET | `/api/legislacao/resolver?ref=art. 5º, LXIII` | Resolve referência em dispositivo |
| GET | `/api/legislacao/dispositivos/{id}` · `/{id}/historico` | Dispositivo · redações anteriores |
| GET | `/api/jurisprudencia` · `/jurisprudencia/{id}` | Pesquisa com filtros e facetas · decisão |
| GET | `/api/processos/{numeroCnj}` | Consulta em tempo real ao DataJud |
| GET | `/api/radar` · `/api/controle` · `/api/inicio` · `/api/fontes` | Radar · controle · página inicial · status |
| POST | `/api/auth/registrar` · `/entrar` · `/qr` · `/qr/{id}/aprovar` · `/google/username` | Autenticação |
| GET | `/api/auth/qr/{id}?segredo=` | Consulta da sessão de QR |
| GET/POST/PUT/DELETE | `/api/me/…` | Perfil, senha, favoritos, pastas, pesquisas, histórico, alertas, notificações |
| POST | `/api/admin/sync/{fonteId}` | Sincronização manual (administradores) |

## 10. Próximas etapas

1. Busca semântica híbrida (embeddings + k-NN no OpenSearch) somada ao BM25 atual.
2. Integração do STF via convênio ou conjuntos do *Corte Aberta*; DOU via INLABS; proposições da Câmara/Senado.
3. Ampliação do catálogo do Planalto (todas as leis federais via LexML → texto do Planalto).
4. Notificações push (Firebase Cloud Messaging) para alertas.
5. Comparador de redações palavra a palavra e linha do tempo de alterações por norma.

## Apoie o projeto

Doações em Bitcoin: `bc1qv6a27cnp0gmc9nsmz5a88jj4dq73cexgn87ct9` (também na página **/apoiar** da plataforma).

---
*As informações têm caráter informativo e não substituem a consulta a um advogado ou ao documento oficial.*
