/* global angular */
(function () {
  'use strict';
  angular.module('financehub')
    .factory('FinanceApi', ['$http', '$window', function (http, window) {
      return { forWorkspace: function (workspace) {
        var pending = {};
        function config(params) { return { headers: { 'X-Workspace-Id': workspace }, params: params }; }
        return {
          list: function (resource, params) { return http.get('/api/v1/' + resource, config(params)); },
          update: function (id, body) { return http.patch('/api/v1/categories/' + id, body, config()); },
          command: function (path, draft) {
            var body = angular.copy(draft), fingerprint = JSON.stringify(body);
            if (!pending[path] || pending[path].fingerprint !== fingerprint) pending[path] = { fingerprint: fingerprint, key: window.crypto.randomUUID() };
            var operation = pending[path], options = config();
            options.headers['Idempotency-Key'] = operation.key;
            return http.post('/api/v1/' + path, body, options).then(function (r) {
              if (pending[path] === operation) delete pending[path];
              return r;
            });
          }
        };
      } };
    }])
    .component('financePage', {
      bindings: { workspace: '<', mode: '<' }, templateUrl: 'app/modules/finance.html',
      controller: ['FinanceApi', '$q', function (finance, q) {
        var vm = this, api;
        vm.items = []; vm.categories = []; vm.accounts = []; vm.page = 0; vm.size = 12; vm.status = ''; vm.total = 0;
        vm.$onInit = function () {
          api = finance.forWorkspace(vm.workspace.id);
          vm.canWrite = ['ORGANIZATION_ADMIN','FINANCIAL_MANAGER','MEMBER'].indexOf(vm.workspace.role) !== -1;
          var date = {};
          new Intl.DateTimeFormat('en', { timeZone: vm.workspace.timezone, year: 'numeric', month: '2-digit', day: '2-digit' })
            .formatToParts(new Date()).forEach(function (part) { date[part.type] = part.value; });
          vm.today = date.year + '-' + date.month + '-' + date.day;
          vm.period = { from: date.year + '-01-01', to: vm.today };
          vm.draft = { description: '', amount: '', dueDate: vm.today }; vm.category = { name: '' };
          vm.load();
        };
        function fail(error) {
          vm.error = error.data && error.data.code ? 'finance_' + error.data.code : 'error';
        }
        function all(resource, page, accumulated) {
          return api.list(resource, { page: page, size: 100 }).then(function (r) {
            var values = accumulated.concat(r.data.items);
            return values.length < r.data.total ? all(resource, page + 1, values) : values;
          });
        }
        vm.load = function () {
          if (vm.loading) return;
          vm.loading = true; vm.error = null; vm.analysis = null;
          var request;
          if (vm.mode === 'dashboard') {
            request = api.list('dashboard', vm.period).then(function (r) { vm.analysis = r.data; });
          } else {
            request = api.list(vm.mode, { page: vm.page, size: vm.size, status: vm.status || undefined }).then(function (r) { vm.items = r.data.items; vm.total = r.data.total; });
            if (vm.mode === 'debts' && vm.canWrite) request = q.all([request, all('categories', 0, []).then(function (items) {
              vm.categories = items.filter(function (item) { return item.status === 'ACTIVE'; });
            }), all('accounts', 0, []).then(function (items) {
              vm.accounts = items.filter(function (item) { return item.status === 'ACTIVE'; });
            })]);
          }
          return request.catch(fail).finally(function () { vm.loading = false; });
        };
        vm.move = function (delta) { vm.page += delta; vm.load(); };
        vm.filter = function () { vm.page = 0; vm.load(); };
        function save(operation, done) {
          if (vm.saving || !vm.canWrite) return;
          vm.saving = true; vm.error = null; vm.message = null;
          return operation().then(function () { done(); vm.message = 'financeSaved'; return vm.load(); })
            .catch(fail).finally(function () { vm.saving = false; });
        }
        vm.createDebt = function () { return save(function () { return api.command('debts', vm.draft); }, function () {
          vm.draft.description = ''; vm.draft.amount = ''; vm.page = 0;
        }); };
        vm.edit = function (item) { vm.editing = angular.copy(item); };
        vm.saveCategory = function () { return save(function () {
          return vm.editing ? api.update(vm.editing.id, { name: vm.editing.name, status: vm.editing.status, version: vm.editing.version }) : api.command('categories', vm.category);
        }, function () { vm.category.name = ''; vm.editing = null; vm.page = 0; }); };
        vm.preparePayment = function (item) { vm.payment = { debt: item, paidDate: vm.today }; vm.cancelling = null; };
        vm.pay = function () { return save(function () { return api.command('debts/' + vm.payment.debt.id + '/payments', { paidDate: vm.payment.paidDate }); }, function () { vm.payment = null; }); };
        vm.cancelDebt = function () { return save(function () { return api.command('debts/' + vm.cancelling.id + '/cancellation', {}); }, function () { vm.cancelling = null; }); };
        vm.overdue = function (item) { return item.status === 'PENDING' && item.dueDate < vm.today; };
      }]
    });
}());
