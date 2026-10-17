/* global angular */
(function () {
  'use strict';
  angular.module('financehub')
    .factory('Auth', ['$http', '$q', '$window', function (http, q, window) {
      var accessToken = null, expiresAt = 0, pendingRefresh = null, csrfReady = null;
      function csrf() {
        if (!csrfReady) csrfReady = http.get('/api/v1/auth/csrf', { skipAuth: true }).catch(function (error) {
          csrfReady = null; return q.reject(error);
        });
        return csrfReady;
      }
      function post(path, body) {
        return csrf().then(function () { return http.post('/api/v1/auth/' + path, body || {}, { skipAuth: true }); });
      }
      function remember(response) {
        accessToken = response.data.accessToken;
        expiresAt = Date.now() + response.data.expiresIn * 1000;
        return response;
      }
      function clear() { accessToken = null; expiresAt = 0; }
      function cookieOperation(operation) {
        var locks = window.navigator && window.navigator.locks;
        // The refresh cookie is shared across tabs. Serialize rotations where Web Locks is available.
        return locks ? q.resolve(locks.request('financehub-session', operation)) : operation();
      }
      var auth = {
        token: function () { return accessToken; },
        ensure: function () {
          if (accessToken && Date.now() < expiresAt - 15000) return q.resolve();
          if (!pendingRefresh) pendingRefresh = cookieOperation(function () { return post('refresh'); }).then(remember).catch(function (error) {
            clear(); return q.reject(error);
          }).finally(function () { pendingRefresh = null; });
          return pendingRefresh;
        },
        login: function (email, password) { return cookieOperation(function () { return post('login', { email: email, password: password, deviceName: 'FinanceHub browser' }); }).then(remember); },
        register: function (data) { return post('register', data); },
        action: post,
        profile: function () { return http.get('/api/v1/users/me').then(function (r) { return r.data; }); },
        logout: function () { return cookieOperation(function () { return post('logout'); }).then(clear); },
        clear: clear
      };
      return auth;
    }])
    .factory('AuthInterceptor', ['$injector', '$q', function (injector, q) {
      return {
        request: function (config) {
          if (config.skipAuth || config.url.indexOf('/api/v1/') !== 0) return config;
          var auth = injector.get('Auth');
          return auth.ensure().then(function () {
            config.headers = config.headers || {}; config.headers.Authorization = 'Bearer ' + auth.token(); return config;
          });
        },
        responseError: function (error) {
          if (error.status === 401 && !error.config.skipAuth) {
            injector.get('Auth').clear(); injector.get('$state').go('login');
          }
          // Mutations are never automatically replayed after a failure.
          return q.reject(error);
        }
      };
    }]);
}());
