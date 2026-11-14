# Local Telegram bridge

Node 20+, no npm dependencies. This adapts the AI Studio Telegram API for the native Kotlin client and keeps the bot token outside the APK.

1. Copy `.env.example` to `.env` and set `TELEGRAM_BOT_TOKEN` locally. `.env` is ignored by Git.
2. Run `node telegram-bridge/bridge.mjs` from the repository root.
3. Connect the phone with USB debugging; run `adb reverse tcp:3001 tcp:3001`.
4. Set the app’s Telegram bridge URL to `http://127.0.0.1:3001` in Settings, or use the debug `bridge_url` launch extra shown in the root README.
5. Open the bot chat from Settings. Both people must press Start. The bot responds with each requester’s numeric User ID; store those IDs in the profile/friend forms.
6. Tap a person’s butterfly and confirm Send. A Telegram receipt is required for success. Their reply or the bot message’s **Send a butterfly back** button becomes an incoming garden butterfly. The native app refreshes while foregrounded, every 30 seconds.

Contact registration is local and sends nothing. No real messages are sent by the automated tests. The native journal is never submitted to this bridge. There is no Gemini endpoint yet.

The local single-owner bridge binds only to `127.0.0.1`, rejects browser-origin requests, persists Telegram state in ignored `.data/state.json`, and never returns/logs the bot credential. It deliberately does not overwrite an existing webhook or compete with another bot poller: a Telegram conflict stops this bridge’s incoming polling, while explicitly requested outgoing sends remain available. Stop the other bot instance, then restart this bridge to receive updates here. Bot users must have started the bot and must not have blocked it.

This is a USB/hackathon development service. Before deploying, add authentication and isolated storage per signed-in user, then serve HTTPS; the Android release build refuses cleartext HTTP.

```sh
node --test telegram-bridge/bridge.test.mjs
```

API: `GET /api/telegram/state`, `POST /profile`, `POST /friends`, `POST /send-butterfly`, `DELETE /butterflies/:id` (all paths below `/api/telegram`). Configuration: `TELEGRAM_BOT_TOKEN`, optional `PORT` (3001). There are no credentials in `.env.example`.

Protocol references: [Telegram Bot API](https://core.telegram.org/bots/api) and [bot features](https://core.telegram.org/bots/features).
