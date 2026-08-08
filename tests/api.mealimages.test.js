'use strict';

var request = require('supertest');
var should = require('should');

// smallest valid 1x1 transparent PNG
var PNG_PIXEL = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==';

describe('Meal images API', function ( ) {
  var self = this;

  var api = require('../lib/api/');
  before(function (done) {
    process.env.API_SECRET = 'this is my long pass phrase';
    self.env = require('../env')();
    self.env.settings.enable = ['careportal'];
    this.wares = require('../lib/middleware/')(self.env);
    self.app = require('express')();
    self.app.enable('api');
    require('../lib/bootevent')(self.env).boot(function booted(ctx) {
      self.ctx = ctx;
      self.app.use('/api', api(self.env, ctx));
      done();
    });
  });

  after(function () {
    delete process.env.API_SECRET;
  });

  it('post an image, fetch it back, then delete it', function (done) {
    request(self.app)
      .post('/api/mealimages/')
      .set('api-secret', self.env.api_secret || '')
      .send({image: 'data:image/png;base64,' + PNG_PIXEL})
      .expect(200)
      .end(function (err, res) {
        if (err) { return done(err); }
        should.exist(res.body._id);
        var id = res.body._id;

        request(self.app)
          .get('/api/mealimages/' + id)
          .expect(200)
          .expect('Content-Type', 'image/png')
          .end(function (err) {
            if (err) { return done(err); }

            request(self.app)
              .delete('/api/mealimages/' + id)
              .set('api-secret', self.env.api_secret || '')
              .expect(200)
              .end(function (err) {
                if (err) { return done(err); }

                request(self.app)
                  .get('/api/mealimages/' + id)
                  .expect(404)
                  .end(done);
              });
          });
      });
  });

  it('reject an upload that is not a base64 image data URL', function (done) {
    request(self.app)
      .post('/api/mealimages/')
      .set('api-secret', self.env.api_secret || '')
      .send({image: 'javascript:alert(1)'})
      .expect(422)
      .end(done);
  });

  it('reject an upload without authorization', function (done) {
    request(self.app)
      .post('/api/mealimages/')
      .send({image: 'data:image/png;base64,' + PNG_PIXEL})
      .expect(401)
      .end(done);
  });

  it('return 404 for a bogus id', function (done) {
    request(self.app)
      .get('/api/mealimages/not-a-real-id')
      .expect(404)
      .end(done);
  });

});
