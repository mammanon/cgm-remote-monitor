# Carb Tracker — Meal log for Type 1 Diabetes

The Carb Tracker is a simple page inside this Nightscout website, made for
logging meals the way many doctors ask for:

| Column | What it is |
|---|---|
| **Time** | The time of the meal. It is filled **automatically** with the current time when you add a meal (you can still change it if you log a meal later). |
| **Sugar before meal** | Blood sugar reading before eating — entered by hand. |
| **Sugar after meal** | Blood sugar reading after eating (usually ~2 hours later). You can add it later with the small **“+ after”** button on the meal. |
| **Carbs (grams)** | The carbohydrates of the meal, counted by the patient. |
| **Carb factor** | The carb factor number, calculated and entered **by the patient** (for example `9`). Nothing is calculated automatically — every number is entered manually. |
| **Insulin dose** | (optional) The insulin actually taken. |
| **Notes** | (optional) Anything else. |
| **Photo** | (optional) A picture of the meal, taken with the phone camera or chosen from the photo library. |

The page is in **English and Arabic** (button at the top switches the language).

## Where is it?

Open your Nightscout website and go to:

```
https://YOUR-SITE/carbtracker/
```

There is also a **Carb Tracker** link in the site menu (the drawer on the main page).

## Using it on iPhone (like an app)

1. Open `https://YOUR-SITE/carbtracker/` in **Safari** on the iPhone.
2. Tap the **Share** button (square with an arrow).
3. Tap **Add to Home Screen**.
4. Now there is a **Carb Tracker** icon on the home screen — it opens full
   screen like a normal app.

## Unlocking (so the patient can add meals)

The first time, tap the **lock button** (🔒) at the top and enter the
**API secret** — this is the `API_SECRET` password that was set when the
website was created. The phone remembers it, so this is needed only once
per device.

Without the secret the page is **view only** — perfect for the doctor.

## Sharing with the doctor

Two easy ways:

1. **Send the doctor the website link** (`https://YOUR-SITE/carbtracker/`).
   The doctor can see the whole table, the statistics, and the meal photos —
   but cannot change anything, because changing needs the API secret.
2. **Download CSV** — the button at the bottom of the page downloads the
   table as a file that opens in Excel. You can send it by email or
   WhatsApp before the appointment.

> **Privacy note:** in this version of Nightscout, anyone who has the
> website address can *view* the data. Only share the link with people you
> trust (like the doctor), and do not post it publicly.

## How to run it (hosting)

The easiest way is **Docker** — it works on any small cloud server
(a ~$5/month VPS from Hetzner, DigitalOcean, Contabo…) or a home computer
that stays on. Install Docker, then:

```bash
git clone https://github.com/mammanon/cgm-remote-monitor
cd cgm-remote-monitor
git checkout claude/diabetes-carb-tracker-ws48kl   # until this branch is merged
cp .env.example .env
nano .env                  # set your own API_SECRET (min 12 characters)
docker compose up -d --build
```

Then open `http://YOUR-SERVER-IP:1337/carbtracker/` on the phone.

**With a domain name** (recommended, gives you a proper `https://` address):
point a domain at the server, put it in `.env` as `DOMAIN=...`, and run:

```bash
docker compose --profile https up -d --build
```

Then the site is at `https://your-domain/carbtracker/` with an automatic
free certificate.

### Updating to a newer version

On the server:

```bash
cd cgm-remote-monitor
./update.sh
```

> ⚠️ **Restarting is not updating.** `docker compose restart`, or rebooting
> the server, starts the *same* code again — nothing new appears. Only
> `./update.sh` (which does `git pull` and rebuilds) brings in changes.
> Meals, photos and backups live in their own volumes and are never touched
> by an update.

> ⚠️ **Database version matters:** this Nightscout version is from 2015 and
> can only talk to **MongoDB 4.0 or older**. The `docker-compose.yml` uses
> MongoDB 4.0 on purpose. It can NOT use MongoDB Atlas or MongoDB 6+.

**Without Docker:** install Node.js and a local MongoDB ≤ 4.0, then
`npm install`, set the environment variables below, and `node server.js`.

This setup was verified end-to-end: the server boots on Node 22 with
MongoDB 4.0 and the whole tracker (add / edit / delete / photos / auth /
CSV) passes an automated browser test suite against it.

## Server settings needed

The tracker uses the normal Nightscout server. Make sure these environment
variables are set on your hosting:

| Variable | Value | Why |
|---|---|---|
| `API_SECRET` | a password, **at least 12 characters** | Needed to add/edit meals. |
| `ENABLE` | must contain `careportal` (example: `ENABLE=careportal`) | Turns on the part of the API that saves treatments and photos. |
| `TREATMENTS_AUTH` | `true` (recommended) | Makes sure only someone with the secret can add entries. |
| `MONGO_CONNECTION` | your MongoDB connection string | Standard Nightscout database. |
| `DISPLAY_UNITS` | `mg/dl` (default) or `mmol` | The unit shown next to the sugar readings. |

Meal photos are stored in the database in their own collection
(`mealimages` by default, can be changed with `MONGO_MEALIMAGES_COLLECTION`).
Photos are automatically resized on the phone before upload so they stay
small (~100–300 KB each).

## Comments with the doctor

Every meal has a conversation on it, and there is a general thread behind
the 💬 button at the top. Messages look like a chat: the doctor's on one
side, the patient's on the other.

The doctor does **not** get the API secret. Instead set `DOCTOR_CODE` (8+
characters) and give the doctor that. With it they can **only add
comments** — they cannot add, change or delete a meal, and they cannot
delete comments. The patient, who has the API secret, can do all of it.

On the doctor's phone: open the link, tap 💬 on a meal, tap **"I am the
doctor"**, enter the code once — the phone remembers it.

Leave `DOCTOR_CODE` empty and the writing box simply never appears for
anyone but the patient.

The patient's name shown on the messages is set in the page's translation
table (`patientName`), currently **Amani / أماني**.

## Automatic daily backup

The server writes **one backup a day** by itself, into a Docker volume that
updates never touch:

```
/data/backups/meals-YYYY-MM-DD.json    every meal, the last 30 days kept
/data/backups/photos/<id>.jpg          every meal photo, copied once
```

Photos are copied only the first time they are seen, so the daily backup
stays small no matter how many photos have piled up.

To copy the backups onto your own computer:

```bash
docker compose cp nightscout:/data/backups ./carb-tracker-backups
```

Set `BACKUP_DIR` to change the location, or leave it empty to switch the
automatic backup off.

## Suggested carb factor

The top of the page shows a **suggested carb factor** worked out from the
last four days. For every meal that has a before reading, an after reading
and carbs, it asks: how much insulin would have landed the sugar on target?

```
insulin that was missing = (sugar after the meal − target) ÷ correction step
factor that would have worked = carbs ÷ (insulin given + insulin missing)
```

The suggestion is the middle value (median) of those, rounded to the
nearest half. The **target** comes from the site settings (`bgTargetTop`,
180 mg/dL by default) and the **correction step** is 1 unit per 40 mg/dL,
which can be changed with the ✎ button next to the explanation.

> This is a suggestion calculated from the patient's own readings, shown so
> it can be discussed with the doctor. It is not a prescription, and the app
> never changes the factor by itself.

## Where is the data stored?

Meal entries are stored in the standard Nightscout **treatments**
collection with `eventType: "Meal Log"`, so:

- Meals with carbs also appear on the main Nightscout graph.
- If you upgrade Nightscout later, the meal data stays in the database.

## API (for the curious)

- `GET /api/v1/treatments?find[eventType]=Meal Log` — list meals (public).
- `POST | PUT /api/v1/treatments` — add/edit a meal (needs `api-secret` header).
- `DELETE /api/v1/treatments/<id>` — delete a meal (needs `api-secret` header).
- `GET /api/v1/mealimages/<id>` — the meal photo as a normal image (public).
- `POST /api/v1/mealimages` — upload a photo as `{ "image": "data:image/jpeg;base64,..." }` (needs `api-secret` header).
- `DELETE /api/v1/mealimages/<id>` — delete a photo (needs `api-secret` header).
