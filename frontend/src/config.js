export const API_URL = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');
export const PREVIEW = import.meta.env.VITE_PREVIEW === 'true';

export const FIREBASE_CONFIG = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID
};
export const FIREBASE_ENABLED = Boolean(FIREBASE_CONFIG.apiKey && FIREBASE_CONFIG.projectId);

export const ABAS = [
  { id: 'todos', rotulo: 'Visão geral' },
  { id: 'legislacao', rotulo: 'Legislação' },
  { id: 'constituicao', rotulo: 'Constituição Federal' },
  { id: 'jurisprudencia', rotulo: 'Jurisprudência' },
  { id: 'stf', rotulo: 'STF' },
  { id: 'stj', rotulo: 'STJ' },
  { id: 'tjs', rotulo: 'Tribunais de Justiça' },
  { id: 'sumulas', rotulo: 'Súmulas' },
  { id: 'temas', rotulo: 'Temas repetitivos' },
  { id: 'controle', rotulo: 'Controle de constitucionalidade' },
  { id: 'doutrina', rotulo: 'Doutrina' }
];

export const ABAS_LEGISLACAO = ['legislacao', 'constituicao'];

export const TRIBUNAIS = {
  superiores: ['STF', 'STJ', 'TST', 'TSE', 'STM'],
  federais: ['TRF1', 'TRF2', 'TRF3', 'TRF4', 'TRF5', 'TRF6'],
  estaduais: ['TJAC', 'TJAL', 'TJAM', 'TJAP', 'TJBA', 'TJCE', 'TJDFT', 'TJES', 'TJGO', 'TJMA', 'TJMG', 'TJMS', 'TJMT',
    'TJPA', 'TJPB', 'TJPE', 'TJPI', 'TJPR', 'TJRJ', 'TJRN', 'TJRO', 'TJRR', 'TJRS', 'TJSC', 'TJSE', 'TJSP', 'TJTO'],
  trabalho: Array.from({ length: 24 }, (_, i) => `TRT${i + 1}`),
  eleitorais: ['AC', 'AL', 'AM', 'AP', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MG', 'MS', 'MT', 'PA', 'PB', 'PE', 'PI', 'PR',
    'RJ', 'RN', 'RO', 'RR', 'RS', 'SC', 'SE', 'SP', 'TO'].map((uf) => `TRE-${uf}`),
  militares: ['TJMMG', 'TJMRS', 'TJMSP']
};

export const UFS = ['AC', 'AL', 'AM', 'AP', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MG', 'MS', 'MT', 'PA', 'PB', 'PE', 'PI',
  'PR', 'RJ', 'RN', 'RO', 'RR', 'RS', 'SC', 'SE', 'SP', 'TO'];

export const CLASSES_CONTROLE = [
  { id: 'ADI', nome: 'Ação Direta de Inconstitucionalidade' },
  { id: 'ADC', nome: 'Ação Declaratória de Constitucionalidade' },
  { id: 'ADPF', nome: 'Arguição de Descumprimento de Preceito Fundamental' },
  { id: 'ADO', nome: 'Ação Direta de Inconstitucionalidade por Omissão' },
  { id: 'MI', nome: 'Mandado de Injunção' },
  { id: 'SV', nome: 'Súmula Vinculante' },
  { id: 'RG', nome: 'Repercussão Geral' },
  { id: 'REP', nome: 'Recursos Repetitivos' }
];

export const EXEMPLOS = [
  'Uma pessoa foi presa em flagrante e permaneceu presa por mais de 24 horas sem audiência de custódia. Quais dispositivos legais se aplicam?',
  'Quais são os direitos de uma pessoa que teve sua conta bancária bloqueada indevidamente?',
  'art. 5º, inciso LXIII',
  'Lei 11.340/2006 medidas protetivas de urgência',
  'Súmula vinculante 11'
];
