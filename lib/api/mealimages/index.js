'use strict';

var consts = require('../../constants');

var ALLOWED_TYPES = {
  'image/jpeg': true
  , 'image/png': true
  , 'image/gif': true
  , 'image/webp': true
};

// a base64 data URL, like the ones produced by canvas.toDataURL()
var DATA_URL_RE = /^data:(image\/[a-z+.-]+);base64,([A-Za-z0-9+\/]+={0,2})$/;

function configure (app, wares, ctx) {
  var express = require('express'),
    api = express.Router( );

  api.use(wares.sendJSONStatus);
  // images arrive as base64 data URLs inside JSON, so allow larger bodies
  api.use(wares.bodyParser.json({limit: 1048576 * 10}));

  // Serve a single image as a real image response, so it can be used
  // directly in an <img src="/api/v1/mealimages/<id>"> tag.
  api.get('/mealimages/:_id', function (req, res) {
    ctx.mealimages.fetch(req.params._id, function (err, doc) {
      if (err || !doc || !ALLOWED_TYPES[doc.type] || !doc.data) {
        return res.sendJSONStatus(res, 404, 'Meal image not found');
      }
      var image = Buffer.from ? Buffer.from(doc.data, 'base64') : new Buffer(doc.data, 'base64');
      res.setHeader('Content-Type', doc.type);
      res.setHeader('X-Content-Type-Options', 'nosniff');
      res.setHeader('Cache-Control', 'private, max-age=604800');
      res.send(image);
    });
  });

  function config_authed (app, api, wares, ctx) {

    api.post('/mealimages/', wares.verifyAuthorization, function (req, res) {
      var body = req.body || { };
      var match = DATA_URL_RE.exec(typeof body.image === 'string' ? body.image : '');
      if (!match || !ALLOWED_TYPES[match[1]]) {
        return res.sendJSONStatus(res, consts.HTTP_VALIDATION_ERROR
          , 'image must be a base64 data URL of type jpeg, png, gif or webp');
      }
      var image = {
        type: match[1]
        , data: match[2]
        , enteredBy: typeof body.enteredBy === 'string' ? body.enteredBy : 'Carb Tracker'
      };
      ctx.mealimages.create(image, function (err, created) {
        if (err) {
          console.log('Error adding meal image');
          res.sendJSONStatus(res, consts.HTTP_INTERNAL_ERROR, 'Mongo Error', err);
        } else {
          console.log('Meal image created');
          res.json(created);
        }
      });
    });

    api.delete('/mealimages/:_id', wares.verifyAuthorization, function (req, res) {
      ctx.mealimages.remove(req.params._id, function ( ) {
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
