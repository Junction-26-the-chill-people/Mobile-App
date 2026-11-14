import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createBridge } from './bridge.mjs';

async function fixture(t, telegramCall = async () => ({ message_id: 1 })) {
  const calls = [];
  const bridge = createBridge({ token: 'test-token', telegramCall: async (method, body) => { calls.push({ method, body }); return telegramCall(method, body); } });
  await new Promise(resolve => bridge.server.listen(0, '127.0.0.1', resolve));
  t.after(() => bridge.close());
  const base = `http://127.0.0.1:${bridge.server.address().port}`;
  const request = async (endpoint, body, method = 'POST') => {
    const response = await fetch(base + '/api/telegram/' + endpoint, { method, headers: body ? { 'content-type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined });
    return { status: response.status, json: await response.json() };
  };
  return { bridge, calls, request, base };
}

test('registering a contact does not send a message; Send returns an actual delivery receipt', async t => {
  const { request, calls } = await fixture(t);
  await request('profile', { name: 'Owner', telegramUserId: '123' });
  const registration = await request('friends', { name: 'Friend', telegramUserId: '456' });
  assert.equal(calls.length, 0);
  const delivery = await request('send-butterfly', { friendId: registration.json.friends[0].id });
  assert.equal(delivery.status, 200);
  assert.equal(delivery.json.message.deliveredVia, 'telegram_bot');
  assert.equal(calls[0].method, 'sendMessage');
  assert.equal(calls[0].body.chat_id, '456');
  assert.match(calls[0].body.text, /Owner/);
  assert.ok(!JSON.stringify(delivery).includes('test-token'));
});
test('Telegram failure stays a failure, with no fake relay delivery', async t => {
  const { request } = await fixture(t, async () => { throw new Error('Friend must start the bot first.'); });
  const friend = (await request('friends', { name: 'Friend', telegramUserId: '456' })).json.friends[0];
  const delivery = await request('send-butterfly', { friendId: friend.id });
  assert.equal(delivery.status, 502);
  assert.match(delivery.json.error, /start the bot/);
  assert.equal(delivery.json.message, undefined);
});
test('duplicate numeric contact IDs update the contact rather than creating duplicate recipients', async t => {
  const { request } = await fixture(t);
  await request('friends', { name: 'Old', telegramUserId: '456' });
  const result = await request('friends', { name: 'New', telegramUserId: '456' });
  assert.equal(result.json.friends.length, 1);
  assert.equal(result.json.friends[0].name, 'New');
  assert.equal((await request('friends', { name: 'Invalid', telegramUserId: '@handle' })).status, 400);
});
test('known friend replies become a garden butterfly; strangers and bot commands do not', async t => {
  const { bridge, request } = await fixture(t);
  await request('profile', { name: 'Owner', telegramUserId: '123' });
  await request('friends', { name: 'Friend', telegramUserId: '456' });
  for (const [id, text] of [['999', 'unknown'], ['456', '/start'], ['456', 'Thinking of you.']]) {
    await bridge.handleUpdate({ message: { from: { id }, chat: { type: 'private' }, text } });
  }
  const state = (await request('state', undefined, 'GET')).json;
  assert.equal(state.butterflies.length, 1);
  assert.equal(state.butterflies[0].friendName, 'Friend');
  assert.equal(state.butterflies[0].message, 'Thinking of you.');
  await request('butterflies/' + state.butterflies[0].id, undefined, 'DELETE');
  assert.equal((await request('state', undefined, 'GET')).json.butterflies.length, 0);
});
test('local bridge rejects browser-origin requests and never exposes the credential', async t => {
  const { base, request } = await fixture(t);
  const rejected = await fetch(base + '/api/telegram/state', { headers: { Origin: 'https://unrelated.example' } });
  assert.equal(rejected.status, 403);
  const result = await request('state', undefined, 'GET');
  assert.ok(!JSON.stringify(result).includes('test-token'));
  assert.equal(result.json.botInfo.enabled, true);
});
test('Start replies only to the requesting chat with its own numeric ID', async t => {
  const { bridge, calls } = await fixture(t);
  await bridge.handleUpdate({ message: { from: { id: 456 }, chat: { id: 456, type: 'private' }, text: '/start' } });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].method, 'sendMessage');
  assert.equal(calls[0].body.chat_id, 456);
  assert.match(calls[0].body.text, /User ID is 456/);
});
