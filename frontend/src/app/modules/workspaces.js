/* global angular */
(function () {
  'use strict';
  angular.module('financehub').component('workspacePage', {
    bindings: { profile: '<' }, templateUrl: 'app/modules/workspaces.html',
    controller: ['$http', 'Auth', '$state', function (http, auth, state) {
      var vm = this; vm.items = []; vm.page = 0; vm.size = 12; vm.total = 0;
      vm.draft = { name:'',kind:'PERSONAL',currency:'BRL',timezone:'America/Fortaleza' };
      vm.$onInit = function () { vm.load(); };
      vm.load = function () {
        vm.loading = true; vm.error = null;
        return http.get('/api/v1/workspaces', {params:{page:vm.page,size:vm.size}}).then(function (r) { vm.items = r.data.items; vm.total = r.data.total; })
          .catch(function () { vm.error = 'error'; }).finally(function () { vm.loading = false; });
      };
      vm.create = function () {
        if (vm.saving) return;
        vm.saving = true; vm.error = null;
        http.post('/api/v1/workspaces', vm.draft).then(function (r) {
          vm.selected = r.data; vm.draft.name = ''; vm.page = 0; return vm.load();
        }).catch(function () { vm.error = 'error'; }).finally(function () { vm.saving = false; });
      };
      vm.select = function (item) {
        vm.error = null;
        state.go('accounts', { workspaceId: item.id });
      };
      vm.move = function (delta) { vm.page += delta; vm.load(); };
      vm.logout = function () {
        auth.logout().then(function () { state.go('login'); }).catch(function () { vm.error = 'error'; });
      };
    }]
  });
}());
