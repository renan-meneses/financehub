/* global angular */
(function () {
  'use strict';
  angular.module('financehub').component('accessPage', {
    bindings: { mode: '<' }, templateUrl: 'app/modules/access.html',
    controller: ['Auth', '$state', '$stateParams', function (auth, state, params) {
      var vm = this;
      vm.data = {}; vm.busy = false;
      vm.$onInit = function () {
        vm.title = { login:'loginTitle',register:'registerTitle','forgot-password':'forgotTitle','verify-email':'verifyTitle','reset-password':'resetTitle' }[vm.mode];
      };
      vm.submit = function () {
        if (vm.busy) return;
        vm.error = null; vm.message = null; vm.busy = true;
        var operation;
        if (vm.mode === 'login') operation = auth.login(vm.data.email, vm.data.password).then(function () { state.go('workspaces'); });
        if (vm.mode === 'register') operation = auth.register(vm.data).then(function () { vm.message = 'checkEmail'; });
        if (vm.mode === 'forgot-password') operation = auth.action('forgot-password', { email:vm.data.email }).then(function () { vm.message = 'checkEmail'; });
        if (vm.mode === 'verify-email') operation = auth.action('verify-email', { token:params.token }).then(function () { vm.message = 'verified'; });
        if (vm.mode === 'reset-password') operation = auth.action('reset-password', { token:params.token,password:vm.data.password }).then(function () { vm.message = 'passwordReset'; });
        operation.catch(function (error) { vm.error = error.status === 429 ? 'rateLimited' : error.status === 401 ? 'unauthorized' : 'error'; })
          .finally(function () { vm.busy = false; vm.data.password = ''; });
      };
      vm.resend = function () {
        if (vm.busy || !vm.data.email) return;
        vm.busy = true;
        auth.action('resend-verification', {email:vm.data.email}).then(function () { vm.message = 'checkEmail'; vm.error = null; })
          .catch(function () { vm.error = 'error'; }).finally(function () { vm.busy = false; });
      };
    }]
  });
}());
