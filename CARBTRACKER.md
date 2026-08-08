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
