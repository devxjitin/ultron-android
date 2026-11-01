// Ultron Control: edits the prompt and skill text the Ultron app loads from Firestore (remote/prompts, remote_skills/*).
// Security lives in firestore.rules; the owner check here only decides what to show.
(function () {
  'use strict';

  const OWNER_EMAIL = 'devxjitin@gmail.com';
  const NAME_RE = /^[a-z0-9][a-z0-9_-]{0,47}$/;
  const MAX_BODY = 8000;
  const MAX_DESC = 300;

  const PROMPTS = [
    { key: 'persona', title: 'Persona', icon: 'user', max: 20000, tall: true,
      desc: 'Who Ultron is and how it speaks. {owner} and {Owner} are replaced with the owner\'s name in the app.' },
    { key: 'policy', title: 'Answer policy', icon: 'shield', max: 12000, tall: true,
      desc: 'Fixed rules for tools, answers, memory and conduct. They apply after the persona and win when the two disagree. The text must still mention use_skill, otherwise the app keeps its own.' },
    { key: 'assistantName', title: 'Assistant name', icon: 'user', max: 24, single: true,
      desc: 'The name shown on the home screen and given to the assistant in its prompt.' },
    { key: 'greeting', title: 'Greeting', icon: 'chat', max: 60, single: true,
      desc: 'The headline on the home screen while Ultron is idle.' },
    { key: 'documentFormatPrompt', title: 'Document prompt', icon: 'file', max: 10000,
      desc: 'Instructions used when a PDF is turned into searchable knowledge.' },
    { key: 'retrievalChunkLimit', title: 'Search results', icon: 'search', number: true, min: 1, max: 15,
      desc: 'How many pieces of the owner\'s documents Ultron reads for each search. More gives richer answers but costs more tokens.' },
  ];

  const ICONS = {
    home: '<path d="M3 11l9-8 9 8M5 10v10h5v-6h4v6h5V10"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7"/>',
    shield: '<path d="M12 3l8 3v6c0 4.5-3.2 8-8 9-4.8-1-8-4.5-8-9V6z"/><path d="M9 12l2 2 4-4"/>',
    chat: '<path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/>',
    file: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M9 13h6M9 17h6"/>',
    skills: '<path d="M12 3l9 5-9 5-9-5z"/><path d="M3 13l9 5 9-5M3 17.5l9 5 9-5" opacity=".6"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    trash: '<path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>',
    save: '<path d="M5 4h11l3 3v13H5z"/><path d="M8 4v5h7V4M8 20v-6h8v6"/>',
  };

  const db = firebase.firestore();
  const auth = firebase.auth();
  const FieldValue = firebase.firestore.FieldValue;
  const $ = (id) => document.getElementById(id);

  const state = {
    defaults: { skills: {} },
    prompts: {},
    skillDocs: {},
    skill: { open: false, id: null, isNew: false },
    skillFilter: '',
    route: 'overview',
    dirty: false,
    skipHash: false,
    unsubscribe: null,
  };

  // ---- small helpers ----
  function h(tag, props, ...kids) {
    const node = document.createElement(tag);
    Object.entries(props || {}).forEach(([k, v]) => {
      if (v === false || v == null) return;
      if (k === 'class') node.className = v;
      else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
      else node.setAttribute(k, v === true ? '' : v);
    });
    kids.flat().forEach((c) => { if (c != null && c !== false) node.append(c); });
    return node;
  }

  function icon(name, size) {
    const s = size || 18;
    const wrap = document.createElement('span');
    wrap.style.display = 'inline-flex';
    wrap.innerHTML = `<svg viewBox="0 0 24 24" width="${s}" height="${s}" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[name]}</svg>`;
    return wrap;
  }

  function toast(message, kind) {
    const node = h('div', { class: 'toast' + (kind === 'err' ? ' err' : '') }, message);
    $('toasts').append(node);
    setTimeout(() => node.remove(), kind === 'err' ? 7000 : 3500);
  }

  function counter(input, node, max) {
    const update = () => {
      const n = input.value.trim().length;
      node.textContent = `${n.toLocaleString()} / ${max.toLocaleString()}`;
      node.classList.toggle('over', n > max);
    };
    input.addEventListener('input', update);
    update();
    return update;
  }

  let fieldSeq = 0;
  function field(label, control, help, right) {
    if (!control.id) control.id = 'f' + (++fieldSeq);
    return h('div', { class: 'field' },
      h('label', { for: control.id }, h('span', {}, label), right || null), control, help ? h('div', { class: 'help' }, help) : null);
  }

  function setDirty(value) {
    state.dirty = value;
    document.querySelectorAll('[data-dirty-flag]').forEach((n) => { n.hidden = !value; });
    document.querySelectorAll('[data-needs-dirty]').forEach((n) => { n.disabled = !value; });
  }

  function pageHead(title, desc, actions) {
    return h('div', { class: 'page-head' },
      h('div', {}, h('h2', {}, title), desc ? h('p', {}, desc) : null),
      actions && actions.length ? h('div', { class: 'page-actions' }, actions) : null);
  }

  function show(...nodes) {
    $('content').replaceChildren(...nodes);
  }

  // ---- auth ----
  $('sign-in').addEventListener('click', () => {
    auth.signInWithPopup(new firebase.auth.GoogleAuthProvider()).catch((e) => toast('Sign-in failed. ' + e.message, 'err'));
  });
  $('sign-out').addEventListener('click', () => auth.signOut());
  $('denied-out').addEventListener('click', () => auth.signOut());

  auth.onAuthStateChanged((user) => {
    if (state.unsubscribe) { state.unsubscribe(); state.unsubscribe = null; }
    if (!user) {
      document.body.dataset.state = 'out';
      $('auth-signed-out').hidden = false;
      $('auth-denied').hidden = true;
      return;
    }
    if (user.email !== OWNER_EMAIL) {
      document.body.dataset.state = 'denied';
      $('auth-signed-out').hidden = true;
      $('auth-denied').hidden = false;
      $('denied-email').textContent = user.email;
      return;
    }
    $('who-email').textContent = user.email;
    $('avatar').textContent = user.email.charAt(0).toUpperCase();
    start();
  });

  async function start() {
    document.body.dataset.state = 'ready';
    show(h('div', { class: 'empty' }, 'Loading...'));
    try {
      const res = await fetch('defaults.json', { cache: 'no-store' });
      if (res.ok) state.defaults = await res.json();
    } catch (e) { /* the panel still works without the bundled text */ }
    try {
      const doc = await db.collection('remote').doc('prompts').get();
      state.prompts = doc.exists ? doc.data() : {};
    } catch (e) { toast('Could not load prompts. ' + e.message, 'err'); }
    state.unsubscribe = db.collection('remote_skills').onSnapshot((snap) => {
      state.skillDocs = {};
      snap.forEach((d) => { state.skillDocs[d.id] = d.data(); });
      renderMenu();
      if (state.route === 'overview') renderOverview();
      if (state.route === 'skills') renderSkillList();
    }, (e) => toast('Could not load skills. ' + e.message, 'err'));
    render();
  }

  // ---- menu and routing ----
  function renderMenu() {
    const menu = $('menu');
    const link = (route, label, iconName, count) => h('a', { href: '#' + route, 'aria-current': state.route === route ? 'page' : null },
      icon(iconName), h('span', {}, label), count != null ? h('span', { class: 'count' }, String(count)) : null);
    menu.replaceChildren(
      link('overview', 'Overview', 'home'),
      h('div', { class: 'menu-group' }, 'Assistant'),
      ...PROMPTS.map((p) => link(p.key, p.title, p.icon)),
      h('div', { class: 'menu-group' }, 'Skills'),
      link('skills', 'Skills', 'skills', Object.keys(state.skillDocs).length));
  }

  function closeMenu() {
    $('sidebar').classList.remove('open');
    $('scrim').hidden = true;
    $('menu-toggle').setAttribute('aria-expanded', 'false');
  }
  $('menu-toggle').addEventListener('click', () => {
    const open = $('sidebar').classList.toggle('open');
    $('scrim').hidden = !open;
    $('menu-toggle').setAttribute('aria-expanded', String(open));
  });
  $('scrim').addEventListener('click', closeMenu);

  function render() {
    const route = (location.hash || '#overview').slice(1);
    state.route = route === 'skills' || PROMPTS.some((p) => p.key === route) ? route : 'overview';
    state.dirty = false;
    closeMenu();
    renderMenu();
    const prompt = PROMPTS.find((p) => p.key === state.route);
    if (prompt) renderPrompt(prompt);
    else if (state.route === 'skills') renderSkills();
    else renderOverview();
    document.title = (prompt ? prompt.title : state.route === 'skills' ? 'Skills' : 'Overview') + ' - Ultron Control';
    window.scrollTo(0, 0);
  }

  window.addEventListener('hashchange', () => {
    if (state.skipHash) { state.skipHash = false; return; }
    if (state.dirty && !confirm('You have unsaved changes. Discard them?')) {
      state.skipHash = true;
      location.hash = '#' + state.route;
      return;
    }
    render();
  });
  window.addEventListener('beforeunload', (e) => { if (state.dirty) { e.preventDefault(); e.returnValue = ''; } });

  // ---- overview ----
  function promptStatus(p) {
    const v = String(state.prompts[p.key] ?? '').trim();
    if (!v) return h('span', { class: 'badge' }, 'Using app default');
    return h('span', { class: 'badge accent' }, p.number ? `Set to ${v}` : `Edited, ${v.length.toLocaleString()} characters`);
  }

  function renderOverview() {
    const skillIds = Object.keys(state.skillDocs);
    const hidden = skillIds.filter((id) => state.skillDocs[id].published === false).length;
    const minIn = h('input', { class: 'input narrow', type: 'number', min: '0', placeholder: 'Any build', id: 'min-build' });
    minIn.value = state.prompts.minAppVersion != null ? state.prompts.minAppVersion : '';
    const saveMin = h('button', { class: 'btn secondary', onclick: async () => {
      const raw = minIn.value.trim();
      try {
        await db.collection('remote').doc('prompts').set({ minAppVersion: raw ? Number(raw) : FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
        if (raw) state.prompts.minAppVersion = Number(raw); else delete state.prompts.minAppVersion;
        toast('Minimum build saved.');
      } catch (e) { toast('Save failed. ' + e.message, 'err'); }
    } }, 'Save');

    show(
      pageHead('Overview', 'What the Ultron app loads from here. Anything left empty falls back to the text bundled in the app, so the app always works on its own.'),
      h('div', { class: 'grid' },
        ...PROMPTS.map((p) => h('a', { class: 'card tile', href: '#' + p.key },
          h('div', { class: 'meta' }, icon(p.icon), h('h3', {}, p.title)),
          h('p', {}, p.desc.split('. ')[0].replace(/\.$/, '') + '.'),
          h('div', {}, promptStatus(p)))),
        h('a', { class: 'card tile', href: '#skills' },
          h('div', { class: 'meta' }, icon('skills'), h('h3', {}, 'Skills')),
          h('p', {}, 'Instruction sets Ultron opens for a task, like sending a WhatsApp message.'),
          h('div', {}, h('span', { class: skillIds.length ? 'badge accent' : 'badge' },
            skillIds.length ? `${skillIds.length} from here${hidden ? `, ${hidden} hidden` : ''}` : 'Using bundled skills')))),
      h('div', { class: 'section-title' }, 'Compatibility'),
      h('div', { class: 'card card-pad' },
        field('Minimum app build for these prompts', h('div', { class: 'row' }, minIn, saveMin),
          'Apps with an older build ignore the prompts and keep their bundled text. Leave empty to apply to every build.')));
  }

  // ---- prompt editor ----
  function renderPrompt(p) {
    const saved = String(state.prompts[p.key] ?? '');
    const input = p.number
      ? h('input', { class: 'input narrow', type: 'number', min: String(p.min), max: String(p.max), 'aria-label': p.title })
      : p.single
      ? h('input', { class: 'input', maxlength: String(p.max), spellcheck: 'false', 'aria-label': p.title })
      : h('textarea', { class: 'area' + (p.tall ? ' tall' : ''), spellcheck: 'false', 'aria-label': p.title, rows: p.tall ? '20' : '10' });
    input.value = saved;
    const count = h('span', { class: 'counter' });
    const status = h('span', {});
    const bundled = state.defaults[p.key];

    const refresh = () => {
      status.replaceChildren(input.value.trim()
        ? h('span', { class: 'badge accent' }, 'Edited')
        : h('span', { class: 'badge' }, 'Using app default'));
    };
    const update = p.number ? () => {} : counter(input, count, p.max);
    input.addEventListener('input', () => { refresh(); setDirty(input.value.trim() !== saved.trim()); });

    const save = h('button', { class: 'btn primary', 'data-needs-dirty': true, disabled: true, onclick: async () => {
      const value = input.value.trim();
      if (p.number) {
        const n = Number(value);
        if (value && !(Number.isInteger(n) && n >= p.min && n <= p.max)) return toast(`${p.title} must be a whole number from ${p.min} to ${p.max}.`, 'err');
      } else if (value.length > p.max) return toast(`${p.title} is over ${p.max.toLocaleString()} characters.`, 'err');
      if (p.key === 'policy' && value && !value.includes('use_skill')) return toast('The answer policy must mention use_skill.', 'err');
      save.disabled = true;
      try {
        await db.collection('remote').doc('prompts').set({ [p.key]: value ? (p.number ? Number(value) : value) : FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
        if (value) state.prompts[p.key] = p.number ? Number(value) : value; else delete state.prompts[p.key];
        renderPrompt(p);
        toast(value ? `${p.title} saved. Apps pick it up at their next session.` : `${p.title} cleared. Apps use their bundled text.`);
      } catch (e) { save.disabled = false; toast('Save failed. ' + e.message, 'err'); }
    } }, icon('save', 16), 'Save changes');

    const tools = h('div', { class: 'editor-tools' },
      bundled != null ? h('button', { class: 'btn secondary sm', onclick: () => {
        if (input.value.trim() && !confirm('Replace the current value with the bundled one?')) return;
        input.value = bundled; input.dispatchEvent(new Event('input'));
      } }, p.number ? 'Load bundled value' : 'Load bundled text') : null,
      h('button', { class: 'btn ghost sm', onclick: () => {
        if (input.value.trim() && !confirm('Clear the text so the app uses its own default?')) return;
        input.value = ''; input.dispatchEvent(new Event('input'));
      } }, 'Use app default'));

    refresh();
    show(
      pageHead(p.title, p.desc, [save]),
      h('div', { class: 'card' },
        h('div', { class: 'card-pad stack' },
          h('div', { class: 'row', style: 'justify-content:space-between;align-items:center' }, status, count),
          input,
          h('div', { class: 'help' }, 'Fixed for everyone. The app has no setting for this, so it can only be changed here.')),
        h('div', { class: 'editor-foot' }, tools, h('span', { class: 'dirty', 'data-dirty-flag': true, hidden: true }, 'Unsaved changes'))));
    update();
    setDirty(false);
  }

  // ---- skills ----
  function renderSkills() {
    show(
      pageHead('Skills', 'Instruction sets Ultron opens only when a request needs them. A skill with the same name as a bundled one replaces it, and unpublishing a bundled name hides it.',
        [h('button', { class: 'btn secondary', onclick: importBundledSkills }, 'Import bundled skills'),
          h('button', { class: 'btn primary', onclick: () => selectSkill({ open: true, id: null, isNew: true }) }, icon('plus', 16), 'New skill')]),
      h('div', { class: 'split' },
        h('div', { class: 'card skill-nav' },
          h('input', { class: 'input', type: 'search', placeholder: 'Search skills', 'aria-label': 'Search skills', value: state.skillFilter,
            oninput: (e) => { state.skillFilter = e.target.value; renderSkillList(); } }),
          h('div', { class: 'skill-items', id: 'skill-items' })),
        h('div', { class: 'card', id: 'skill-editor' })));
    renderSkillList();
    renderSkillEditor();
  }

  // Copies the skills bundled in the app into Firestore, so they can be edited here from then on.
  async function importBundledSkills() {
    const bundled = state.defaults.skills || {};
    const names = Object.keys(bundled).filter((n) => !state.skillDocs[n]);
    if (!names.length) return toast('Every bundled skill is already here.');
    if (!confirm(`Import ${names.join(', ')}? The app will load them from here instead of its own copy.`)) return;
    const batch = db.batch();
    for (const n of names) {
      const s = bundled[n];
      const tasks = {};
      Object.keys(s.tasks || {}).forEach((t) => { tasks[t] = { description: s.tasks[t].description, body: s.tasks[t].body, published: true }; });
      batch.set(db.collection('remote_skills').doc(n), { description: s.description, body: s.body || '', published: true, tasks, updatedAt: FieldValue.serverTimestamp() });
    }
    try {
      await batch.commit();
      toast(`Imported ${names.join(', ')}.`);
    } catch (e) { toast('Import failed. ' + e.message, 'err'); }
  }

  function renderSkillList() {
    const list = $('skill-items');
    if (!list) return;
    const q = state.skillFilter.trim().toLowerCase();
    const ids = Object.keys(state.skillDocs).filter((id) => id.includes(q)).sort();
    if (!ids.length) {
      list.replaceChildren(h('div', { class: 'help', style: 'padding:8px 10px' }, Object.keys(state.skillDocs).length ? 'No skill matches.' : 'No skills yet. Ultron uses the ones bundled in the app.'));
      return;
    }
    list.replaceChildren(...ids.map((id) => {
      const d = state.skillDocs[id];
      const sub = d.published === false ? 'Hidden from the app' : (state.defaults.skills && state.defaults.skills[id] ? 'Replaces bundled skill' : 'Added from here');
      return h('button', { type: 'button', 'aria-current': String(state.skill.open && state.skill.id === id), onclick: () => selectSkill({ open: true, id, isNew: false }) },
        h('span', { class: 'name' }, id), h('span', { class: 'sub' }, sub));
    }));
  }

  function selectSkill(next) {
    if (state.dirty && !confirm('You have unsaved changes. Discard them?')) return;
    state.dirty = false;
    state.skill = next;
    renderSkillList();
    renderSkillEditor();
  }

  function renderSkillEditor() {
    const box = $('skill-editor');
    if (!box) return;
    const sk = state.skill;
    if (!sk.open) {
      box.replaceChildren(h('div', { class: 'empty' },
        icon('skills', 28), h('h3', {}, 'Choose a skill'),
        h('p', {}, 'Pick one from the list to edit it, or create a new one.'),
        h('button', { class: 'btn secondary', onclick: () => selectSkill({ open: true, id: null, isNew: true }) }, icon('plus', 16), 'New skill')));
      return;
    }
    const d = sk.isNew ? { published: true } : (state.skillDocs[sk.id] || {});

    const nameIn = h('input', { class: 'input', autocomplete: 'off', spellcheck: 'false', placeholder: 'whatsapp', disabled: !sk.isNew });
    nameIn.value = sk.isNew ? '' : sk.id;
    const tpl = h('select', { class: 'select', 'aria-label': 'Start from a bundled skill' },
      h('option', { value: '' }, 'Start from a bundled skill'),
      ...Object.keys(state.defaults.skills || {}).map((n) => h('option', { value: n }, n)));
    const descIn = h('input', { class: 'input', maxlength: String(MAX_DESC), placeholder: 'One line Ultron reads before opening the skill' });
    descIn.value = d.description || '';
    const bodyIn = h('textarea', { class: 'area', rows: '10', spellcheck: 'false', placeholder: 'Shared instructions for every task. Leave empty if the skill only has tasks.' });
    bodyIn.value = d.body || '';
    const bodyCount = h('span', { class: 'counter' });
    counter(bodyIn, bodyCount, MAX_BODY);
    const pub = h('input', { type: 'checkbox' });
    pub.checked = d.published !== false;
    const minIn = h('input', { class: 'input narrow', type: 'number', min: '0', placeholder: 'Any build' });
    minIn.value = d.minAppVersion != null ? d.minAppVersion : '';
    const tasksBox = h('div', {});

    function addTask(name, t) {
      t = t || {};
      const nm = h('input', { class: 'input t-name', autocomplete: 'off', spellcheck: 'false', placeholder: 'send_message' });
      nm.value = name || '';
      const ds = h('input', { class: 'input t-desc', maxlength: String(MAX_DESC), placeholder: 'When Ultron should use this task' });
      ds.value = t.description || '';
      const bd = h('textarea', { class: 'area t-body', rows: '12', spellcheck: 'false', placeholder: 'Step-by-step instructions for this task' });
      bd.value = t.body || '';
      const bc = h('span', { class: 'counter' });
      counter(bd, bc, MAX_BODY);
      const pb = h('input', { type: 'checkbox', class: 't-pub' });
      pb.checked = t.published !== false;
      const sumName = h('span', { class: 'tname' });
      const sumDesc = h('span', { class: 'tdesc' });
      const sync = () => { sumName.textContent = nm.value.trim() || 'New task'; sumDesc.textContent = ds.value.trim(); };
      nm.addEventListener('input', sync); ds.addEventListener('input', sync); sync();
      const card = h('details', { class: 'task', open: !name },
        h('summary', {}, sumName, sumDesc, pb.checked ? null : h('span', { class: 'badge warn' }, 'Hidden')),
        h('div', { class: 'task-body' },
          field('Task name', nm, 'Lowercase letters, digits, - or _.'),
          field('Description', ds),
          field('Instructions', bd, null, bc),
          h('div', { class: 'row', style: 'justify-content:space-between;align-items:center' },
            h('label', { class: 'check' }, pb, 'Published'),
            h('button', { type: 'button', class: 'btn danger sm', onclick: () => { card.remove(); setDirty(true); } }, icon('trash', 15), 'Remove task'))));
      tasksBox.append(card);
    }
    Object.keys(d.tasks || {}).sort().forEach((n) => addTask(n, d.tasks[n]));

    tpl.addEventListener('change', () => {
      const t = (state.defaults.skills || {})[tpl.value];
      if (!t) return;
      if (!nameIn.value.trim()) nameIn.value = tpl.value;
      descIn.value = t.description || ''; bodyIn.value = t.body || ''; bodyIn.dispatchEvent(new Event('input'));
      pub.checked = true;
      tasksBox.replaceChildren();
      Object.keys(t.tasks || {}).sort().forEach((n) => addTask(n, t.tasks[n]));
      setDirty(true);
    });

    function read() {
      const tasks = {};
      let taskCount = 0;
      tasksBox.querySelectorAll('.task').forEach((card) => {
        taskCount += 1;
        const name = card.querySelector('.t-name').value.trim().toLowerCase();
        tasks[name || '(blank)'] = {
          description: card.querySelector('.t-desc').value.replace(/\s+/g, ' ').trim(),
          body: card.querySelector('.t-body').value.trim(),
          published: card.querySelector('.t-pub').checked,
        };
      });
      return {
        name: nameIn.value.trim().toLowerCase(), description: descIn.value.replace(/\s+/g, ' ').trim(), body: bodyIn.value.trim(),
        published: pub.checked, min: minIn.value.trim(), tasks, taskCount,
      };
    }

    function validate(s) {
      if (!NAME_RE.test(s.name)) return 'Use a short lowercase name with letters, digits, - or _.';
      if (!s.description) return 'Add a description.';
      if (s.description.length > MAX_DESC) return `The description is over ${MAX_DESC} characters.`;
      if (s.body.length > MAX_BODY) return `The instructions are over ${MAX_BODY.toLocaleString()} characters.`;
      const names = Object.keys(s.tasks);
      if (names.length !== s.taskCount) return 'Two tasks share a name.';
      if (!names.length && !s.body) return 'Add instructions or at least one task.';
      for (const n of names) {
        const t = s.tasks[n];
        if (!NAME_RE.test(n)) return `"${n}" is not a valid task name. Use lowercase letters, digits, - or _.`;
        if (!t.description) return `Task ${n} needs a description.`;
        if (t.description.length > MAX_DESC) return `Task ${n} has a description over ${MAX_DESC} characters.`;
        if (!t.body) return `Task ${n} needs instructions.`;
        if (t.body.length > MAX_BODY) return `Task ${n} is over ${MAX_BODY.toLocaleString()} characters.`;
      }
      return null;
    }

    const save = h('button', { class: 'btn primary', 'data-needs-dirty': true, disabled: true, onclick: async () => {
      const s = read();
      const problem = validate(s);
      if (problem) return toast(problem, 'err');
      if (sk.isNew && state.skillDocs[s.name] && !confirm(`"${s.name}" already exists. Replace it?`)) return;
      const data = { description: s.description, body: s.body, published: s.published, tasks: s.tasks, updatedAt: FieldValue.serverTimestamp() };
      if (s.min) data.minAppVersion = Number(s.min);
      save.disabled = true;
      try {
        await db.collection('remote_skills').doc(s.name).set(data);
        state.dirty = false;
        state.skill = { open: true, id: s.name, isNew: false };
        state.skillDocs[s.name] = { ...data, updatedAt: null };
        renderSkillList();
        renderSkillEditor();
        toast(`${s.name} saved. Apps pick it up at their next session.`);
      } catch (e) { save.disabled = false; toast('Save failed. ' + e.message, 'err'); }
    } }, icon('save', 16), 'Save skill');

    const del = sk.isNew ? null : h('button', { class: 'btn danger', onclick: async () => {
      const bundled = state.defaults.skills && state.defaults.skills[sk.id];
      if (!confirm(`Delete "${sk.id}"? ${bundled ? 'The app goes back to its bundled version.' : 'The app stops offering it.'}`)) return;
      try {
        await db.collection('remote_skills').doc(sk.id).delete();
        delete state.skillDocs[sk.id];
        state.dirty = false;
        state.skill = { open: false, id: null, isNew: false };
        renderSkillList();
        renderSkillEditor();
        toast('Skill deleted.');
      } catch (e) { toast('Delete failed. ' + e.message, 'err'); }
    } }, icon('trash', 16), 'Delete');

    box.replaceChildren(
      h('div', { class: 'sec' }, h('h3', {}, sk.isNew ? 'New skill' : sk.id),
        h('div', { class: 'stack' },
          field('Name', nameIn, sk.isNew ? 'Lowercase letters, digits, - or _. A bundled skill with the same name is replaced.' : 'The name cannot change. Delete the skill and create a new one to rename it.'),
          sk.isNew ? tpl : null,
          field('Description', descIn, 'Ultron sees this line for every skill, so say when to use it.'),
          h('div', { class: 'row' },
            h('label', { class: 'check' }, pub, 'Published'),
            field('Minimum app build', minIn)))),
      h('div', { class: 'sec' }, h('h3', {}, 'Instructions'),
        field('Shared instructions', bodyIn, 'Opened together with whichever task Ultron picks.', bodyCount)),
      h('div', { class: 'sec' },
        h('h3', {}, 'Tasks', h('button', { type: 'button', class: 'btn secondary sm', onclick: () => { addTask('', {}); setDirty(true); } }, icon('plus', 15), 'Add task')),
        tasksBox, h('div', { class: 'help' }, 'Tasks stay hidden from Ultron until it opens the skill and asks for one.')),
      h('div', { class: 'editor-foot' }, h('div', { class: 'editor-tools' }, save, del),
        h('span', { class: 'dirty', 'data-dirty-flag': true, hidden: true }, 'Unsaved changes')));

    box.oninput = () => setDirty(true);
    box.onchange = () => setDirty(true);
    setDirty(false);
  }
})();
