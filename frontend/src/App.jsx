import { useState, useEffect, useRef, createContext, useContext, useCallback } from "react";

const BASE = "https://alms-pro.onrender.com/api";
const req = async (method, path, body, token) => {
  const r = await fetch(`${BASE}${path}`, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!r.ok) { const e = await r.json().catch(() => ({})); throw new Error(e.message || "Request failed"); }
  return r.json().catch(() => ({}));
};
const api = {
  post:   (p, b, t) => req("POST",   p, b, t),
  get:    (p, t)    => req("GET",    p, null, t),
  delete: (p, t)    => req("DELETE", p, null, t),
  patch:  (p, b, t) => req("PATCH",  p, b, t),
};

const Ctx = createContext(null);
function AuthProvider({ children }) {
  const [user, setUser] = useState(() => { try { return JSON.parse(sessionStorage.getItem("alms_u")); } catch { return null; } });
  const login  = u => { setUser(u); sessionStorage.setItem("alms_u", JSON.stringify(u)); };
  const logout = () => { setUser(null); sessionStorage.removeItem("alms_u"); };
  return <Ctx.Provider value={{ user, login, logout }}>{children}</Ctx.Provider>;
}
const useAuth = () => useContext(Ctx);

const CSS = `
@import url('https://fonts.googleapis.com/css2?family=Bebas+Neue&family=DM+Mono:wght@300;400;500&family=Barlow:wght@300;400;500;600;700&display=swap');
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
:root{
  --bg:#060810;--bg2:#0a0d18;--bg3:#0f1420;--bg4:#151c2e;
  --border:#1e2a44;--border2:#2a3a5c;
  --blue:#4a9eff;--blue2:#1a6fd4;--blue3:#0d3a7a;
  --blue-glow:rgba(74,158,255,0.15);--blue-glow2:rgba(74,158,255,0.07);
  --red:#e84040;--green:#4caf82;--amber:#f5a623;
  --text:#dde8ff;--text2:#7a90c0;--text3:#344060;
  --font-d:'Bebas Neue',sans-serif;--font-m:'DM Mono',monospace;--font-b:'Barlow',sans-serif;
  --r:4px;
}
html{scroll-behavior:smooth}
body{background:var(--bg);color:var(--text);font-family:var(--font-b);min-height:100vh;overflow-x:hidden}
body::before{content:'';position:fixed;inset:0;background-image:url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.03'/%3E%3C/svg%3E");pointer-events:none;z-index:9999;opacity:0.4}
::-webkit-scrollbar{width:4px}::-webkit-scrollbar-track{background:var(--bg)}::-webkit-scrollbar-thumb{background:var(--border2);border-radius:2px}

.layout{display:flex;min-height:100vh}
.sidebar{width:240px;flex-shrink:0;background:var(--bg2);border-right:1px solid var(--border);display:flex;flex-direction:column;position:sticky;top:0;height:100vh;overflow-y:auto}
.main-content{flex:1;min-width:0;display:flex;flex-direction:column}
.page{flex:1;padding:32px;max-width:1300px;margin:0 auto;width:100%}
@media(max-width:768px){.sidebar{width:60px}.sidebar .nav-label,.sidebar .logo-sub,.sidebar .user-info{display:none}.page{padding:16px}}

.logo{padding:24px 20px 20px;border-bottom:1px solid var(--border);display:flex;align-items:center;gap:12px}
.logo-icon{width:36px;height:36px;background:var(--blue);display:flex;align-items:center;justify-content:center;font-family:var(--font-d);font-size:20px;color:var(--bg);clip-path:polygon(50% 0%,100% 25%,100% 75%,50% 100%,0% 75%,0% 25%);flex-shrink:0}
.logo-text{font-family:var(--font-d);font-size:22px;letter-spacing:3px;color:var(--blue);line-height:1}
.logo-sub{font-family:var(--font-m);font-size:9px;color:var(--text3);letter-spacing:2px;text-transform:uppercase;margin-top:2px}

.nav-section{padding:16px 12px 8px}
.nav-section-title{font-family:var(--font-m);font-size:9px;color:var(--text3);letter-spacing:2px;text-transform:uppercase;padding:0 8px;margin-bottom:6px}
.nav-item{display:flex;align-items:center;gap:10px;padding:10px 8px;border-radius:var(--r);cursor:pointer;transition:all 0.15s;color:var(--text2);font-size:13px;font-weight:500;border:1px solid transparent;width:100%;background:none;text-align:left}
.nav-item:hover{background:var(--bg3);color:var(--text)}
.nav-item.active{background:var(--blue-glow);border-color:var(--blue3);color:var(--blue)}
.nav-icon{font-size:15px;flex-shrink:0;width:20px;text-align:center}

.sidebar-user{margin-top:auto;padding:16px;border-top:1px solid var(--border)}
.user-info{display:flex;align-items:center;gap:10px;margin-bottom:10px}
.user-avatar{width:34px;height:34px;border-radius:50%;background:var(--blue3);display:flex;align-items:center;justify-content:center;font-family:var(--font-d);font-size:16px;color:var(--blue);flex-shrink:0}
.user-name{font-size:13px;font-weight:600;color:var(--text)}
.user-role{font-family:var(--font-m);font-size:10px;color:var(--blue);letter-spacing:1px}
.btn-logout{width:100%;padding:8px;background:transparent;border:1px solid var(--border2);border-radius:var(--r);color:var(--text2);font-family:var(--font-m);font-size:11px;letter-spacing:1px;cursor:pointer;transition:all 0.15s}
.btn-logout:hover{border-color:var(--red);color:var(--red)}

.topbar{background:var(--bg2);border-bottom:1px solid var(--border);padding:0 32px;height:56px;display:flex;align-items:center;justify-content:space-between;flex-shrink:0}
.topbar-title{font-family:var(--font-d);font-size:26px;letter-spacing:3px;color:var(--text)}
.topbar-title span{color:var(--blue)}
.topbar-meta{display:flex;align-items:center;gap:12px}

.pill{padding:3px 10px;border-radius:2px;font-family:var(--font-m);font-size:10px;letter-spacing:1px}
.pill-blue{background:var(--blue-glow);border:1px solid var(--blue3);color:var(--blue)}
.pill-green{background:rgba(76,175,130,0.1);border:1px solid rgba(76,175,130,0.3);color:var(--green)}
.pill-red{background:rgba(232,64,64,0.1);border:1px solid rgba(232,64,64,0.3);color:var(--red)}
.pill-amber{background:rgba(245,166,35,0.1);border:1px solid rgba(245,166,35,0.3);color:var(--amber)}

.btn{display:inline-flex;align-items:center;gap:8px;padding:10px 20px;border-radius:var(--r);font-family:var(--font-d);font-size:16px;letter-spacing:1px;cursor:pointer;transition:all 0.15s;border:none;text-transform:uppercase}
.btn:disabled{opacity:0.4;cursor:not-allowed}
.btn-primary{background:var(--blue);color:var(--bg)}
.btn-primary:not(:disabled):hover{background:#6ab4ff;box-shadow:0 0 24px rgba(74,158,255,0.4)}
.btn-outline{background:transparent;color:var(--blue);border:1px solid var(--blue3)}
.btn-outline:not(:disabled):hover{background:var(--blue-glow)}
.btn-ghost{background:transparent;color:var(--text2);border:1px solid var(--border2)}
.btn-ghost:not(:disabled):hover{border-color:var(--border2);color:var(--text);background:var(--bg3)}
.btn-danger{background:transparent;color:var(--red);border:1px solid rgba(232,64,64,0.3)}
.btn-danger:hover{background:rgba(232,64,64,0.08)}
.btn-sm{padding:6px 14px;font-size:13px}
.btn-block{width:100%;justify-content:center}

.form-group{margin-bottom:16px}
.label{display:block;margin-bottom:6px;font-family:var(--font-m);font-size:10px;color:var(--text2);letter-spacing:2px;text-transform:uppercase}
.input{width:100%;background:var(--bg3);border:1px solid var(--border);border-radius:var(--r);color:var(--text);padding:10px 14px;font-size:14px;font-family:var(--font-b);outline:none;transition:border-color 0.15s}
.input:focus{border-color:var(--blue);box-shadow:0 0 0 3px rgba(74,158,255,0.08)}
.input::placeholder{color:var(--text3)}
select.input option{background:var(--bg3)}

.card{background:var(--bg2);border:1px solid var(--border);border-radius:6px;padding:20px}
.card-title{font-family:var(--font-d);font-size:20px;letter-spacing:2px;color:var(--text);margin-bottom:16px;display:flex;align-items:center;gap:10px}
.card-title::before{content:'';display:block;width:3px;height:20px;background:linear-gradient(180deg,var(--blue),transparent);border-radius:2px}

.stats-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin-bottom:24px}
.stat-card{background:var(--bg3);border:1px solid var(--border);border-radius:6px;padding:16px 18px;position:relative;overflow:hidden}
.stat-card::after{content:'';position:absolute;bottom:0;left:0;right:0;height:2px;background:linear-gradient(90deg,var(--blue),transparent)}
.stat-label{font-family:var(--font-m);font-size:10px;color:var(--text3);letter-spacing:2px;text-transform:uppercase}
.stat-value{font-family:var(--font-d);font-size:38px;color:var(--blue);letter-spacing:2px;line-height:1.1;margin-top:4px}
.stat-sub{font-family:var(--font-m);font-size:11px;color:var(--text2);margin-top:2px}

/* ── LIFT SHAFT — LIVE ── */
.shafts-section{background:var(--bg2);border:1px solid var(--border);border-radius:6px;padding:20px;margin-bottom:20px}
.shafts-title{font-family:var(--font-d);font-size:20px;letter-spacing:2px;color:var(--text);margin-bottom:16px;display:flex;align-items:center;justify-content:space-between}
.shafts-wrap{display:grid;grid-template-columns:repeat(6,1fr);gap:6px}
@media(max-width:900px){.shafts-wrap{grid-template-columns:repeat(3,1fr)}}
@media(max-width:500px){.shafts-wrap{grid-template-columns:repeat(2,1fr)}}

.shaft-col{display:flex;flex-direction:column;align-items:center;gap:6px}
.shaft-outer{position:relative;width:70%;height:170px;background:var(--bg3);border:1px solid var(--border);border-radius:6px;overflow:hidden}

/* Color by status */
.shaft-outer.IDLE      {border-color:var(--border)}
.shaft-outer.WAITING   {border-color:var(--amber);box-shadow:0 0 12px rgba(245,166,35,0.1)}
.shaft-outer.TRAVELING {border-color:var(--blue);box-shadow:0 0 12px rgba(74,158,255,0.15)}
.shaft-outer.HALTING   {border-color:var(--green);box-shadow:0 0 12px rgba(76,175,130,0.15)}
.shaft-outer.MAINTENANCE{border-color:var(--red);box-shadow:0 0 12px rgba(232,64,64,0.1)}

.shaft-track{position:absolute;left:50%;top:8px;bottom:8px;width:2px;background:var(--border);transform:translateX(-50%)}

.shaft-car{position:absolute;left:4px;right:4px;height:20px;border-radius:4px;transition:bottom 0.5s cubic-bezier(0.4,0,0.2,1);display:flex;align-items:center;justify-content:center;font-family:var(--font-m);font-size:11px;font-weight:500;z-index:2}
.shaft-car.IDLE        {background:var(--bg4);border:1px solid var(--border2);color:var(--text3)}
.shaft-car.BUSY        {background:var(--blue3);border:1px solid var(--blue);color:var(--blue)}
.shaft-car.WAITING_CAR {background:rgba(245,166,35,0.15);border:1px solid var(--amber);color:var(--amber);animation:pulse-wait 1s ease-in-out infinite}
.shaft-car.TRAVELING_CAR{background:var(--blue3);border:1px solid var(--blue);color:#fff;box-shadow:0 0 8px rgba(74,158,255,0.5)}
.shaft-car.HALTING_CAR {background:rgba(76,175,130,0.2);border:1px solid var(--green);color:var(--green)}
.shaft-car.MAINTENANCE {background:rgba(232,64,64,0.15);border:1px solid var(--red);color:var(--red)}
@keyframes pulse-wait{0%,100%{opacity:1}50%{opacity:0.6}}

.shaft-floor-label{position:absolute;top:4px;right:5px;font-family:var(--font-m);font-size:8px;color:var(--text3)}
.shaft-name{font-family:var(--font-d);font-size:18px;color:var(--blue);letter-spacing:1px}
.shaft-status-badge{font-family:var(--font-m);font-size:9px;letter-spacing:1px;padding:2px 7px;border-radius:2px;text-transform:uppercase}
.badge-IDLE        {background:rgba(74,74,74,0.2);color:var(--text3)}
.badge-WAITING     {background:rgba(245,166,35,0.1);color:var(--amber)}
.badge-TRAVELING   {background:var(--blue-glow);color:var(--blue)}
.badge-HALTING     {background:rgba(76,175,130,0.1);color:var(--green)}
.badge-MAINTENANCE {background:rgba(232,64,64,0.1);color:var(--red)}
.shaft-wait-bar{width:100%;height:3px;background:var(--border);border-radius:2px;overflow:hidden}
.shaft-wait-fill{height:100%;background:var(--amber);border-radius:2px;transition:width 1s linear}
.shaft-load{font-family:var(--font-m);font-size:9px;color:var(--text3)}

/* ── LIFT GRID ── */
.lift-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:10px}
@media(max-width:1000px){.lift-grid{grid-template-columns:repeat(3,1fr)}}
@media(max-width:600px){.lift-grid{grid-template-columns:repeat(2,1fr)}}
.lift-tile{background:var(--bg3);border:1px solid var(--border);border-radius:6px;padding:14px 10px;text-align:center;transition:all 0.2s;position:relative}
.lift-tile.IDLE        {border-color:var(--border)}
.lift-tile.BUSY        {border-color:var(--blue3);background:var(--blue-glow2)}
.lift-tile.MAINTENANCE {border-color:rgba(232,64,64,0.4)}
.lift-tile.OFFLINE     {border-color:var(--text3);opacity:0.5}
.lift-num{font-family:var(--font-d);font-size:32px;color:var(--text);line-height:1}
.lift-name{font-family:var(--font-m);font-size:10px;color:var(--text3);letter-spacing:0;margin-bottom:6px}
.lift-floor{font-family:var(--font-m);font-size:11px;color:var(--text2);margin:4px 0}
.lift-status-pill{display:inline-block;padding:2px 8px;border-radius:2px;font-family:var(--font-m);font-size:9px;letter-spacing:1px;margin-top:4px}
.s-IDLE        {background:rgba(76,175,130,0.1);color:var(--green)}
.s-BUSY        {background:var(--blue-glow);color:var(--blue)}
.s-MAINTENANCE {background:rgba(232,64,64,0.1);color:var(--red)}
.s-OFFLINE     {background:rgba(74,74,74,0.2);color:var(--text3)}
.lift-block{font-family:var(--font-m);font-size:9px;color:var(--text3);margin-top:3px}
.load-bar{height:2px;background:var(--border);border-radius:1px;margin-top:6px}
.load-fill{height:100%;background:var(--blue);border-radius:1px;transition:width 0.3s}

/* ── ROUTE TILES ── */
.route-grid{display:flex;flex-wrap:wrap;gap:10px}
.route-tile{background:var(--bg3);border:1px solid var(--border);border-radius:6px;padding:14px 18px;cursor:pointer;transition:all 0.15s;min-width:130px;position:relative;text-align:left}
.route-tile:hover{border-color:var(--border2);transform:translateY(-1px)}
.route-tile.selected{border-color:var(--blue);background:var(--blue-glow2);box-shadow:0 0 16px var(--blue-glow)}
.route-name{font-family:var(--font-d);font-size:17px;letter-spacing:1px;color:var(--text)}
.route-floors{font-family:var(--font-m);font-size:10px;color:var(--text2);margin-top:4px}
.route-del{position:absolute;top:6px;right:8px;background:none;border:none;color:var(--text3);cursor:pointer;font-size:12px;transition:color 0.15s;padding:2px}
.route-del:hover{color:var(--red)}
.route-add{background:transparent;border:1px dashed var(--border2);border-radius:6px;padding:14px 18px;cursor:pointer;color:var(--text2);font-family:var(--font-d);font-size:14px;letter-spacing:1px;min-width:110px;transition:all 0.15s;text-transform:uppercase}
.route-add:hover{border-color:var(--blue3);color:var(--blue)}

/* ── RESULT ── */
.result-box{background:var(--bg3);border-radius:8px;padding:28px;text-align:center;margin-top:20px;animation:popIn 0.3s cubic-bezier(0.175,0.885,0.32,1.275)}
.result-box.ASSIGNED{border:1px solid var(--blue3);box-shadow:0 0 40px rgba(74,158,255,0.1)}
.result-box.QUEUED{border:1px solid rgba(74,158,255,0.3)}
.result-big{font-family:var(--font-d);font-size:88px;line-height:1;letter-spacing:4px}
.result-big.blue{color:var(--blue);text-shadow:0 0 50px rgba(74,158,255,0.4)}
.result-label{font-family:var(--font-m);font-size:11px;color:var(--text3);letter-spacing:3px;text-transform:uppercase;margin-top:6px}
.result-block{font-family:var(--font-m);font-size:12px;color:var(--text2);margin-top:8px}
.result-msg{color:var(--text2);font-size:13px;margin-top:12px;font-family:var(--font-b)}
@keyframes popIn{from{transform:scale(0.92) translateY(8px);opacity:0}to{transform:scale(1) translateY(0);opacity:1}}

/* ── TABLE ── */
.tbl-wrap{overflow-x:auto}
table{width:100%;border-collapse:collapse;font-size:13px}
thead tr{border-bottom:1px solid var(--border)}
th{padding:10px 14px;font-family:var(--font-m);font-size:10px;letter-spacing:1px;color:var(--text3);text-align:left;white-space:nowrap}
td{padding:10px 14px;border-bottom:1px solid var(--border);color:var(--text);vertical-align:middle}
tr:hover td{background:var(--bg3)}
.mono{font-family:var(--font-m);font-size:12px;color:var(--text2)}
.tag{display:inline-flex;align-items:center;padding:2px 8px;border-radius:2px;font-family:var(--font-m);font-size:10px;letter-spacing:0.5px}
.t-ENTRY{background:rgba(76,175,130,0.1);color:var(--green)}
.t-EXIT{background:rgba(232,64,64,0.1);color:var(--red)}
.t-INTER_FLOOR{background:rgba(74,158,255,0.1);color:var(--blue)}
.t-SCHEDULED{background:rgba(245,166,35,0.1);color:var(--amber)}
.t-ASSIGNED{background:rgba(76,175,130,0.1);color:var(--green)}
.t-IN_PROGRESS{background:var(--blue-glow);color:var(--blue)}
.t-COMPLETED{background:rgba(74,74,74,0.2);color:var(--text2)}
.t-QUEUED{background:rgba(74,158,255,0.1);color:var(--blue)}
.t-CANCELLED{background:rgba(232,64,64,0.1);color:var(--red)}

/* ── MODAL ── */
.overlay{position:fixed;inset:0;background:rgba(0,0,0,0.75);display:flex;align-items:center;justify-content:center;z-index:500;backdrop-filter:blur(6px);padding:20px}
.modal{background:var(--bg2);border:1px solid var(--border2);border-radius:8px;padding:28px;width:100%;max-width:420px;animation:popIn 0.2s ease}
.modal-title{font-family:var(--font-d);font-size:24px;letter-spacing:2px;color:var(--blue);margin-bottom:20px}

/* ── AUTH ── */
.auth-bg{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px;background:radial-gradient(ellipse at 20% 50%,rgba(74,158,255,0.04) 0%,transparent 50%),radial-gradient(ellipse at 80% 20%,rgba(74,158,255,0.03) 0%,transparent 40%),var(--bg)}
.auth-box{width:100%;max-width:400px;background:var(--bg2);border:1px solid var(--border);border-radius:8px;padding:40px 36px;box-shadow:0 24px 80px rgba(0,0,0,0.6),0 0 60px rgba(74,158,255,0.04)}
.auth-logo{text-align:center;margin-bottom:36px}
.auth-logo-mark{width:56px;height:56px;margin:0 auto 14px;background:var(--blue3);clip-path:polygon(50% 0%,100% 25%,100% 75%,50% 100%,0% 75%,0% 25%);display:flex;align-items:center;justify-content:center;font-family:var(--font-d);font-size:26px;color:var(--blue)}
.auth-title{font-family:var(--font-d);font-size:36px;letter-spacing:4px;color:var(--blue)}
.auth-sub{font-family:var(--font-m);font-size:10px;color:var(--text3);letter-spacing:2px;margin-top:4px}
.auth-tabs{display:flex;border:1px solid var(--border);border-radius:var(--r);overflow:hidden;margin-bottom:28px}
.auth-tab{flex:1;padding:10px;background:transparent;border:none;color:var(--text2);font-family:var(--font-d);font-size:15px;letter-spacing:1px;cursor:pointer;transition:all 0.15s}
.auth-tab.active{background:var(--blue);color:var(--bg)}

.g2{display:grid;grid-template-columns:1fr 1fr;gap:12px}
.g3{display:grid;grid-template-columns:1fr 1fr 1fr;gap:12px}
@media(max-width:600px){.g2,.g3{grid-template-columns:1fr}}
.row{display:flex;align-items:center}
.row-between{display:flex;align-items:center;justify-content:space-between}
.gap-8{gap:8px}.gap-12{gap:12px}
.mt-12{margin-top:12px}.mt-16{margin-top:16px}.mt-20{margin-top:20px}
.mb-16{margin-bottom:16px}.mb-20{margin-bottom:20px}

.alert{padding:10px 14px;border-radius:var(--r);font-size:13px;margin-bottom:14px}
.alert-err{background:rgba(232,64,64,0.08);border:1px solid rgba(232,64,64,0.3);color:var(--red)}
.alert-ok{background:rgba(76,175,130,0.08);border:1px solid rgba(76,175,130,0.3);color:var(--green)}
.spin{display:inline-block;width:16px;height:16px;border:2px solid var(--border);border-top-color:var(--blue);border-radius:50%;animation:rotate 0.6s linear infinite}
@keyframes rotate{to{transform:rotate(360deg)}}
.divider{height:1px;background:var(--border);margin:20px 0}
.section-hdr{display:flex;align-items:center;justify-content:space-between;margin-bottom:20px}
.section-title{font-family:var(--font-d);font-size:24px;letter-spacing:2px;color:var(--text)}
.section-title span{color:var(--blue)}
.pulse{animation:pulse 2s ease-in-out infinite}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:0.5}}
.empty{text-align:center;padding:48px 20px;color:var(--text3);font-family:var(--font-m);font-size:13px;letter-spacing:1px}
.empty-icon{font-size:32px;margin-bottom:10px}
.search-row{display:flex;gap:10px;margin-bottom:16px}
.search-row .input{flex:1}
.floor-bar{display:flex;align-items:center;gap:10px;margin-bottom:6px}
.floor-bar-label{font-family:var(--font-m);font-size:11px;color:var(--text2);width:50px;text-align:right}
.floor-bar-track{flex:1;height:8px;background:var(--bg3);border-radius:4px;overflow:hidden}
.floor-bar-fill{height:100%;background:linear-gradient(90deg,var(--blue2),var(--blue));border-radius:4px;transition:width 0.4s}
.floor-bar-count{font-family:var(--font-m);font-size:11px;color:var(--text3);width:36px}
`;

// ═══════════════════════════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════════════════════════
const Spin = () => <span className="spin" />;
const Alert = ({ type, msg }) => msg ? <div className={`alert alert-${type}`}>{msg}</div> : null;

// ═══════════════════════════════════════════════════════════════
//  LIVE LIFT SHAFT
// ═══════════════════════════════════════════════════════════════
function LiftShaft({ lift, maxFloor = 60 }) {
  const phase = lift.phase || (lift.liftStatus === "IDLE" ? "IDLE" : "BUSY");
  const pct   = ((lift.currentFloor - 1) / Math.max(1, maxFloor - 1)) * (140 - 36 - 16);
  const bottomPx = 8 + pct;

  const carClass = phase === "WAITING"   ? "WAITING_CAR"
                 : phase === "TRAVELING" ? "TRAVELING_CAR"
                 : phase === "HALTING"   ? "HALTING_CAR"
                 : lift.liftStatus === "MAINTENANCE" ? "MAINTENANCE"
                 : lift.liftStatus === "IDLE"        ? "IDLE"
                 : "BUSY";

  const waitPct = phase === "WAITING" && lift.waitRemaining > 0
    ? (lift.waitRemaining / 15) * 100 : 0;

  return (
    <div className="shaft-col">
      <div className={`shaft-outer ${phase === "IDLE" && lift.liftStatus === "MAINTENANCE" ? "MAINTENANCE" : phase}`}>
        <div className="shaft-track" />
        <div className="shaft-floor-label">{maxFloor}F</div>
        <div
          className={`shaft-car ${carClass}`}
          style={{ bottom: `${bottomPx}px` }}
        >{lift.currentFloor}F</div>
      </div>

      <div className="shaft-name">{`L-${lift.liftNumber}`}</div>
      <div className={`shaft-status-badge badge-${phase}`}>{phase}</div>

      {phase === "WAITING" && lift.waitRemaining > 0 && (
        <>
          <div className="shaft-wait-bar">
            <div className="shaft-wait-fill" style={{ width: `${waitPct}%` }} />
          </div>
          <div className="shaft-load" style={{ color: "var(--amber)" }}>{lift.waitRemaining}s</div>
        </>
      )}
      {phase === "TRAVELING" && (
        <div className="shaft-load" style={{ color: "var(--blue)" }}>↑↓ Moving</div>
      )}
      {phase === "HALTING" && (
        <div className="shaft-load" style={{ color: "var(--green)" }}>⏸ Boarding</div>
      )}
      {phase === "IDLE" && (
        <div className="shaft-load">{lift.totalTrips} trips</div>
      )}
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
//  AUTH PAGE
// ═══════════════════════════════════════════════════════════════
function AuthPage() {
  const { login } = useAuth();
  const [tab, setTab] = useState("login");
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");
  const [f, setF] = useState({ email: "", password: "", fullName: "", employeeId: "", homeFloor: "" });
  const set = k => e => setF(p => ({ ...p, [k]: e.target.value }));

  const submit = async () => {
    setErr(""); setLoading(true);
    try {
      const data = tab === "login"
        ? await api.post("/auth/login",    { email: f.email, password: f.password })
        : await api.post("/auth/register", { email: f.email, password: f.password, fullName: f.fullName, employeeId: f.employeeId, homeFloor: parseInt(f.homeFloor) || 1 });
      login(data);
    } catch (e) { setErr(e.message); }
    finally { setLoading(false); }
  };

  return (
    <div className="auth-bg">
      <div className="auth-box">
        <div className="auth-logo">
          <div className="auth-logo-mark">⬆</div>
          <div className="auth-title">ALMS PRO</div>
          <div className="auth-sub">Advanced Lift Management System</div>
        </div>
        <div className="auth-tabs">
          <button className={`auth-tab ${tab === "login" ? "active" : ""}`} onClick={() => setTab("login")}>Login</button>
          <button className={`auth-tab ${tab === "register" ? "active" : ""}`} onClick={() => setTab("register")}>Register</button>
        </div>
        <Alert type="err" msg={err} />
        {tab === "register" && (
          <div className="g2">
            <div className="form-group">
              <label className="label">Full Name</label>
              <input className="input" placeholder="John Smith" value={f.fullName} onChange={set("fullName")} />
            </div>
            <div className="form-group">
              <label className="label">Employee ID</label>
              <input className="input" placeholder="EMP001" value={f.employeeId} onChange={set("employeeId")} />
            </div>
          </div>
        )}
        <div className="form-group">
          <label className="label">Email</label>
          <input className="input" type="email" placeholder="you@company.com" value={f.email} onChange={set("email")} />
        </div>
        {tab === "register" && (
          <div className="form-group">
            <label className="label">Your Floor (1–60)</label>
            <input className="input" type="number" min="1" max="60" placeholder="5" value={f.homeFloor} onChange={set("homeFloor")} />
          </div>
        )}
        <div className="form-group">
          <label className="label">Password</label>
          <input className="input" type="password" placeholder="••••••••" value={f.password} onChange={set("password")} onKeyDown={e => e.key === "Enter" && submit()} />
        </div>
        <button className="btn btn-primary btn-block" style={{ fontSize: "20px", padding: "12px" }} onClick={submit} disabled={loading}>
          {loading ? <Spin /> : tab === "login" ? "ENTER SYSTEM" : "CREATE ACCOUNT"}
        </button>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
//  CREATE ROUTE MODAL
// ═══════════════════════════════════════════════════════════════
function CreateRouteModal({ token, onClose, onCreated }) {
  const [f, setF] = useState({ routeName: "", fromFloor: "", toFloor: "" });
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");
  const set = k => e => setF(p => ({ ...p, [k]: e.target.value }));

  const submit = async () => {
    if (!f.routeName || !f.fromFloor || !f.toFloor) { setErr("All fields required"); return; }
    setLoading(true);
    try {
      const r = await api.post("/lift/routes", { routeName: f.routeName, fromFloor: parseInt(f.fromFloor), toFloor: parseInt(f.toFloor) }, token);
      onCreated(r); onClose();
    } catch (e) { setErr(e.message); }
    finally { setLoading(false); }
  };

  return (
    <div className="overlay" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <div className="modal-title">NEW ROUTE</div>
        <Alert type="err" msg={err} />
        <div className="form-group">
          <label className="label">Route Name</label>
          <input className="input" placeholder="e.g. Office → Cafeteria" value={f.routeName} onChange={set("routeName")} />
        </div>
        <div className="g2">
          <div className="form-group">
            <label className="label">From Floor</label>
            <input className="input" type="number" min="1" max="60" placeholder="1" value={f.fromFloor} onChange={set("fromFloor")} />
          </div>
          <div className="form-group">
            <label className="label">To Floor</label>
            <input className="input" type="number" min="1" max="60" placeholder="45" value={f.toFloor} onChange={set("toFloor")} />
          </div>
        </div>
        <div className="row gap-12 mt-16">
          <button className="btn btn-primary" style={{ flex: 1 }} onClick={submit} disabled={loading}>{loading ? <Spin /> : "CREATE"}</button>
          <button className="btn btn-ghost" onClick={onClose}>CANCEL</button>
        </div>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
//  USER DASHBOARD
// ═══════════════════════════════════════════════════════════════
function UserDashboard({ page }) {
  const { user } = useAuth();
  const [lifts,   setLifts]   = useState([]);
  const [routes,  setRoutes]  = useState([]);
  const [trips,   setTrips]   = useState([]);
  const [selected, setSelected] = useState(null);
  const [customFrom, setCustomFrom] = useState("");
  const [customTo,   setCustomTo]   = useState("");
  const [loading, setLoading] = useState(false);
  const [result,  setResult]  = useState(null);
  const [err, setErr] = useState("");
  const [showModal, setShowModal] = useState(false);
  const [tripsLoading, setTripsLoading] = useState(false);

  const builtinRoutes = [
    { id: "__entry", routeName: "ENTRY", fromFloor: 1,              toFloor: user.homeFloor },
    { id: "__exit",  routeName: "EXIT",  fromFloor: user.homeFloor, toFloor: 1 },
  ];
  const allRoutes = [...builtinRoutes, ...routes];

  const fetchLifts  = useCallback(async () => { try { setLifts(await api.get("/lift/status", user.token)); } catch {} }, [user.token]);
  const fetchRoutes = useCallback(async () => { try { setRoutes(await api.get("/lift/routes", user.token)); } catch {} }, [user.token]);
  const fetchTrips  = useCallback(async () => {
    setTripsLoading(true);
    try { setTrips(await api.get("/lift/trips", user.token)); } catch {}
    finally { setTripsLoading(false); }
  }, [user.token]);

  useEffect(() => { fetchLifts(); fetchRoutes(); const t = setInterval(fetchLifts, 1000); return () => clearInterval(t); }, [fetchLifts, fetchRoutes]);
  useEffect(() => { if (page === "trips") fetchTrips(); }, [page, fetchTrips]);

  const handleRequest = async () => {
    setErr(""); setResult(null);
    let fromFloor, toFloor, routeName, tripType;
    if (selected) {
      const r = allRoutes.find(r => r.id == selected || r.id === selected);
      if (!r) return;
      fromFloor = r.fromFloor; toFloor = r.toFloor; routeName = r.routeName;
      tripType  = r.routeName === "ENTRY" ? "ENTRY" : r.routeName === "EXIT" ? "EXIT" : "INTER_FLOOR";
    } else {
      if (!customFrom || !customTo) { setErr("Select a route or enter floors"); return; }
      fromFloor = parseInt(customFrom); toFloor = parseInt(customTo);
      tripType  = "INTER_FLOOR"; routeName = `${fromFloor}F → ${toFloor}F`;
    }
    setLoading(true);
    try {
      const r = await api.post("/lift/request", { fromFloor, toFloor, tripType, routeName }, user.token);
      setResult(r); fetchLifts();
    } catch (e) { setErr(e.message); }
    finally { setLoading(false); }
  };

  if (page === "trips") return (
    <div className="page">
      <div className="section-hdr"><div className="section-title">MY <span>TRIPS</span></div></div>
      <div className="card">
        {tripsLoading ? <div className="empty"><Spin /></div> : trips.length === 0 ?
          <div className="empty"><div className="empty-icon">📋</div>No trips yet</div> :
          <TripTable trips={trips} />}
      </div>
    </div>
  );

  return (
    <div className="page">
      {/* Request Panel */}
      <div className="g2">
        <div className="card">
          <div className="card-title">REQUEST LIFT</div>

          <label className="label" style={{ marginBottom: "8px" }}>Select Route</label>
          <div className="route-grid" style={{ marginBottom: "14px" }}>
            {allRoutes.map(r => (
              <div key={r.id}
                className={`route-tile ${selected == r.id ? "selected" : ""}`}
                onClick={() => { setSelected(r.id); setCustomFrom(""); setCustomTo(""); }}>
                {r.id !== "__entry" && r.id !== "__exit" && (
                  <button className="route-del" onClick={e => { e.stopPropagation(); api.delete(`/lift/routes/${r.id}`, user.token).then(() => setRoutes(p => p.filter(x => x.id !== r.id))); }}>✕</button>
                )}
                <div className="route-name">{r.routeName}</div>
                <div className="route-floors">{r.fromFloor}F → {r.toFloor}F</div>
              </div>
            ))}
            <button className="route-add" onClick={() => setShowModal(true)}>+ NEW</button>
          </div>

          <div className="divider" />
          <label className="label" style={{ marginBottom: "8px" }}>Or Enter Manually</label>
          <div className="g2">
            <div className="form-group">
              <label className="label">From Floor</label>
              <input className="input" type="number" min="1" max="60" placeholder="1" value={customFrom}
                onChange={e => { setCustomFrom(e.target.value); setSelected(null); }} />
            </div>
            <div className="form-group">
              <label className="label">To Floor</label>
              <input className="input" type="number" min="1" max="60" placeholder="45" value={customTo}
                onChange={e => { setCustomTo(e.target.value); setSelected(null); }} />
            </div>
          </div>

          <Alert type="err" msg={err} />
          <button className="btn btn-primary btn-block" style={{ fontSize: "18px", padding: "14px" }}
            onClick={handleRequest} disabled={loading || (!selected && (!customFrom || !customTo))}>
            {loading ? <Spin /> : "⬆  REQUEST LIFT"}
          </button>

          {result && (
            <div className={`result-box ${result.status}`}>
              {result.status === "ASSIGNED" ? (
                <>
                  <div className="result-label">ASSIGNED LIFT</div>
                  <div className="result-big blue">{result.liftNumber}</div>
                  <div style={{ fontFamily: "var(--font-d)", fontSize: "18px", color: "var(--text2)", letterSpacing: "1px" }}>L-{result.liftNumber}</div>
                  <div className="result-block">Serving Block {result.blockStart}F – {result.blockEnd}F</div>
                  <div className="result-msg">{result.message}</div>
                  <div style={{ marginTop: "10px", fontFamily: "var(--font-m)", fontSize: "11px", color: "var(--amber)" }}>
                    ⏳ Lift collecting requests for 15s then departing automatically
                  </div>
                </>
              ) : (
                <>
                  <div className="result-label">QUEUED</div>
                  <div className="result-big blue">#{result.queuePosition}</div>
                  <div className="result-msg">{result.message}</div>
                </>
              )}
            </div>
          )}
        </div>
      </div>
      
      {/* Live Shaft Visualization */}
      <div className="shafts-section mb-20">
        <div className="shafts-title">
          <span>LIVE LIFT POSITIONS</span>
          <span className="pill pill-blue pulse">LIVE</span>
        </div>
        <div className="shafts-wrap">
          {lifts.map(l => <LiftShaft key={l.liftNumber} lift={l} />)}
        </div>
      </div>


      {showModal && (
        <CreateRouteModal token={user.token} onClose={() => setShowModal(false)} onCreated={r => setRoutes(p => [...p, r])} />
      )}
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
//  ADMIN DASHBOARD
// ═══════════════════════════════════════════════════════════════
function AdminDashboard({ page }) {
  const { user } = useAuth();
  const [stats, setStats] = useState(null);
  const [allTrips, setAllTrips] = useState([]);
  const [search, setSearch] = useState("");
  const [searchResult, setSearchResult] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchStats = useCallback(async () => {
    try { setStats(await api.get("/admin/dashboard", user.token)); } catch {}
  }, [user.token]);

  useEffect(() => {
    if (page === "overview") { fetchStats(); const t = setInterval(fetchStats, 1000); return () => clearInterval(t); }
    if (page === "trips") { setLoading(true); api.get("/admin/trips", user.token).then(setAllTrips).catch(() => {}).finally(() => setLoading(false)); }
  }, [page, fetchStats, user.token]);

  const toggleMaintenance = async (lift, active) => {
    try { await api.patch(`/admin/lifts/${user.buildingId || 1}/${lift.liftNumber}/maintenance?active=${active}`, null, user.token); fetchStats(); } catch {}
  };

  if (page === "overview" && !stats) return <div className="page"><div className="empty"><Spin /></div></div>;

  if (page === "overview") return (
    <div className="page">
      <div className="stats-grid">
        <div className="stat-card"><div className="stat-label">Trips Today</div><div className="stat-value">{stats.totalTripsToday}</div></div>
        <div className="stat-card"><div className="stat-label">All Time</div><div className="stat-value">{stats.totalTripsAllTime}</div></div>
        <div className="stat-card"><div className="stat-label">Active</div><div className="stat-value" style={{ color: "var(--blue)" }}>{stats.activeLifts}</div></div>
        <div className="stat-card"><div className="stat-label">Idle</div><div className="stat-value" style={{ color: "var(--green)" }}>{stats.idleLifts}</div></div>
        <div className="stat-card"><div className="stat-label">Queued</div><div className="stat-value" style={{ color: "var(--amber)" }}>{stats.queuedRequests}</div></div>
        <div className="stat-card"><div className="stat-label">Maintenance</div><div className="stat-value" style={{ color: stats.maintenanceLifts > 0 ? "var(--red)" : "var(--text3)" }}>{stats.maintenanceLifts}</div></div>
      </div>

      {/* Live shafts in admin too */}
      <div className="shafts-section mb-20">
        <div className="shafts-title">
          <span>LIVE LIFT POSITIONS</span>
          <span className="pill pill-blue pulse">LIVE</span>
        </div>
        <div className="shafts-wrap">
          {(stats.liftStatuses || []).map(l => <LiftShaft key={l.liftNumber} lift={l} />)}
        </div>
      </div>

      <div className="g2">
        {/* Top Floors */}
        <div className="card">
          <div className="card-title">TOP FLOORS</div>
          {(stats.topFloors || []).length === 0 ? <div className="empty">No data yet</div> :
            stats.topFloors.map((f, i) => {
              const max = stats.topFloors[0]?.count || 1;
              return (
                <div key={i} className="floor-bar">
                  <div className="floor-bar-label">{f.floor}F</div>
                  <div className="floor-bar-track"><div className="floor-bar-fill" style={{ width: `${(f.count / max) * 100}%` }} /></div>
                  <div className="floor-bar-count">{f.count}</div>
                </div>
              );
            })
          }
        </div>

        {/* Recent Trips */}
        <div className="card">
          <div className="card-title">RECENT TRIPS</div>
          {(stats.recentTrips || []).slice(0, 6).map(t => (
            <div key={t.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "8px 0", borderBottom: "1px solid var(--border)" }}>
              <div>
                <div style={{ fontSize: "13px", fontWeight: 600 }}>{t.fullName || t.employeeId}</div>
                <div className="mono">{t.fromFloor}F → {t.toFloor}F · L-{t.liftNumber}</div>
              </div>
              <span className={`tag t-${t.tripStatus}`}>{t.tripStatus}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Lift control */}
      <div className="card mt-20">
        <div className="card-title">LIFT CONTROL</div>
        <div className="lift-grid">
          {(stats.liftStatuses || []).map(l => (
            <div key={l.liftNumber} style={{ textAlign: "center" }}>
              <div style={{ background: "var(--bg3)", border: `1px solid ${l.liftStatus === "IDLE" ? "var(--border)" : l.liftStatus === "MAINTENANCE" ? "rgba(232,64,64,0.4)" : "var(--blue3)"}`, borderRadius: "6px", padding: "12px 8px", marginBottom: "6px" }}>
                <div className="lift-name">{`L-${l.liftNumber}`}</div>
                <div className="lift-num">{l.liftNumber}</div>
                <div className="lift-floor">{l.currentFloor}F</div>
                <div className={`lift-status-pill s-${l.liftStatus}`}>{l.liftStatus}</div>
                {l.phase && l.phase !== "IDLE" && <div style={{ fontFamily: "var(--font-m)", fontSize: "9px", color: "var(--blue)", marginTop: "3px" }}>{l.phase}</div>}
              </div>
              <button className={`btn btn-sm ${l.liftStatus === "MAINTENANCE" ? "btn-outline" : "btn-danger"}`}
                style={{ width: "100%", fontSize: "10px", padding: "4px" }}
                onClick={() => toggleMaintenance(l, l.liftStatus !== "MAINTENANCE")}>
                {l.liftStatus === "MAINTENANCE" ? "RESTORE" : "MAINT"}
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );

  if (page === "trips") return (
    <div className="page">
      <div className="section-hdr"><div className="section-title">ALL <span>TRIPS</span></div></div>
      <div className="card">{loading ? <div className="empty"><Spin /></div> : <TripTable trips={allTrips} />}</div>
    </div>
  );

  if (page === "lookup") return (
    <div className="page">
      <div className="section-hdr"><div className="section-title">EMPLOYEE <span>LOOKUP</span></div></div>
      <div className="card">
        <div className="search-row">
          <input className="input" placeholder="Enter Employee ID" value={search} onChange={e => setSearch(e.target.value)} onKeyDown={e => e.key === "Enter" && (() => { setLoading(true); api.get(`/admin/trips/employee/${search.trim()}`, user.token).then(d => setSearchResult({ id: search.trim(), trips: d })).catch(() => setSearchResult({ id: search.trim(), trips: [] })).finally(() => setLoading(false)); })()} />
          <button className="btn btn-primary" onClick={() => { setLoading(true); api.get(`/admin/trips/employee/${search.trim()}`, user.token).then(d => setSearchResult({ id: search.trim(), trips: d })).catch(() => setSearchResult({ id: search.trim(), trips: [] })).finally(() => setLoading(false)); }} disabled={loading}>{loading ? <Spin /> : "SEARCH"}</button>
        </div>
        {searchResult && (
          <>
            <div style={{ marginBottom: "14px", fontFamily: "var(--font-m)", fontSize: "12px", color: "var(--text2)" }}>
              Employee <span style={{ color: "var(--blue)" }}>{searchResult.id}</span> — {searchResult.trips.length} trip(s)
            </div>
            {searchResult.trips.length === 0 ? <div className="empty">No trips found</div> : <TripTable trips={searchResult.trips} />}
          </>
        )}
      </div>
    </div>
  );

  return null;
}

// ═══════════════════════════════════════════════════════════════
//  TRIP TABLE
// ═══════════════════════════════════════════════════════════════
function TripTable({ trips }) {
  if (!trips?.length) return <div className="empty"><div className="empty-icon">📋</div>No trips found</div>;
  return (
    <div className="tbl-wrap">
      <table>
        <thead><tr><th>#</th><th>Employee</th><th>Type</th><th>From</th><th>To</th><th>Lift</th><th>Status</th><th>Requested</th><th>Completed</th></tr></thead>
        <tbody>
          {trips.map(t => (
            <tr key={t.id}>
              <td className="mono">{t.id}</td>
              <td style={{ fontSize: "13px" }}>{t.fullName || t.employeeId}</td>
              <td><span className={`tag t-${t.tripType}`}>{t.tripType}</span></td>
              <td className="mono">{t.fromFloor}F</td>
              <td className="mono">{t.toFloor}F</td>
              <td className="mono">{t.liftNumber > 0 ? `L-${t.liftNumber}` : "—"}</td>
              <td><span className={`tag t-${t.tripStatus}`}>{t.tripStatus}</span></td>
              <td className="mono">{t.requestedAt ? new Date(t.requestedAt).toLocaleString() : "—"}</td>
              <td className="mono">{t.completedAt ? new Date(t.completedAt).toLocaleString() : "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
//  APP SHELL
// ═══════════════════════════════════════════════════════════════
function AppShell() {
  const { user, logout } = useAuth();
  const isAdmin = user.role === "ADMIN" || user.role === "SUPER_ADMIN";
  const [page, setPage] = useState(isAdmin ? "overview" : "request");

  const nav = isAdmin
    ? [{ id: "overview", icon: "📊", label: "Overview" }, { id: "trips", icon: "📋", label: "All Trips" }, { id: "lookup", icon: "🔍", label: "Emp Lookup" }]
    : [{ id: "request", icon: "⬆", label: "Request Lift" }, { id: "trips", icon: "📋", label: "My Trips" }];

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="logo">
          <div className="logo-icon">⬆</div>
          <div><div className="logo-text">ALMS</div><div className="logo-sub">Pro v2.0</div></div>
        </div>
        <div className="nav-section">
          <div className="nav-section-title">{isAdmin ? "Admin" : "Employee"}</div>
          {nav.map(n => (
            <button key={n.id} className={`nav-item ${page === n.id ? "active" : ""}`} onClick={() => setPage(n.id)}>
              <span className="nav-icon">{n.icon}</span>
              <span className="nav-label">{n.label}</span>
            </button>
          ))}
        </div>
        <div className="sidebar-user">
          <div className="user-info">
            <div className="user-avatar">{(user.fullName || user.employeeId || "?")[0].toUpperCase()}</div>
            <div><div className="user-name">{user.fullName || user.employeeId}</div><div className="user-role">{user.role} · FL{user.homeFloor}</div></div>
          </div>
          <button className="btn-logout" onClick={logout}>LOG OUT</button>
        </div>
      </aside>
      <div className="main-content">
        <div className="topbar">
          <div className="topbar-title">{isAdmin ? <><span>ADMIN</span> PANEL</> : <><span>LIFT</span> CONSOLE</>}</div>
          <div className="topbar-meta">
            <span className="mono" style={{ fontSize: "11px", color: "var(--text3)" }}>{user.employeeId}</span>
            <span className="pill pill-blue">{user.buildingName || "HQ"}</span>
          </div>
        </div>
        {isAdmin ? <AdminDashboard page={page} /> : <UserDashboard page={page} />}
      </div>
    </div>
  );
}

function Inner() {
  const { user } = useAuth();
  return <>{user ? <AppShell /> : <AuthPage />}</>;
}

export default function Root() {
  return <AuthProvider><style>{CSS}</style><Inner /></AuthProvider>;
}
// Override Root with proper inner component
