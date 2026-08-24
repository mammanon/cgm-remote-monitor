/* FCU Time Schedule manager for Niagara stations.
 *
 * Equipment (FCUs) can be assigned to exactly one schedule at a time:
 * adding an FCU to Schedule 1, removing it from Schedule 2, or moving it
 * between schedules is done by drag & drop or the per-card "Move to" menu.
 * State persists in localStorage and can be exported/imported as JSON so
 * the assignment map can be applied to the Niagara station (see README).
 */
(function () {
  'use strict';

  var STORAGE_KEY = 'fcu-niagara-schedules-v1';
  var DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

  function uid() {
    return 'id-' + Math.random().toString(36).slice(2, 10) + Date.now().toString(36);
  }

  function defaultWeek() {
    return DAYS.map(function (day, i) {
      return { day: day, start: '07:00', stop: '18:00', enabled: i < 5 };
    });
  }

  function defaultState() {
    return {
      schedules: [
        { id: uid(), name: 'Schedule 1', week: defaultWeek() },
        { id: uid(), name: 'Schedule 2', week: defaultWeek() }
      ],
      fcus: [] // { id, name, ord, zone, scheduleId|null }
    };
  }

  function loadState() {
    try {
      var raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        var s = JSON.parse(raw);
        if (s && Array.isArray(s.schedules) && Array.isArray(s.fcus)) { return s; }
      }
    } catch (e) { /* fall through to defaults */ }
    return defaultState();
  }

  function saveState() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch (e) { /* storage unavailable; page still works for this visit */ }
  }

  var state = loadState();

  var schedulesEl = document.getElementById('schedules');
  var unassignedList = document.getElementById('unassigned-list');
  var tplSchedule = document.getElementById('tpl-schedule');
  var dlgFcu = document.getElementById('dlg-fcu');
  var formFcu = document.getElementById('form-fcu');
  var dlgSchedule = document.getElementById('dlg-schedule');
  var formSchedule = document.getElementById('form-schedule');
  var editingFcuId = null;
  var editingScheduleId = null;

  function scheduleById(id) {
    return state.schedules.filter(function (s) { return s.id === id; })[0] || null;
  }
  function fcuById(id) {
    return state.fcus.filter(function (f) { return f.id === id; })[0] || null;
  }

  function assign(fcuId, scheduleId) {
    var fcu = fcuById(fcuId);
    if (!fcu) { return; }
    fcu.scheduleId = scheduleId; // null = unassigned
    saveState();
    render();
  }

  function buildFcuCard(fcu) {
    var li = document.createElement('li');
    li.className = 'fcu-card';
    li.draggable = true;
    li.dataset.fcuId = fcu.id;

    var meta = document.createElement('div');
    meta.className = 'meta';
    var name = document.createElement('div');
    name.className = 'name';
    name.textContent = fcu.name;
    meta.appendChild(name);
    if (fcu.zone) {
      var zone = document.createElement('div');
      zone.className = 'zone';
      zone.textContent = fcu.zone;
      meta.appendChild(zone);
    }
    if (fcu.ord) {
      var ord = document.createElement('div');
      ord.className = 'ord';
      ord.textContent = fcu.ord;
      ord.title = fcu.ord;
      meta.appendChild(ord);
    }
    li.appendChild(meta);

    var actions = document.createElement('div');
    actions.className = 'card-actions';

    var moveSel = document.createElement('select');
    moveSel.title = 'Move to schedule';
    var opt0 = document.createElement('option');
    opt0.value = '';
    opt0.textContent = '▸ Move to…';
    moveSel.appendChild(opt0);
    if (fcu.scheduleId) {
      var optU = document.createElement('option');
      optU.value = '__unassigned__';
      optU.textContent = 'Unassigned (remove)';
      moveSel.appendChild(optU);
    }
    state.schedules.forEach(function (s) {
      if (s.id === fcu.scheduleId) { return; }
      var opt = document.createElement('option');
      opt.value = s.id;
      opt.textContent = s.name;
      moveSel.appendChild(opt);
    });
    moveSel.addEventListener('change', function () {
      if (!moveSel.value) { return; }
      assign(fcu.id, moveSel.value === '__unassigned__' ? null : moveSel.value);
    });
    actions.appendChild(moveSel);

    var editBtn = document.createElement('button');
    editBtn.className = 'btn small';
    editBtn.textContent = 'Edit';
    editBtn.addEventListener('click', function () { openFcuDialog(fcu.id); });
    actions.appendChild(editBtn);

    var delBtn = document.createElement('button');
    delBtn.className = 'btn small danger';
    delBtn.textContent = '✕';
    delBtn.title = 'Delete FCU';
    delBtn.addEventListener('click', function () {
      if (confirm('Delete ' + fcu.name + ' entirely?')) {
        state.fcus = state.fcus.filter(function (f) { return f.id !== fcu.id; });
        saveState();
        render();
      }
    });
    actions.appendChild(delBtn);

    li.appendChild(actions);

    li.addEventListener('dragstart', function (ev) {
      ev.dataTransfer.setData('text/plain', fcu.id);
      ev.dataTransfer.effectAllowed = 'move';
    });
    return li;
  }

  function makeDropTarget(listEl, scheduleId) {
    listEl.addEventListener('dragover', function (ev) {
      ev.preventDefault();
      ev.dataTransfer.dropEffect = 'move';
      listEl.classList.add('drag-over');
    });
    listEl.addEventListener('dragleave', function () {
      listEl.classList.remove('drag-over');
    });
    listEl.addEventListener('drop', function (ev) {
      ev.preventDefault();
      listEl.classList.remove('drag-over');
      var id = ev.dataTransfer.getData('text/plain');
      if (id) { assign(id, scheduleId); }
    });
  }

  function buildScheduleCard(schedule) {
    var node = tplSchedule.content.cloneNode(true);
    var article = node.querySelector('.schedule');
    article.dataset.scheduleId = schedule.id;
    node.querySelector('.schedule-name').textContent = schedule.name;

    node.querySelector('.act-rename').addEventListener('click', function () {
      openScheduleDialog(schedule.id);
    });
    node.querySelector('.act-del-schedule').addEventListener('click', function () {
      var assigned = state.fcus.filter(function (f) { return f.scheduleId === schedule.id; });
      var msg = 'Delete "' + schedule.name + '"?';
      if (assigned.length) {
        msg += ' Its ' + assigned.length + ' FCU(s) will become unassigned.';
      }
      if (!confirm(msg)) { return; }
      assigned.forEach(function (f) { f.scheduleId = null; });
      state.schedules = state.schedules.filter(function (s) { return s.id !== schedule.id; });
      saveState();
      render();
    });

    var tbody = node.querySelector('.time-table tbody');
    schedule.week.forEach(function (row) {
      var tr = document.createElement('tr');

      var tdDay = document.createElement('td');
      tdDay.textContent = row.day.slice(0, 3);
      tr.appendChild(tdDay);

      ['start', 'stop'].forEach(function (field) {
        var td = document.createElement('td');
        var input = document.createElement('input');
        input.type = 'time';
        input.value = row[field];
        input.addEventListener('change', function () {
          row[field] = input.value;
          saveState();
        });
        td.appendChild(input);
        tr.appendChild(td);
      });

      var tdEn = document.createElement('td');
      var chk = document.createElement('input');
      chk.type = 'checkbox';
      chk.checked = row.enabled;
      chk.addEventListener('change', function () {
        row.enabled = chk.checked;
        saveState();
      });
      tdEn.appendChild(chk);
      tr.appendChild(tdEn);

      tbody.appendChild(tr);
    });

    var list = node.querySelector('.fcu-list');
    var assigned = state.fcus.filter(function (f) { return f.scheduleId === schedule.id; });
    node.querySelector('.count').textContent = assigned.length;
    assigned.forEach(function (f) { list.appendChild(buildFcuCard(f)); });
    makeDropTarget(list, schedule.id);

    return node;
  }

  function render() {
    schedulesEl.innerHTML = '';
    state.schedules.forEach(function (s) {
      schedulesEl.appendChild(buildScheduleCard(s));
    });
    unassignedList.innerHTML = '';
    state.fcus
      .filter(function (f) { return !f.scheduleId || !scheduleById(f.scheduleId); })
      .forEach(function (f) { unassignedList.appendChild(buildFcuCard(f)); });
  }
  makeDropTarget(unassignedList, null);

  // --- FCU dialog ---
  function openFcuDialog(fcuId) {
    editingFcuId = fcuId || null;
    var fcu = fcuId ? fcuById(fcuId) : null;
    document.getElementById('dlg-fcu-title').textContent = fcu ? 'Edit FCU' : 'Add FCU';
    formFcu.elements.name.value = fcu ? fcu.name : '';
    formFcu.elements.ord.value = fcu ? (fcu.ord || '') : '';
    formFcu.elements.zone.value = fcu ? (fcu.zone || '') : '';
    dlgFcu.showModal();
  }

  dlgFcu.addEventListener('close', function () {
    if (dlgFcu.returnValue !== 'ok') { return; }
    var name = formFcu.elements.name.value.trim();
    if (!name) { return; }
    var ord = formFcu.elements.ord.value.trim();
    var zone = formFcu.elements.zone.value.trim();
    if (editingFcuId) {
      var fcu = fcuById(editingFcuId);
      if (fcu) { fcu.name = name; fcu.ord = ord; fcu.zone = zone; }
    } else {
      state.fcus.push({ id: uid(), name: name, ord: ord, zone: zone, scheduleId: null });
    }
    saveState();
    render();
  });

  // --- Schedule dialog ---
  function openScheduleDialog(scheduleId) {
    editingScheduleId = scheduleId || null;
    var s = scheduleId ? scheduleById(scheduleId) : null;
    document.getElementById('dlg-schedule-title').textContent = s ? 'Rename Schedule' : 'New Schedule';
    formSchedule.elements.name.value = s ? s.name : '';
    dlgSchedule.showModal();
  }

  dlgSchedule.addEventListener('close', function () {
    if (dlgSchedule.returnValue !== 'ok') { return; }
    var name = formSchedule.elements.name.value.trim();
    if (!name) { return; }
    if (editingScheduleId) {
      var s = scheduleById(editingScheduleId);
      if (s) { s.name = name; }
    } else {
      state.schedules.push({ id: uid(), name: name, week: defaultWeek() });
    }
    saveState();
    render();
  });

  // --- Toolbar ---
  document.getElementById('btn-add-fcu').addEventListener('click', function () { openFcuDialog(null); });
  document.getElementById('btn-add-schedule').addEventListener('click', function () { openScheduleDialog(null); });

  document.getElementById('btn-export').addEventListener('click', function () {
    var blob = new Blob([JSON.stringify(state, null, 2)], { type: 'application/json' });
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'fcu-schedules.json';
    a.click();
    URL.revokeObjectURL(a.href);
  });

  document.getElementById('file-import').addEventListener('change', function (ev) {
    var file = ev.target.files[0];
    if (!file) { return; }
    var reader = new FileReader();
    reader.onload = function () {
      try {
        var imported = JSON.parse(reader.result);
        if (!imported || !Array.isArray(imported.schedules) || !Array.isArray(imported.fcus)) {
          throw new Error('bad shape');
        }
        state = imported;
        saveState();
        render();
      } catch (e) {
        alert('Invalid file: expected a JSON export from this page.');
      }
      ev.target.value = '';
    };
    reader.readAsText(file);
  });

  render();
})();
