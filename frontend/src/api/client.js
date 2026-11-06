import { API_URL, PREVIEW } from '../config';
import { auth } from '../firebase';
import { previewHandle } from './preview';

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

async function authHeader() {
  const user = auth?.currentUser;
  if (!user) return {};
  const token = await user.getIdToken();
  return { Authorization: `Bearer ${token}` };
}

function toQuery(params = {}) {
  const usp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '') return;
    if (Array.isArray(v)) v.forEach((x) => usp.append(k, x));
    else usp.append(k, String(v));
  });
  const s = usp.toString();
  return s ? `?${s}` : '';
}

export async function request(method, path, { params, body, signal } = {}) {
  const url = `${API_URL}/api${path}${toQuery(params)}`;
  if (PREVIEW) {
    return previewHandle(method, path, params, body);
  }
  let res;
  try {
    res = await fetch(url, {
      method,
      signal,
      headers: {
        Accept: 'application/json',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(await authHeader())
      },
      body: body ? JSON.stringify(body) : undefined
    });
  } catch (e) {
    if (e.name === 'AbortError') throw e;
    throw new ApiError('Não foi possível conectar ao servidor. Verifique sua conexão.', 0);
  }
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!res.ok) {
    throw new ApiError(data?.erro || `Erro ${res.status}`, res.status);
  }
  return data;
}

export const api = {
  get: (path, params, opts = {}) => request('GET', path, { params, ...opts }),
  post: (path, body, opts = {}) => request('POST', path, { body, ...opts }),
  put: (path, body) => request('PUT', path, { body }),
  del: (path) => request('DELETE', path)
};
