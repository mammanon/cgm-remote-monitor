'use strict';

var fs = require('fs');
var path = require('path');

var HOUR = 60 * 60 * 1000;
var BOOT_DELAY = 10 * 1000;
var KEEP_FILES = 30;

var EXTENSIONS = {
  'image/jpeg': 'jpg'
  , 'image/png': 'png'
  , 'image/gif': 'gif'
  , 'image/webp': 'webp'
};

/*
 * Writes one backup a day to BACKUP_DIR, so nothing is lost when the site is
 * updated or the database is replaced:
 *
 *   BACKUP_DIR/meals-YYYY-MM-DD.json   every treatment, the last 30 days kept
 *   BACKUP_DIR/photos/<id>.<ext>       every meal photo, copied once
 *
 * Photos are copied only the first time they are seen, so a daily backup stays
 * small however many photos have piled up.
 */
function init (env, ctx) {
  var dir = env.backup_dir;
  if (!dir) {
    console.info('Automatic backup is off (BACKUP_DIR is not set)');
    return null;
  }

  var photoDir = path.join(dir, 'photos');

  function stampFor (date) {
    return date.toISOString().slice(0, 10);
  }

  function mealsFileFor (date) {
    return path.join(dir, 'meals-' + stampFor(date) + '.json');
  }

  function imageIdsIn (treatments) {
    var ids = [];
    treatments.forEach(function (treatment) {
      var list = Array.isArray(treatment.mealImageIds) ? treatment.mealImageIds
        : (treatment.mealImageId ? [treatment.mealImageId] : []);
      list.forEach(function (id) {
        var key = String(id);
        if (key && ids.indexOf(key) < 0) { ids.push(key); }
      });
    });
    return ids;
  }

  function alreadySaved ( ) {
    var saved = {};
    fs.readdirSync(photoDir).forEach(function (name) {
      saved[name.replace(/\.[^.]*$/, '')] = true;
    });
    return saved;
  }

  function copyPhotos (ids, done) {
    var saved = alreadySaved( );
    var pending = ids.filter(function (id) { return !saved[id]; });
    var copied = 0;

    function next ( ) {
      if (!pending.length) { return done(copied); }
      var id = pending.shift( );
      ctx.mealimages.fetch(id, function (err, doc) {
        if (!err && doc && doc.data) {
          try {
            var ext = EXTENSIONS[doc.type] || 'bin';
            var buffer = Buffer.from ? Buffer.from(doc.data, 'base64') : new Buffer(doc.data, 'base64');
            fs.writeFileSync(path.join(photoDir, id + '.' + ext), buffer);
            copied++;
          } catch (e) {
            console.error('Backup could not save photo', id, e.message);
          }
        }
        next( );
      });
    }

    next( );
  }

  function prune ( ) {
    var files = fs.readdirSync(dir)
      .filter(function (name) { return (/^meals-\d{4}-\d{2}-\d{2}\.json$/).test(name); })
      .sort();
    files.slice(0, Math.max(0, files.length - KEEP_FILES)).forEach(function (name) {
      try {
        fs.unlinkSync(path.join(dir, name));
      } catch (e) {
        console.error('Backup could not remove old file', name, e.message);
      }
    });
  }

  function run ( ) {
    ctx.treatments.list({ count: 100000, find: { } }, function (err, treatments) {
      if (err || !treatments) {
        console.error('Backup could not read the treatments', err);
        return;
      }
      try {
        fs.writeFileSync(mealsFileFor(new Date()), JSON.stringify({
          app: 'carb-tracker'
          , version: 1
          , savedAt: new Date().toISOString()
          , treatments: treatments
        }));
      } catch (e) {
        console.error('Backup could not write the meals file', e.message);
        return;
      }
      copyPhotos(imageIdsIn(treatments), function (copied) {
        prune( );
        console.info('Backup written:', treatments.length, 'treatments,', copied, 'new photos');
      });
    });
  }

  function maybeRun ( ) {
    try {
      if (fs.existsSync(mealsFileFor(new Date()))) { return; }
      run( );
    } catch (e) {
      console.error('Backup failed', e.message);
    }
  }

  try {
    [dir, photoDir].forEach(function (target) {
      if (!fs.existsSync(target)) { fs.mkdirSync(target, { recursive: true }); }
    });
  } catch (e) {
    console.error('Backup directory is not usable:', dir, e.message);
    return null;
  }

  console.info('Automatic daily backup to', dir);
  setTimeout(maybeRun, BOOT_DELAY);
  setInterval(maybeRun, HOUR);

  return { run: run, maybeRun: maybeRun, dir: dir };
}

module.exports = init;
