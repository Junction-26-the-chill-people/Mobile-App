import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';

const directory = path.dirname(fileURLToPath(import.meta.url));
export function readEnv(filename = path.join(directory, '.env')) {
  if (!fs.existsSync(filename)) return {};
  return Object.fromEntries(fs.readFileSync(filename, 'utf8').split(/\r?\n/).filter(line => /^[A-Z_]+=/.test(line)).map(line => {
    const at = line.indexOf('='); return [line.slice(0, at), line.slice(at + 1).trim().replace(/^(['"])(.*)\1$/, '$2')];
  }));
}

/** Local single-owner hackathon bridge, matching the AI Studio API. No diary or health data is accepted. */
export function createBridge({ token = '', stateFile, telegramCall } = {}) {
  let state = { userProfile: { name: '', telegramHandle: '', telegramUserId: '' }, friends: [], butterflies: [], messages: [], offset: 0 };
  if (stateFile && fs.existsSync(stateFile)) {
    try { const saved = JSON.parse(fs.readFileSync(stateFile, 'utf8')); if (saved.version === 1) state = { ...state, ...saved }; } catch { /* Retain a readable clean state if a local file was interrupted. */ }
  }
  const botInfo = { enabled: Boolean(token), botUsername: '', error: '' };
  let stopped = false;
  let polling;
  const persist = () => {
    if (!stateFile) return;
    fs.mkdirSync(path.dirname(stateFile), { recursive: true });
    fs.writeFileSync(stateFile + '.tmp', JSON.stringify({ ...state, version: 1 }), { mode: 0o600 });
    fs.renameSync(stateFile + '.tmp', stateFile);
  };
  const telegram = telegramCall || (async (method, body) => {
    if (!token) throw new Error('No Telegram bot token is configured.');
    let response;
    try {
      response = await fetch(`https://api.telegram.org/bot${token}/${method}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(method === 'getUpdates' ? 30_000 : 12_000) });
    } catch { throw new Error('Telegram could not be reached. Check the bridge internet connection.'); }
    const json = await response.json();
    if (!json.ok) throw new Error(String(json.description || 'Telegram rejected the request.').replaceAll(token, '[redacted]'));
    return json.result;
  });
  const snapshot = () => ({ userProfile: state.userProfile, friends: state.friends, butterflies: state.butterflies, botInfo });
  const clean = (value, max = 200) => typeof value === 'string' ? value.trim().slice(0, max) : '';
  const numeric = value => /^-?\d+$/.test(clean(value)) ? clean(value) : '';
  const butterflyFrom = (friend, message) => {
    state.butterflies.push({ id: randomUUID(), friendId: friend.id, friendName: friend.name, friendTelegram: friend.telegram, message, receivedAt: new Date().toISOString() });
    state.butterflies = state.butterflies.slice(-100); persist();
  };
  async function handleUpdate(update) {
    const callback = update.callback_query;
    const message = update.message;
    const senderId = String(callback?.from?.id || message?.from?.id || '');
    if (message?.chat?.type === 'private' && /^\/start(?:\s|$)/.test(message.text || '')) {
      await telegram('sendMessage', { chat_id: message.chat.id, text: `Hello from Sola 🌱 Your Telegram User ID is ${senderId}. Add this number to your Sola profile, or share it with the person adding you as a friend. You can reply here to leave a little butterfly in their garden.` });
      return;
    }
    const friend = state.friends.find(f => f.telegramChatId === senderId);
    if (!friend || !state.userProfile.telegramUserId) return;
    if (callback?.data === `butterfly:${friend.id}`) {
      butterflyFrom(friend, `${friend.name} sent a little butterfly back — thinking of you, no questions asked.`);
      // This is a response to a person's explicit Telegram button press, not an unsolicited message.
      await telegram('answerCallbackQuery', { callback_query_id: callback.id, text: 'Your butterfly is in their garden.' });
    } else if (message?.chat?.type === 'private' && typeof message.text === 'string' && !message.text.startsWith('/')) {
      butterflyFrom(friend, clean(message.text, 1200));
    }
  }
  async function startPolling() {
    if (!token || polling) return;
    try { const me = await telegram('getMe', {}); botInfo.botUsername = me.username || ''; botInfo.error = ''; } catch (error) { botInfo.enabled = false; botInfo.error = error.message; return; }
    polling = (async () => {
      while (!stopped) {
        try {
          const updates = await telegram('getUpdates', { offset: state.offset, timeout: 20, allowed_updates: ['message', 'callback_query'] });
          for (const update of updates) { if (stopped) break; await handleUpdate(update); state.offset = update.update_id + 1; persist(); }
          botInfo.error = '';
        } catch (error) {
          botInfo.error = error.message;
          // Never fight another running bot instance for the same Telegram update stream.
          if (/Conflict|webhook/i.test(error.message)) break;
          await new Promise(resolve => { const retry = setTimeout(resolve, 5000); retry.unref(); });
        }
      }
    })();
  }
  const server = http.createServer(async (req, res) => {
    const reply = (code, body) => { res.writeHead(code, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' }); res.end(JSON.stringify(body)); };
    if (req.headers.origin) return reply(403, { error: 'This local bridge accepts native clients only.' });
    try {
      const pathname = new URL(req.url, 'http://localhost').pathname;
      let body = {};
      if (['POST', 'PATCH'].includes(req.method)) {
        let raw = '';
        for await (const chunk of req) { raw += chunk; if (raw.length > 16_384) return reply(413, { error: 'Request too large.' }); }
        try { body = JSON.parse(raw || '{}'); } catch { return reply(400, { error: 'Invalid JSON.' }); }
      }
      if (req.method === 'GET' && pathname === '/api/telegram/state') return reply(200, snapshot());
      if (req.method === 'POST' && pathname === '/api/telegram/profile') {
        state.userProfile = { name: clean(body.name), telegramHandle: clean(body.telegramHandle), telegramUserId: numeric(body.telegramUserId) };
        persist(); return reply(200, snapshot());
      }
      if (req.method === 'POST' && pathname === '/api/telegram/friends') {
        const name = clean(body.name), id = numeric(body.telegramUserId || body.telegramChatId);
        if (!name || !id) return reply(400, { error: 'A name and numeric Telegram User ID are required.' });
        const existing = state.friends.find(f => f.telegramChatId === id);
        if (existing) Object.assign(existing, { name, telegram: clean(body.telegram) });
        else state.friends.push({ id: randomUUID(), name, telegram: clean(body.telegram), telegramUserId: id, telegramChatId: id });
        persist(); return reply(200, snapshot());
      }
      if (req.method === 'POST' && pathname === '/api/telegram/send-butterfly') {
        const friend = state.friends.find(f => f.id === body.friendId);
        if (!friend) return reply(404, { error: 'Friend not found.' });
        const text = `${state.userProfile.name || 'Your friend'} sent you a butterfly 🦋 — I’d love to have some company, no questions asked.`;
        // No fake relay success: a non-200 result remains an error in the native app.
        await telegram('sendMessage', { chat_id: friend.telegramChatId, text, reply_markup: { inline_keyboard: [[{ text: 'Send a butterfly back 🦋', callback_data: `butterfly:${friend.id}` }]] } });
        const message = { id: randomUUID(), friendId: friend.id, text, timestamp: new Date().toISOString(), deliveredVia: 'telegram_bot' };
        state.messages = [...state.messages, message].slice(-100); persist(); return reply(200, { message, ...snapshot() });
      }
      const butterfly = pathname.match(/^\/api\/telegram\/butterflies\/([^/]+)$/);
      if (req.method === 'DELETE' && butterfly) { state.butterflies = state.butterflies.filter(b => b.id !== decodeURIComponent(butterfly[1])); persist(); return reply(200, snapshot()); }
      return reply(404, { error: 'Not found.' });
    } catch (error) { return reply(502, { error: String(error.message || 'Bridge request failed.').replaceAll(token || '\0', '[redacted]') }); }
  });
  return { server, startPolling, handleUpdate, close: () => { stopped = true; server.closeAllConnections(); return new Promise(resolve => server.close(resolve)); } };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const env = { ...readEnv(), ...process.env };
  const bridge = createBridge({ token: env.TELEGRAM_BOT_TOKEN || '', stateFile: path.join(directory, '.data', 'state.json') });
  const port = Number(env.PORT || 3001);
  bridge.server.listen(port, '127.0.0.1', () => {
    console.log(`Sola Telegram bridge: http://127.0.0.1:${port}`);
    bridge.startPolling();
  });
  process.on('SIGINT', async () => { await bridge.close(); process.exit(0); });
  process.on('SIGTERM', async () => { await bridge.close(); process.exit(0); });
}
