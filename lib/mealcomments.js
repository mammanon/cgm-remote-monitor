'use strict';

function storage (env, ctx) {
  var ObjectID = require('mongodb').ObjectID;

  function create (obj, fn) {
    obj.created_at = new Date().toISOString();
    api( ).insert(obj, function (err) {
      if (err) { return fn(err, null); }
      fn(null, obj);
    });
  }

  function list (opts, fn) {
    var query = { };
    if (opts && opts.meal) { query.meal = String(opts.meal); }
    api( ).find(query).sort({ created_at: 1 }).limit(2000).toArray(fn);
  }

  function remove (_id, fn) {
    var query;
    try {
      query = { '_id': new ObjectID(_id) };
    } catch (e) {
      return fn(e, null);
    }
    api( ).remove(query, fn);
  }

  // when a meal is deleted its conversation goes with it
  function removeForMeal (meal, fn) {
    api( ).remove({ meal: String(meal) }, fn);
  }

  function api ( ) {
    return ctx.store.db.collection(env.mealcomments_collection);
  }

  api.create = create;
  api.list = list;
  api.remove = remove;
  api.removeForMeal = removeForMeal;
  api.indexedFields = ['meal', 'created_at'];

  return api;
}

module.exports = storage;
