import { initializeApp, getApps } from 'firebase/app';
import { getAuth, GoogleAuthProvider, signInWithPopup, signOut, type User } from 'firebase/auth';

const apiBase = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
  measurementId: import.meta.env.VITE_FIREBASE_MEASUREMENT_ID,
};
const firebaseReady = Object.values(firebaseConfig).every(Boolean);
const configuredProjectId = firebaseConfig.projectId || '';
const firebaseApp = firebaseReady ? (getApps()[0] ?? initializeApp(firebaseConfig)) : null;
const auth = firebaseApp ? getAuth(firebaseApp) : null;

export interface Product { sku: string; name: string; priceMinor: number; currency?: string; availableStock?: number }
export interface CartItem { sku: string; quantity: number }
export interface Order { orderId: string; status: string; totalMinor?: number | null; currency?: string | null; items?: CartItem[] }

const demoProducts: Product[] = [
  { sku: 'CERAMIC-MUG', name: 'Everyday ceramic mug', priceMinor: 2800 },
  { sku: 'LINEN-TOTE', name: 'Market day tote', priceMinor: 3400 },
  { sku: 'CANDLE-01', name: 'Slow morning candle', priceMinor: 4200 },
  { sku: 'GLASS-CARAFE', name: 'Ribbed glass carafe', priceMinor: 5600 },
  { sku: 'COTTON-THROW', name: 'Sunday cotton throw', priceMinor: 8900 },
  { sku: 'BOWL-SET', name: 'Little things bowl set', priceMinor: 4800 },
  { sku: 'DESK-LAMP', name: 'Soft light desk lamp', priceMinor: 11200 },
  { sku: 'SOAP-TRIO', name: 'Botanical soap trio', priceMinor: 2400 },
];

export async function getProducts(): Promise<Product[]> {
  if (!auth) return demoProducts;
  try {
    const response = await fetch(`${apiBase}/api/products?size=100`, { headers: await authHeaders() });
    if (!response.ok) throw new Error('Catalog unavailable');
    return await response.json() as Product[];
  } catch {
    return demoProducts;
  }
}

export async function authHeaders(): Promise<Record<string, string>> {
  if (!auth && !configuredProjectId) {
    const configuration = await fetch(`${apiBase}/actuator/health`, { signal: AbortSignal.timeout(2000) }).catch(() => null);
    if (configuration) throw new Error('Firebase is not configured in frontend/.env.local yet.');
    throw new Error('Backend gateway is not reachable. Start the backend before continuing.');
  }
  if (!auth?.currentUser) throw new Error('Sign in to continue.');
  return { Authorization: `Bearer ${await auth.currentUser.getIdToken()}`, 'Content-Type': 'application/json' };
}

export function currentUser(): User | null { return auth?.currentUser ?? null; }
export function subscribeToAuth(callback: (user: User | null) => void): () => void {
  if (!auth) {
    callback(null);
    return () => undefined;
  }
  return auth.onAuthStateChanged(callback);
}
export async function login(): Promise<User> {
  if (!auth) throw new Error('Add Firebase settings in frontend/.env.local to enable sign in.');
  const result = await signInWithPopup(auth, new GoogleAuthProvider());
  return result.user;
}
export async function logout(): Promise<void> { if (auth) await signOut(auth); }
export async function createOrder(items: CartItem[]): Promise<Order> {
  const headers = await authHeaders();
  headers['Idempotency-Key'] = crypto.randomUUID();
  const response = await fetch(`${apiBase}/api/orders`, {
    method: 'POST', headers, body: JSON.stringify({ items }),
  });
  if (!response.ok) throw new Error(response.status === 401 ? 'Please sign in again.' : `Checkout couldn't start (${response.status}).`);
  return await response.json() as Order;
}
export async function readOrder(id: string): Promise<Order> {
  const response = await fetch(`${apiBase}/api/orders/${encodeURIComponent(id)}`, { headers: await authHeaders() });
  if (!response.ok) throw new Error('Could not load order status.');
  return await response.json() as Order;
}
