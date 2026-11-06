import { initializeApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider } from 'firebase/auth';
import { FIREBASE_CONFIG, FIREBASE_ENABLED } from './config';

let auth = null;
let googleProvider = null;

if (FIREBASE_ENABLED) {
  const app = initializeApp(FIREBASE_CONFIG);
  auth = getAuth(app);
  auth.languageCode = 'pt-BR';
  googleProvider = new GoogleAuthProvider();
  googleProvider.setCustomParameters({ prompt: 'select_account' });
}

export { auth, googleProvider };
