# FCU Time Schedules (Niagara)

A self-contained page for managing which fan coil units (FCUs) run on which
time schedule, matching the schedule setup in a Tridium Niagara station.

Open it at `/fcu-schedules/` on the running server, or open
`static/fcu-schedules/index.html` directly in a browser — it has no server
dependency.

## What it does

- **Schedule 1 and Schedule 2** exist by default; more schedules can be added
  ("+ New Schedule"), renamed, or deleted.
- **Add equipment to a schedule**: add an FCU ("+ Add FCU", with its Niagara
  ORD/path and zone), then drag its card onto a schedule — or pick the target
  from the card's "Move to…" menu.
- **Delete from a schedule**: choose "Unassigned (remove)" in the card's
  "Move to…" menu, or drag it back to the Unassigned panel. An FCU belongs to
  at most one schedule, so moving it to another schedule removes it from its
  current one automatically.
- **Weekly times**: each schedule has a per-day start/stop time and an
  enabled checkbox, mirroring a Niagara `BooleanSchedule` weekly sheet.
- **Persistence**: state is saved in the browser (localStorage) and can be
  exported/imported as JSON.

## Applying it in Niagara

The page is the planning/record surface; the station itself is configured in
Workbench:

1. In the station, create one `BooleanSchedule` (schedule palette) per
   schedule defined on this page, and copy the weekly start/stop times.
2. For each FCU listed under a schedule, link that schedule's `out` to the
   FCU's enable/occupancy point (the ORD recorded on the FCU card tells you
   which point).
3. When an FCU is moved between schedules on this page, move its link to the
   corresponding `BooleanSchedule` in the station.

The exported JSON (`fcu-schedules.json`) is a machine-readable map of
schedule → FCU ORDs, suitable for driving a Program/robot object or an
obix-based sync if station-side automation is wanted later.
