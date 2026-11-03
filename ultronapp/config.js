// Ultronapp settings. Fill apiKey from Firebase console > Project settings > Your apps > Web app (add one if there is none).
// It is a public identifier, not a secret, but it is not the Android key: that one is restricted to the phone app.
// Also add the domain Ultronapp is served from under Authentication > Settings > Authorized domains.
export const config = {
  apiBase: "https://api.devxjitin.dev",
  firebase: {
    apiKey: "",
    authDomain: "ultron-5350c.firebaseapp.com",
    projectId: "ultron-5350c",
  },
};
