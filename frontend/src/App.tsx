import { useEffect, useMemo, useState } from 'react';
import { ArrowDown, ArrowLeft, ArrowRight, ArrowUpRight, Check, Heart, Leaf, Menu, Minus, PackageCheck, Plus, Search, ShieldCheck, ShoppingBag, Sparkles, Truck, X } from 'lucide-react';
import { authHeaders, createOrder, currentUser, getProducts, login, logout, readOrder, subscribeToAuth, type CartItem, type Order, type Product } from './api';
import './styles.css';

type Cart = Record<string, number>;
const pictures: Record<string, string> = {
  'CERAMIC-MUG': 'photo-1514228742587-6b1558fcca3d', 'LINEN-TOTE': 'photo-1544816155-12df9643f363',
  'CANDLE-01': 'photo-1603006905003-be475563bc59', 'GLASS-CARAFE': 'photo-1514228742587-6b1558fcca3d',
  'COTTON-THROW': 'photo-1600210492486-724fe5c67fb0', 'BOWL-SET': 'photo-1493106641515-6b5631de4bb9',
  'DESK-LAMP': 'photo-1507473885765-e6ed057f782c', 'SOAP-TRIO': 'photo-1608571423902-eed4a5ad8108',
};
const categories = ['Everything', 'Home', 'Kitchen', 'Accessories', 'Wellbeing'];
const categoryMap: Record<string, string[]> = {
  Home: ['CANDLE-01', 'COTTON-THROW', 'DESK-LAMP'], Kitchen: ['CERAMIC-MUG', 'GLASS-CARAFE', 'BOWL-SET'],
  Accessories: ['LINEN-TOTE'], Wellbeing: ['SOAP-TRIO', 'CANDLE-01'],
};
const money = (minor: number) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(minor / 100);

export default function App() {
  const [products, setProducts] = useState<Product[]>([]);
  const [cart, setCart] = useState<Cart>(() => JSON.parse(localStorage.getItem('common-cart') || '{}') as Cart);
  const [category, setCategory] = useState('Everything');
  const [query, setQuery] = useState('');
  const [drawer, setDrawer] = useState(false);
  const [user, setUser] = useState(currentUser());
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState('');
  const [order, setOrder] = useState<Order | null>(null);
  const [mobileMenu, setMobileMenu] = useState(false);
  const [liked, setLiked] = useState<string[]>([]);

  useEffect(() => {
    const unsubscribe = subscribeToAuth(setUser);
    void getProducts().then(setProducts);
    return unsubscribe;
  }, []);
  useEffect(() => { localStorage.setItem('common-cart', JSON.stringify(cart)); }, [cart]);
  useEffect(() => {
    if (!order || ['CONFIRMED', 'CANCELLED'].includes(order.status)) return;
    const timer = window.setInterval(() => { void readOrder(order.orderId).then(setOrder).catch(() => undefined); }, 2500);
    return () => window.clearInterval(timer);
  }, [order]);
  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(''), 2800);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const filtered = useMemo(() => products.filter(product => {
    const matchesCategory = category === 'Everything' || categoryMap[category]?.includes(product.sku);
    return matchesCategory && product.name.toLowerCase().includes(query.toLowerCase());
  }), [products, category, query]);
  const count = Object.values(cart).reduce((sum, qty) => sum + qty, 0);
  const total = products.reduce((sum, product) => sum + (cart[product.sku] || 0) * product.priceMinor, 0);
  const cartRows = products.filter(product => cart[product.sku]);

  function changeQuantity(sku: string, amount: number) {
    setCart(previous => {
      const next = { ...previous };
      const quantity = (next[sku] || 0) + amount;
      if (quantity < 1) delete next[sku]; else next[sku] = quantity;
      return next;
    });
  }
  async function signIn() {
    try { const result = await login(); setUser(result); setToast(`Welcome, ${result.displayName?.split(' ')[0] || 'friend'}!`); }
    catch (error) { setToast(error instanceof Error ? error.message : 'Could not sign in.'); }
  }
  async function checkout() {
    if (!user) { await signIn(); return; }
    if (!cartRows.length) return;
    setBusy(true);
    try {
      const items: CartItem[] = cartRows.map(({ sku }) => ({ sku, quantity: cart[sku] }));
      const placed = await createOrder(items);
      setOrder(placed);
      setCart({});
      setToast('Your order is on its way through our checkout.');
    } catch (error) { setToast(error instanceof Error ? error.message : 'Checkout failed.'); }
    finally { setBusy(false); }
  }
  async function loadMyOrders() {
    if (!user) { await signIn(); return; }
    try {
      const apiBase = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
      const response = await fetch(`${apiBase}/api/notifications`, { headers: await authHeaders() });
      if (!response.ok) throw new Error('Order history is unavailable.');
      const notifications = await response.json() as Array<{ orderId: string; kind: string }>;
      if (notifications[0]?.orderId) setOrder(await readOrder(notifications[0].orderId));
      else setToast('No completed orders yet — find something lovely.');
    } catch (error) { setToast(error instanceof Error ? error.message : 'Unable to load orders.'); }
  }

  return <>
    <div className="announcement"><Sparkles size={13} /> Thoughtful things for a slower, softer everyday <span className="announcement-sep">·</span> Free shipping over $75 <ArrowRight size={13} /></div>
    <header className="header">
      <button className="icon-button mobile-toggle" aria-label="Open menu" onClick={() => setMobileMenu(!mobileMenu)}><Menu size={21} /></button>
      <a className="wordmark" href="#top" aria-label="Common Goods home">common<span>goods</span><i>.</i></a>
      <nav className={`nav ${mobileMenu ? 'nav-open' : ''}`}>
        <a href="#shop" onClick={() => setMobileMenu(false)}>Shop all</a><a href="#shop" onClick={() => { setCategory('Home'); setMobileMenu(false) }}>Home & living</a><a href="#story" onClick={() => setMobileMenu(false)}>Our story</a>
      </nav>
      <div className="header-actions"><button className="account-button" onClick={user ? loadMyOrders : signIn}>{user ? <><span className="avatar">{user.displayName?.[0] || 'Y'}</span><span className="account-label">Hi, {user.displayName?.split(' ')[0] || 'there'}</span></> : <span className="account-label">Sign in</span>}</button>{user && <button className="account-label signout-button" onClick={async () => { await logout(); setOrder(null); setToast('Signed out successfully.'); }}>Sign out</button>}<button className="bag-button" onClick={() => setDrawer(true)} aria-label={`Open bag, ${count} items`}><ShoppingBag size={19} /><span>Bag</span><b>{count}</b></button></div>
    </header>
    <main id="top">
      <section className="hero">
        <div className="hero-copy"><div className="eyebrow"><span className="eyebrow-line" />THE EVERYDAY EDIT · VOL. 04</div><h1>Make room<br />for <em>good</em> things.</h1><p>Useful, beautiful things for the spaces where life actually happens. Made with care. Kept for a long time.</p><a href="#shop" className="dark-button">Explore the collection <ArrowRight size={16} /></a><div className="hero-note"><div className="tiny-avatars"><i>J</i><i>M</i><i>A</i></div><span>Loved in 1200+ homes</span><span className="stars">★★★★★</span></div></div>
        <div className="hero-photo"><img src="https://images.unsplash.com/photo-1600210492486-724fe5c67fb0?auto=format&fit=crop&w=1500&q=85" alt="Warm, sunlit living room with thoughtfully chosen home essentials" /><div className="photo-caption"><span>01 / 04</span><span>THE SUNDAY HOME</span><ArrowUpRight size={15} /></div><div className="round-stamp"><span>MADE TO BE<br />LIVED IN</span><Leaf size={17} /></div></div>
      </section>
      <section className="promise-strip"><div><Truck /><span><b>Free shipping</b><small>On orders over $75</small></span></div><i /><div><Leaf /><span><b>Considered materials</b><small>Better by design</small></span></div><i /><div><ShieldCheck /><span><b>Easy, happy returns</b><small>30 days to make it yours</small></span></div><i /><div><Heart /><span><b>Small-batch goodness</b><small>Thoughtfully made</small></span></div></section>
      <section id="shop" className="shop-section"><div className="section-heading"><div><div className="eyebrow"><span className="eyebrow-line" />THE GOOD STUFF</div><h2>Little things.<br className="mobile-break" /> <em>Better days.</em></h2></div><p>Everyday essentials with a little extra thought. The keepers, the reach-fors, the “where did you get that?”s.</p></div>
        <div className="shop-toolbar"><div className="categories">{categories.map(item => <button key={item} onClick={() => setCategory(item)} className={category === item ? 'category active' : 'category'}>{item}</button>)}</div><label className="search-box"><Search size={15} /><input aria-label="Search products" placeholder="Find your thing" value={query} onChange={e => setQuery(e.target.value)} />{query && <button aria-label="Clear search" onClick={() => setQuery('')}><X size={14} /></button>}</label></div>
        <div className="product-grid">{filtered.map((product, index) => <article className="product-card" key={product.sku} style={{ animationDelay: `${index * 55}ms` }}><div className="product-image"><img src={`https://images.unsplash.com/${pictures[product.sku] || 'photo-1490312278390-ab64016e0aa9'}?auto=format&fit=crop&w=850&q=80`} alt={product.name} loading="lazy" /><span className={`product-tag ${index % 3 === 0 ? 'tag-visible' : ''}`}>{index % 3 === 0 ? 'A GOOD PICK' : 'JUST LANDED'}</span><button className={`heart-button ${liked.includes(product.sku) ? 'liked' : ''}`} aria-label="Save item" onClick={() => setLiked(previous => previous.includes(product.sku) ? previous.filter(id => id !== product.sku) : [...previous, product.sku])}><Heart size={17} fill={liked.includes(product.sku) ? 'currentColor' : 'none'} /></button><button className="quick-add" onClick={() => { changeQuantity(product.sku, 1); setToast(`${product.name} added to your bag`) }}>Add to bag <Plus size={14} /></button></div><div className="product-info"><div><span className="product-category">{categoryMap.Home?.includes(product.sku) ? 'HOME OBJECTS' : categoryMap.Kitchen?.includes(product.sku) ? 'KITCHEN & TABLE' : 'EVERYDAY GOODS'}</span><h3>{product.name}</h3></div><b>{money(product.priceMinor)}</b></div><div className="product-rating"><span>★★★★★</span> <small>({19 + index * 13})</small></div></article>)}</div>
        {filtered.length === 0 && <div className="empty-search">No little treasures match that search. <button onClick={() => { setQuery(''); setCategory('Everything') }}>See everything</button></div>}
        <div className="collection-footer"><span>GOOD THINGS, NO RUSH.</span><span>Showing {filtered.length} thoughtfully chosen things</span><button onClick={() => { setCategory('Everything'); setQuery('') }}>View all <ArrowDown size={14} /></button></div>
      </section>
      <section className="story" id="story"><div className="story-image"><img src="https://images.unsplash.com/photo-1449247709967-d4461a6a6103?auto=format&fit=crop&w=1100&q=85" alt="A calm, naturally lit interior" /><span>GOOD THINGS START AT HOME</span></div><div className="story-copy"><div className="eyebrow"><span className="eyebrow-line" />A NOTE FROM US</div><h2>Less, but a little<br />more <em>meaningful.</em></h2><p>We believe the things we live with should earn their place. So we look for honest materials, useful shapes and makers who care as much as we do.</p><p>No endless scrolling. No stuff for stuff’s sake. Just a small, considered collection of things that make ordinary days feel a bit more yours.</p><a href="#shop" className="text-link">A little more about us <ArrowUpRight size={15} /></a><div className="handwritten">With care, always <span>♡</span></div></div></section>
      <section className="newsletter"><div className="eyebrow"><span className="eyebrow-line" />A NICE LITTLE NOTE</div><h2>Good things in your <em>inbox.</em></h2><p>New finds, everyday inspiration, and a little something for your first order.</p><form onSubmit={e => { e.preventDefault(); setToast('You’re on the list. Look out for a little note from us!'); (e.currentTarget.elements.namedItem('email') as HTMLInputElement).value = '' }}><input name="email" type="email" placeholder="Your email address" required /><button type="submit">Count me in <ArrowRight size={15} /></button></form><small>No noise, just the nice stuff. Unsubscribe anytime.</small><div className="newsletter-flower">✳</div></section>
    </main>
    <footer className="footer"><a className="wordmark" href="#top">common<span>goods</span><i>.</i></a><span>Made for everyday. Kept for a long time.</span><span>© 2025 Common Goods Studio</span></footer>
    {drawer && <div className="scrim" onMouseDown={e => { if (e.target === e.currentTarget) setDrawer(false) }}><aside className="cart-drawer"><div className="drawer-head"><div><span className="eyebrow">YOUR LITTLE FINDS</span><h2>Your bag <span>({count})</span></h2></div><button className="icon-button" aria-label="Close bag" onClick={() => setDrawer(false)}><X /></button></div>{order && <div className="order-status"><div className="status-icon"><PackageCheck size={20} /></div><div><b>Order {order.status.toLowerCase()}</b><span>{order.status === 'CONFIRMED' ? 'Your good things are on their way.' : order.status === 'CANCELLED' ? 'This order could not be completed.' : 'We’re making sure everything is just right…'}</span></div><button aria-label="Dismiss order status" onClick={() => setOrder(null)}><X size={15} /></button></div>}{cartRows.length ? <><div className="cart-items">{cartRows.map(product => <div className="cart-item" key={product.sku}><img src={`https://images.unsplash.com/${pictures[product.sku]}?auto=format&fit=crop&w=240&q=75`} alt="" /><div className="cart-item-main"><h3>{product.name}</h3><small>Thoughtfully made · Ships in 2–3 days</small><div className="qty-control"><button onClick={() => changeQuantity(product.sku, -1)} aria-label="Decrease quantity"><Minus size={13} /></button><span>{cart[product.sku]}</span><button onClick={() => changeQuantity(product.sku, 1)} aria-label="Increase quantity"><Plus size={13} /></button></div></div><b>{money(product.priceMinor * cart[product.sku])}</b></div>)}</div><div className="shipping-progress"><div className="shipping-label"><span>{total >= 7500 ? 'You’ve unlocked free shipping!' : `You’re ${money(7500 - total)} away from free shipping`}</span><Truck size={16} /></div><div className="progress-track"><i style={{ width: `${Math.min(100, total / 7500 * 100)}%` }} /></div></div><div className="checkout-area"><div className="subtotal"><span>Subtotal</span><b>{money(total)}</b></div><small>Shipping and taxes calculated at checkout.</small><button className="checkout-button" onClick={checkout} disabled={busy}>{busy ? 'One moment…' : user ? 'Head to checkout' : 'Sign in to checkout'} <ArrowRight size={17} /></button><div className="secure-note"><ShieldCheck size={14} /> A safe, secure checkout</div></div></> : <div className="empty-bag"><div className="empty-bag-icon"><ShoppingBag /></div><h3>A little room for something good.</h3><p>Your bag’s waiting for its first good thing.</p><button className="dark-button" onClick={() => setDrawer(false)}>Find your something <ArrowRight size={15} /></button></div>}<button className="continue-shopping" onClick={() => setDrawer(false)}><ArrowLeft size={14} /> Keep looking around</button></aside></div>}
    {toast && <div role="status" className="toast"><Check size={16} />{toast}<button onClick={() => setToast('')} aria-label="Dismiss"><X size={15} /></button></div>}
    <button className="mobile-bag" onClick={() => setDrawer(true)}><ShoppingBag size={18} /> Bag <b>{count}</b> <span>{money(total)}</span></button>
  </>;
}
