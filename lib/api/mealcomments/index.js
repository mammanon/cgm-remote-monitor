'use strict';

var consts = require('../../constants');

var MAX_TEXT = 2000;

/*
 * A conversation on each meal, between the patient and the doctor.
 *
 * Reading is open, like the rest of the tracker. Writing needs one of two
 * keys: the usual api-secret marks the message as the patient's, while a
 * separate DOCTOR_CODE lets the doctor comment without being handed the
 * secret that would also let them change the meals.
 */
function configure (app, wares, ctx, env) {
  var express = require('express'),
    api = express.Router( );

  api.use(wares.sendJSONStatus);
  api.use(wares.bodyParser.json({limit: 1048576}));

  function authorOf (req) {
    var given = req.header('api-secret');
    if (env.api_secret && env.api_secret.length > 12 && given === env.api_secret) {
      return 'patient';
    }
    var code = req.header('doctor-code');
    if (env.doctor_code && code && code === env.doctor_code) {
      return 'doctor';
    }
    return null;
  }

  api.get('/mealcomments', function (req, res) {
    ctx.mealcomments.list({ meal: req.query.meal }, function (err, results) {
      if (err) {
        return res.sendJSONStatus(res, consts.HTTP_INTERNAL_ERROR, 'Mongo Error', err);
      }
      res.json(results || [ ]);
    });
  });

  // lets the doctor's phone check the code before showing a writing box
  api.get('/verifydoctor', function (req, res) {
    var code = req.header('doctor-code');
    var ok = !!(env.doctor_code && code && code === env.doctor_code);
    res.sendJSONStatus(res, consts.HTTP_OK, ok ? 'OK' : 'UNAUTHORIZED');
  });

  function config_authed (app, api, wares, ctx) {

    api.post('/mealcomments', function (req, res) {
      var author = authorOf(req);
      if (!author) {
        return res.sendJSONStatus(res, consts.HTTP_UNAUTHORIZED, 'Unauthorized'
          , 'api-secret or doctor-code Request Header is incorrect or missing.');
      }
      var body = req.body || { };
      var text = typeof body.text === 'string' ? body.text.trim() : '';
      if (!text) {
        return res.sendJSONStatus(res, consts.HTTP_VALIDATION_ERROR, 'text is required');
      }
      var comment = {
        meal: (typeof body.meal === 'string' && body.meal) ? body.meal : 'general'
        , author: author
        , text: text.slice(0, MAX_TEXT)
      };
      ctx.mealcomments.create(comment, function (err, created) {
        if (err) {
          console.log('Error adding meal comment');
          res.sendJSONStatus(res, consts.HTTP_INTERNAL_ERROR, 'Mongo Error', err);
        } else {
          res.json(created);
        }
      });
    });

    api.delete('/mealcomments/:_id', wares.verifyAuthorization, function (req, res) {
      ctx.mealcomments.remove(req.params._id, function ( ) {
        res.json({ });
      });
    });
  }

  if (app.enabled('api') && app.enabled('careportal')) {
    config_authed(app, api, wares, ctx);
  }

  return api;
}

module.exports = configure;
