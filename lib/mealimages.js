'use strict';

function storage (env, ctx) {
  var ObjectID = require('mongodb').ObjectID;

  function create (obj, fn) {
    obj.created_at = new Date().toISOString();
    api( ).insert(obj, function (err) {
      if (err) {
        fn(err, null);
      } else {
        fn(null, { _id: obj._id, created_at: obj.created_at });
      }
    });
  }

  function fetch (_id, fn) {
    var query;
    try {
      query = { '_id': new ObjectID(_id) };
    } catch (e) {
      return fn(e, null);
    }
    api( ).findOne(query, fn);
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

  function api ( ) {
    return ctx.store.db.collection(env.mealimages_collection);
  }

  api.create = create;
  api.fetch = fetch;
  api.remove = remove;

  return api;
}

module.exports = storage;
