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
      http.interceptors.push('AuthInterceptor');
    }])
    .run(['$transitions', '$state', function (transitions, state) {
      transitions.onError({}, function (transition) {
        var error = transition.error();
        if (error && error.detail && error.detail.status === 401) state.go('login');
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
