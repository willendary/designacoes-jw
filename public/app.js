import { initializeApp } from "https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signInWithPopup,
  GoogleAuthProvider,
  signOut
} from "https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js";
import {
  getFirestore,
  collection,
  onSnapshot
} from "https://www.gstatic.com/firebasejs/12.19.0/firebase-firestore.js";

const firebaseConfig = {
  apiKey: "AIzaSyDpLo4zAsQ8Tl4V6MJ-lp5hgnQSaaZvD_0",
  authDomain: "designacoes-jw.firebaseapp.com",
  projectId: "designacoes-jw",
  storageBucket: "designacoes-jw.firebasestorage.app",
  messagingSenderId: "859002390487",
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

// Estado Global da Aplicação
const state = {
  user: null,
  brothers: [],
  privileges: [],
  meetings: [],
  publicTalks: [],
  fieldServiceGroups: [],
  cleaningSchedules: []
};

// Elementos DOM
const authLoading = document.getElementById("authLoading");
const userInfo = document.getElementById("userInfo");
const userEmail = document.getElementById("userEmail");
const logoutBtn = document.getElementById("logoutBtn");
const showLoginBtn = document.getElementById("showLoginBtn");
const loginView = document.getElementById("loginView");
const appView = document.getElementById("appView");
const loginForm = document.getElementById("loginForm");
const loginError = document.getElementById("loginError");
const googleLoginBtn = document.getElementById("googleLoginBtn");

// Abas
const tabButtons = document.querySelectorAll(".tab-btn");
const tabContents = document.querySelectorAll(".tab-content");

tabButtons.forEach(btn => {
  btn.addEventListener("click", () => {
    const targetId = btn.getAttribute("data-tab");
    tabButtons.forEach(b => {
      b.classList.remove("bg-blue-700", "text-white", "shadow-sm");
      b.classList.add("text-slate-600", "hover:bg-slate-100");
    });
    btn.classList.add("bg-blue-700", "text-white", "shadow-sm");
    btn.classList.remove("text-slate-600", "hover:bg-slate-100");

    tabContents.forEach(content => {
      if (content.id === targetId) {
        content.classList.remove("hidden");
      } else {
        content.classList.add("hidden");
      }
    });
  });
});

// Autenticação
onAuthStateChanged(auth, user => {
  authLoading.classList.add("hidden");
  state.user = user;

  if (user) {
    userEmail.textContent = user.email || user.displayName || "Usuário Conectado";
    userInfo.classList.remove("hidden");
    userInfo.classList.add("flex");
    showLoginBtn.classList.add("hidden");
    loginView.classList.add("hidden");
    appView.classList.remove("hidden");
    initDataListeners();
  } else {
    userInfo.classList.add("hidden");
    userInfo.classList.remove("flex");
    showLoginBtn.classList.remove("hidden");
    loginView.classList.remove("hidden");
    appView.classList.add("hidden");
  }
});

loginForm.addEventListener("submit", async e => {
  e.preventDefault();
  const email = document.getElementById("emailInput").value.trim();
  const pass = document.getElementById("passwordInput").value;
  loginError.classList.add("hidden");

  try {
    await signInWithEmailAndPassword(auth, email, pass);
  } catch (err) {
    loginError.textContent = err.message || "E-mail ou senha incorretos.";
    loginError.classList.remove("hidden");
  }
});

googleLoginBtn.addEventListener("click", async () => {
  loginError.classList.add("hidden");
  const provider = new GoogleAuthProvider();
  try {
    await signInWithPopup(auth, provider);
  } catch (err) {
    loginError.textContent = err.message || "Falha no login com Google.";
    loginError.classList.remove("hidden");
  }
});

logoutBtn.addEventListener("click", () => signOut(auth));
showLoginBtn.addEventListener("click", () => {
  loginView.classList.remove("hidden");
  appView.classList.add("hidden");
});

// Sincronização em Tempo Real com Firestore
let unsubscribeListeners = [];

function initDataListeners() {
  unsubscribeListeners.forEach(u => u());
  unsubscribeListeners = [];

  const ws = collection(db, "workspaces", "designacoes-jw", "brothers");
  unsubscribeListeners.push(onSnapshot(ws, snap => {
    state.brothers = snap.docs.map(d => ({ id: Number(d.id), ...d.data() }));
    renderAll();
  }));

  const ps = collection(db, "workspaces", "designacoes-jw", "privileges");
  unsubscribeListeners.push(onSnapshot(ps, snap => {
    state.privileges = snap.docs.map(d => ({ id: Number(d.id), ...d.data() }));
    renderAll();
  }));

  const ms = collection(db, "workspaces", "designacoes-jw", "meetings");
  unsubscribeListeners.push(onSnapshot(ms, snap => {
    state.meetings = snap.docs.map(d => ({ id: Number(d.id), ...d.data() }));
    renderAll();
  }));

  const ts = collection(db, "workspaces", "designacoes-jw", "publicTalks");
  unsubscribeListeners.push(onSnapshot(ts, snap => {
    state.publicTalks = snap.docs.map(d => ({ id: Number(d.id), ...d.data() }));
    renderTalks();
  }));

  const gs = collection(db, "workspaces", "designacoes-jw", "fieldServiceGroups");
  unsubscribeListeners.push(onSnapshot(gs, snap => {
    state.fieldServiceGroups = snap.docs.map(d => ({ id: Number(d.id), ...d.data() }));
    renderGroupsAndCleaning();
  }));

  const cs = collection(db, "workspaces", "designacoes-jw", "cleaningSchedules");
  unsubscribeListeners.push(onSnapshot(cs, snap => {
    state.cleaningSchedules = snap.docs.map(d => ({ id: Number(d.id), ...d.data() }));
    renderGroupsAndCleaning();
  }));
}

function parseDateStr(str) {
  if (!str) return new Date(0);
  const parts = str.split("/");
  if (parts.length < 3) return new Date(0);
  return new Date(Number(parts[2]), Number(parts[1]) - 1, Number(parts[0]));
}

function renderAll() {
  renderNextMeetingBanner();
  renderMeetings();
  renderTalks();
  renderGroupsAndCleaning();
  renderPublishers();
}

// 1. BANNER PRÓXIMA REUNIÃO
function renderNextMeetingBanner() {
  const banner = document.getElementById("nextMeetingBanner");
  const title = document.getElementById("nextMeetingTitle");
  const tag = document.getElementById("nextMeetingTag");
  const desc = document.getElementById("nextMeetingAssignments");
  const shareBtn = document.getElementById("shareNextMeetingBtn");

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const upcoming = state.meetings
    .map(m => {
      const d = parseDateStr(m.date);
      const diffDays = Math.ceil((d - today) / (1000 * 60 * 60 * 24));
      return { meeting: m, date: d, diffDays };
    })
    .filter(x => x.diffDays >= 0)
    .sort((a, b) => a.diffDays - b.diffDays);

  if (upcoming.length === 0) {
    title.textContent = "Nenhuma reunião agendada";
    tag.textContent = "Sem reuniões";
    desc.textContent = "Gere ou adicione reuniões no aplicativo.";
    shareBtn.classList.add("hidden");
    return;
  }

  shareBtn.classList.remove("hidden");
  const next = upcoming[0];
  const m = next.meeting;

  if (next.diffDays === 0) tag.textContent = "🚨 HOJE";
  else if (next.diffDays === 1) tag.textContent = "⏳ AMANHÃ";
  else tag.textContent = `Em ${next.diffDays} dias`;

  title.textContent = `${m.date} — ${m.type || "Reunião Congregacional"}`;

  const assignedNames = (m.assignments || [])
    .map(a => {
      const b = state.brothers.find(x => x.id === a.brotherId);
      const p = state.privileges.find(x => x.id === a.privilegeId);
      return b && p ? `${p.name}: ${b.name}` : null;
    })
    .filter(Boolean);

  desc.textContent = assignedNames.length > 0
    ? `Designados: ${assignedNames.join(" • ")}`
    : "Nenhum irmão designado ainda.";

  shareBtn.onclick = () => {
    const text = encodeURIComponent(
      `📋 *Designações — ${m.type}*\n📅 *Data:* ${m.date}\n\n` +
      assignedNames.map(x => `• ${x}`).join("\n")
    );
    window.open(`https://wa.me/?text=${text}`, "_blank");
  };
}

// 2. REUNIÕES
function renderMeetings() {
  const list = document.getElementById("meetingsList");
  list.innerHTML = "";

  const sorted = [...state.meetings].sort((a, b) => parseDateStr(a.date) - parseDateStr(b.date));

  if (sorted.length === 0) {
    list.innerHTML = `<div class="col-span-full p-8 text-center text-slate-400 bg-white rounded-2xl border border-slate-100">Nenhuma reunião cadastrada.</div>`;
    return;
  }

  sorted.forEach(m => {
    const card = document.createElement("div");
    card.className = "bg-white rounded-2xl p-5 shadow-sm border border-slate-100 space-y-4 hover:shadow-md transition";

    const parts = m.date.split("/");
    const day = parts[0] || "--";
    const dateObj = parseDateStr(m.date);
    const weekday = dateObj.toLocaleDateString("pt-BR", { weekday: "short" }).toUpperCase();

    const assignmentsHtml = (m.assignments || []).map(a => {
      const p = state.privileges.find(x => x.id === a.privilegeId)?.name || "Privilégio";
      const b = state.brothers.find(x => x.id === a.brotherId)?.name || "—";
      return `<li class="flex justify-between text-xs py-1 border-b border-slate-50 last:border-0">
        <span class="font-medium text-slate-500">${p}</span>
        <span class="font-semibold text-blue-700">${b}</span>
      </li>`;
    }).join("");

    card.innerHTML = `
      <div class="flex items-center gap-3">
        <div class="bg-blue-50 text-blue-800 rounded-xl p-2.5 text-center min-w-[54px]">
          <span class="block text-[10px] font-bold tracking-wider">${weekday}</span>
          <span class="block text-xl font-black leading-none mt-0.5">${day}</span>
        </div>
        <div>
          <h4 class="font-bold text-slate-800 text-sm">${m.type || "Reunião"}</h4>
          <span class="text-xs text-slate-400">${m.date}</span>
        </div>
      </div>
      <ul class="space-y-0.5 bg-slate-50 p-3 rounded-xl border border-slate-100/60">
        ${assignmentsHtml || '<li class="text-xs text-slate-400 italic">Sem designações registradas</li>'}
      </ul>
      <button class="w-full text-xs font-semibold text-emerald-700 bg-emerald-50 hover:bg-emerald-100 py-2 rounded-xl transition flex items-center justify-center gap-1.5 whatsapp-btn">
        <span>Compartilhar Reunião</span>
      </button>
    `;

    card.querySelector(".whatsapp-btn").onclick = () => {
      const textLines = (m.assignments || []).map(a => {
        const p = state.privileges.find(x => x.id === a.privilegeId)?.name || "Privilégio";
        const b = state.brothers.find(x => x.id === a.brotherId)?.name || "—";
        return `• *${p}:* ${b}`;
      }).join("\n");

      const text = encodeURIComponent(
        `📋 *Designações — ${m.type}*\n📅 *Data:* ${m.date}\n\n${textLines}\n\nPor favor, confirmem o recebimento.`
      );
      window.open(`https://wa.me/?text=${text}`, "_blank");
    };

    list.appendChild(card);
  });
}

// 3. DISCURSOS PÚBLICOS
function renderTalks() {
  const list = document.getElementById("talksList");
  if (!list) return;
  list.innerHTML = "";

  const sorted = [...state.publicTalks].sort((a, b) => parseDateStr(b.date) - parseDateStr(a.date));

  if (sorted.length === 0) {
    list.innerHTML = `<div class="col-span-full p-8 text-center text-slate-400 bg-white rounded-2xl border border-slate-100">Nenhum discurso público registrado.</div>`;
    return;
  }

  sorted.forEach(t => {
    const card = document.createElement("div");
    card.className = "bg-white rounded-2xl p-5 shadow-sm border border-slate-100 space-y-3";

    const hosp = state.brothers.find(x => x.id === t.hospitalityBrotherId);
    const themeStr = t.themeNumber ? `Nº ${t.themeNumber} — "${t.themeTitle}"` : `"${t.themeTitle}"`;

    card.innerHTML = `
      <div class="flex items-center justify-between">
        <span class="text-xs font-bold text-blue-700 bg-blue-50 px-2.5 py-1 rounded-full">📅 ${t.date}</span>
        <span class="text-xs font-semibold ${t.confirmed ? 'text-emerald-700 bg-emerald-50' : 'text-amber-700 bg-amber-50'} px-2.5 py-0.5 rounded-full">
          ${t.confirmed ? 'Confirmado' : 'Pendente'}
        </span>
      </div>
      <div>
        <h4 class="font-bold text-slate-800 text-sm leading-snug">${themeStr}</h4>
        <p class="text-xs text-slate-600 mt-1">🎤 Orador: <strong>${t.speakerName}</strong> ${t.speakerCongregation ? `(${t.speakerCongregation})` : ''}</p>
        ${hosp ? `<p class="text-xs text-slate-500 mt-0.5">🍽 Hospitalidade: ${hosp.name}</p>` : ''}
      </div>
      ${t.speakerPhone ? `
        <button class="w-full text-xs font-semibold text-emerald-700 bg-emerald-50 hover:bg-emerald-100 py-2 rounded-xl transition flex items-center justify-center gap-1.5 whatsapp-speaker-btn">
          <span>Enviar Confirmação WhatsApp</span>
        </button>
      ` : ''}
    `;

    const btn = card.querySelector(".whatsapp-speaker-btn");
    if (btn) {
      btn.onclick = () => {
        const text = encodeURIComponent(
          `🎤 *Discurso Público — Confirmação*\n\nOlá irmão *${t.speakerName}*!\nConfirmamos com alegria sua visita para o discurso público no dia *${t.date}*.\nTema: *${themeStr}*.\nFicamos à disposição!`
        );
        const cleanPhone = t.speakerPhone.replace(/\D/g, "");
        window.open(`https://wa.me/${cleanPhone}?text=${text}`, "_blank");
      };
    }

    list.appendChild(card);
  });
}

// 4. LIMPEZA & GRUPOS
function renderGroupsAndCleaning() {
  const cleanList = document.getElementById("cleaningList");
  const groupList = document.getElementById("groupsList");
  if (!cleanList || !groupList) return;

  cleanList.innerHTML = "";
  groupList.innerHTML = "";

  // Escala de Limpeza
  if (state.cleaningSchedules.length === 0) {
    cleanList.innerHTML = `<div class="p-6 text-center text-slate-400 bg-white rounded-2xl border border-slate-100">Nenhuma escala de limpeza registrada.</div>`;
  } else {
    state.cleaningSchedules.forEach(s => {
      const group = state.fieldServiceGroups.find(g => g.id === s.groupId);
      const overseer = group ? state.brothers.find(b => b.id === group.overseerBrotherId) : null;

      const item = document.createElement("div");
      item.className = "bg-white p-4 rounded-xl border border-slate-100 flex items-center justify-between";
      item.innerHTML = `
        <div>
          <span class="text-xs font-bold text-slate-500 block">Semana: ${s.weekDate}</span>
          <span class="text-sm font-semibold text-slate-800">${group?.name || 'Grupo não definido'}</span>
          ${overseer ? `<span class="text-xs text-slate-400 block">Dirigente: ${overseer.name}</span>` : ''}
        </div>
        <span class="text-xs font-semibold px-2.5 py-1 rounded-full ${s.completed ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}">
          ${s.completed ? 'Concluída' : 'Agendada'}
        </span>
      `;
      cleanList.appendChild(item);
    });
  }

  // Grupos de Campo
  if (state.fieldServiceGroups.length === 0) {
    groupList.innerHTML = `<div class="p-6 text-center text-slate-400 bg-white rounded-2xl border border-slate-100">Nenhum grupo cadastrado.</div>`;
  } else {
    state.fieldServiceGroups.forEach(g => {
      const overseer = state.brothers.find(b => b.id === g.overseerBrotherId);
      const count = state.brothers.filter(b => b.groupId === g.id).length;

      const item = document.createElement("div");
      item.className = "bg-white p-4 rounded-xl border border-slate-100 space-y-1";
      item.innerHTML = `
        <h4 class="font-bold text-sm text-slate-800">${g.name}</h4>
        <p class="text-xs text-slate-500">Dirigente: ${overseer?.name || 'A definir'}</p>
        <span class="inline-block text-[11px] text-blue-700 font-medium bg-blue-50 px-2 py-0.5 rounded-md mt-1">
          ${count} publicador(es)
        </span>
      `;
      groupList.appendChild(item);
    });
  }
}

// 5. PUBLICADORES
function renderPublishers() {
  const list = document.getElementById("publishersList");
  if (!list) return;
  const search = (document.getElementById("searchPublisherInput")?.value || "").toLowerCase();
  list.innerHTML = "";

  const filtered = state.brothers.filter(b => b.name.toLowerCase().includes(search));

  if (filtered.length === 0) {
    list.innerHTML = `<div class="col-span-full p-8 text-center text-slate-400 bg-white rounded-2xl border border-slate-100">Nenhum publicador encontrado.</div>`;
    return;
  }

  filtered.forEach(b => {
    const card = document.createElement("div");
    card.className = "bg-white p-4 rounded-xl border border-slate-100 space-y-2";
    const isSister = b.gender === "FEMALE";
    const group = state.fieldServiceGroups.find(g => g.id === b.groupId);

    card.innerHTML = `
      <div class="flex items-center gap-2.5">
        <div class="w-8 h-8 rounded-full ${isSister ? 'bg-pink-100 text-pink-700' : 'bg-blue-100 text-blue-700'} flex items-center justify-center text-xs font-bold">
          ${b.name.charAt(0).toUpperCase()}
        </div>
        <div>
          <h5 class="font-semibold text-xs text-slate-800 leading-tight">${b.name}</h5>
          <span class="text-[10px] text-slate-400">${isSister ? 'Irmã' : (b.role || 'Publicador')}</span>
        </div>
      </div>
      ${group ? `<div class="text-[11px] text-slate-500">${group.name}</div>` : ''}
    `;
    list.appendChild(card);
  });
}

document.getElementById("searchPublisherInput")?.addEventListener("input", renderPublishers);

// Ações Globais
document.getElementById("printBtn")?.addEventListener("click", () => window.print());

document.getElementById("copyWhatsAppBtn")?.addEventListener("click", () => {
  const text = state.meetings.map(m => {
    const assignments = (m.assignments || []).map(a => {
      const p = state.privileges.find(x => x.id === a.privilegeId)?.name || "";
      const b = state.brothers.find(x => x.id === a.brotherId)?.name || "";
      return `  • ${p}: ${b}`;
    }).join("\n");
    return `📅 *${m.date}* (${m.type})\n${assignments}`;
  }).join("\n\n");

  navigator.clipboard.writeText(text).then(() => {
    alert("Escala completa copiada para a área de transferência!");
  });
});
