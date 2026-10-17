/* global angular */
(function () {
  'use strict';
  angular.module('financehub', ['ui.router'])
    .config(['$stateProvider', '$urlRouterProvider', '$httpProvider', function (states, urls, http) {
      ['login', 'register', 'forgot-password', 'verify-email', 'reset-password'].forEach(function (mode) {
        states.state(mode, {
          url: '/' + mode + '?token', component: 'accessPage',
          resolve: { mode: function () { return mode; } }
        });
      });
      states.state('workspaces', {
        url: '/workspaces', component: 'workspacePage',
        resolve: { profile: ['Auth', function (auth) { return auth.ensure().then(function () { return auth.profile(); }); }] }
      });
      urls.otherwise('/workspaces');
      states.state('accounts', {
        url: '/workspaces/:workspaceId/accounts', component: 'accountPage',
        resolve: { workspace: ['$http', '$transition$', function (http, transition) {
          return http.get('/api/v1/workspaces/' + transition.params().workspaceId).then(function (r) { return r.data; });
        }] }
      });
      ['dashboard', 'debts', 'categories'].forEach(function (mode) {
        states.state(mode, {
          url: '/workspaces/:workspaceId/' + mode, component: 'financePage',
          resolve: {
            mode: function () { return mode; },
            workspace: ['$http', '$transition$', function (http, transition) {
              return http.get('/api/v1/workspaces/' + transition.params().workspaceId).then(function (r) { return r.data; });
            }]
          }
        });
      });
      http.interceptors.push('AuthInterceptor');
    }])
    .run(['$transitions', '$state', function (transitions, state) {
      transitions.onError({}, function (transition) {
        var error = transition.error();
        if (error && error.detail && error.detail.status === 401) state.go('login');
        if (error && error.detail && [403, 404].indexOf(error.detail.status) !== -1 && ['accounts','dashboard','debts','categories'].indexOf(transition.to().name) !== -1) state.go('workspaces');
      });
    }])
    .controller('ShellController', ['I18n', '$document', function (i18n, document) {
      var vm = this;
      vm.language = i18n.language; vm.dark = false;
      vm.setLanguage = function () { i18n.language = vm.language; document[0].documentElement.lang = vm.language; };
      vm.toggleTheme = function () { vm.dark = !vm.dark; };
      vm.skip = function (event) { event.preventDefault(); document[0].getElementById('main').focus(); };
    }]);
}());
