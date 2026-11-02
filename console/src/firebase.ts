import { initializeApp } from 'firebase/app';
import { initializeAppCheck, ReCaptchaEnterpriseProvider } from 'firebase/app-check';
import { getAuth } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';

// These identifiers are public by design. Access is controlled by Auth, the admin claim and Firestore rules.
const app = initializeApp({
  apiKey: 'AIzaSyBbuaJ6f2D7xm1my5mqn4kR4REM5Ut3lJo',
  authDomain: 'ultron-5350c.firebaseapp.com',
  projectId: 'ultron-5350c',
  appId: '1:434457544612:web:e45aa34a29793221a02331',
});

const siteKey = import.meta.env.VITE_RECAPTCHA_SITE_KEY as string | undefined;
if (import.meta.env.DEV) {
  // Prints a debug token in the console to register under App Check > Apps > Debug tokens.
  (self as unknown as { FIREBASE_APPCHECK_DEBUG_TOKEN: boolean }).FIREBASE_APPCHECK_DEBUG_TOKEN = true;
}
if (siteKey || import.meta.env.DEV) {
  initializeAppCheck(app, {
    provider: new ReCaptchaEnterpriseProvider(siteKey ?? 'dev-placeholder'),
    isTokenAutoRefreshEnabled: true,
  });
}

export const auth = getAuth(app);
export const db = getFirestore(app);
